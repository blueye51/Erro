import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { chromium } from 'playwright'

const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', '0'], { stdio: ['ignore', 'pipe', 'pipe'] })
let browser
try {
  const base = await new Promise((resolve, reject) => {
    let output = ''
    const timer = setTimeout(() => reject(new Error(`Preview timeout: ${output}`)), 15000)
    server.stdout.on('data', chunk => { output += chunk; const match = output.match(/http:\/\/127\.0\.0\.1:\d+/); if (match) { clearTimeout(timer); resolve(match[0]) } })
    server.stderr.on('data', chunk => { output += chunk })
    server.once('exit', code => { clearTimeout(timer); reject(new Error(`Preview exited ${code}: ${output}`)) })
  })
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  const errors = [], chats = [], deletes = [], uploads = []
  let files = [], ready = false, failChat = false, count = 0, expired = false, polls = 0
  const token = 'a'.repeat(43)
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/attachments/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname
    if (path.endsWith('/problem')) {
      if (req.method() === 'DELETE') { deletes.push(req.headers()['x-problem-token']); files = []; await route.fulfill({ status: 204 }); return }
      await route.fulfill({ status: 201, json: { token, maxFiles: 8, maxFileBytes: 20971520 } }); return
    }
    assert.equal(req.headers()['x-problem-token'], token)
    if (expired) {
      if (req.method() === 'GET') polls++
      await route.fulfill({ status: 410, json: { error: 'These attachments expired. Remove and reattach the files.' } }); return
    }
    if (req.method() === 'DELETE') { files = files.filter(f => !path.endsWith(f.id)); await route.fulfill({ status: 204 }); return }
    if (req.method() === 'POST') {
      const filename = req.postDataBuffer().toString().match(/filename="([^"]+)"/)[1]
      uploads.push(filename)
      const file = { id: `file-${++count}`, filename, status: 'QUEUED', progress: 'Queued for extraction', error: '', warnings: [], expiresAt: new Date(Date.now() + 1800000).toISOString() }
      files.push(file); await route.fulfill({ status: 202, json: file }); return
    }
    await route.fulfill({ json: files.map(file => ({ ...file, status: !ready ? 'PROCESSING' : file.filename === 'broken.png' ? 'ERROR' : 'READY',
      progress: ready ? 'Ready for questions' : 'Reading page 1, crop 1/2', error: file.filename === 'broken.png' && ready ? 'Vision provider unavailable. Remove and retry.' : '' })) })
  })
  await page.route('**/api/chat', async route => {
    const body = route.request().postDataJSON(); chats.push(body)
    if (failChat) { failChat = false; await route.fulfill({ status: 502, json: { error: 'Temporary answer failure. Retry.' } }); return }
    const contextReset = body.message.startsWith('New problem:')
    await route.fulfill({ json: { reply: '**3 C16 breakers listed.** [Document 1]\n\n| Item | Quantity |\n| --- | --- |\n| C16 | 3 |', contextReset,
      documentSources: body.attachmentIds?.length ? [{ id: 'D-test', filename: 'materials.pdf', location: 'Page 1, table line 4', method: 'PDF_TEXT' }] : [] } })
  })
  await page.goto(base)
  const input = page.getByLabel('Attach project files'), message = page.getByRole('textbox', { name: 'Message E.R.R.O. AI' }), send = page.getByRole('button', { name: 'Send message' })
  await input.setInputFiles([{ name: 'materials.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF fixture') }, { name: 'panel.png', mimeType: 'image/png', buffer: Buffer.from('mock image') }])
  await page.getByText('Reading page 1, crop 1/2', { exact: true }).first().waitFor()
  await message.fill('How many C16 breakers?')
  assert.ok(await send.isDisabled())
  ready = true
  await page.getByText('Ready for questions', { exact: true }).first().waitFor()
  await page.waitForFunction(() => !document.querySelector('.send-button').disabled)
  const submit = async text => {
    const before = await page.locator('.message-assistant').count()
    await message.fill(text); await send.click()
    await page.waitForFunction(n => document.querySelectorAll('.message-assistant').length === n, before + 1)
  }
  await submit('How many C16 breakers?')
  assert.deepEqual(chats.at(-1).attachmentIds, ['file-1', 'file-2'])
  await page.getByText('Document sources (1)', { exact: true }).click()
  await page.getByText('Page 1, table line 4', { exact: false }).waitFor()
  assert.equal(await page.locator('.message-table tbody td').last().textContent(), '3')
  await submit('And the DIN module requirement?')
  assert.deepEqual(chats.at(-1).attachmentIds, ['file-1', 'file-2'])
  assert.equal(uploads.length, 2, 'Follow-ups reuse extracted documents')
  await page.getByLabel('Use panel.png in questions').uncheck()
  await submit('Use only the material specification')
  assert.deepEqual(chats.at(-1).attachmentIds, ['file-1'])
  await page.getByRole('button', { name: 'Remove panel.png', exact: true }).click()
  await page.waitForFunction(() => document.querySelectorAll('.attachment').length === 1)
  failChat = true
  await message.fill('Retry the material question'); await send.click()
  await page.getByRole('alert').waitFor()
  assert.equal(await message.inputValue(), 'Retry the material question')
  await submit('Retry the material question')
  assert.deepEqual(chats.at(-1).attachmentIds, ['file-1'])

  // Real browser drag/drop event with a File object.
  const transfer = await page.evaluateHandle(() => { const dt = new DataTransfer(); dt.items.add(new File(['Description,Quantity\nCable,20'], 'cables.csv', { type: 'text/csv' })); return dt })
  await page.locator('.chat-content').dispatchEvent('drop', { dataTransfer: transfer })
  await page.getByLabel('Use cables.csv in questions').waitFor()
  await input.setInputFiles({ name: 'broken.png', mimeType: 'image/png', buffer: Buffer.from('unreadable') })
  await page.getByText('Vision provider unavailable. Remove and retry.', { exact: true }).waitFor()
  await message.fill('Check the attachments'); assert.ok(await send.isDisabled())
  await page.getByRole('button', { name: 'Remove broken.png', exact: true }).click()
  await input.setInputFiles({ name: 'script.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('test') })
  await page.getByText('This file type is unsupported.', { exact: false }).waitFor()
  await page.getByRole('button', { name: 'Remove script.exe', exact: true }).click()
  const uploadCount = uploads.length
  await input.setInputFiles({ name: 'too-large.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(20 * 1024 * 1024 + 1) })
  await page.getByText('Files must be nonempty and at most 20 MiB.', { exact: true }).waitFor()
  assert.equal(uploads.length, uploadCount)
  await page.getByRole('button', { name: 'Remove too-large.pdf', exact: true }).click()
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), `Overflow at ${width}`)
    assert.ok(await page.getByRole('button', { name: 'Attach files' }).isVisible())
    assert.ok(await page.evaluate(() => document.querySelector('.site-footer').getBoundingClientRect().top >= document.querySelector('.chat-content').getBoundingClientRect().bottom - 1), 'Footer must not overlap attachment controls or privacy text')
  }
  await page.screenshot({ path: '/tmp/erro-attachments-mobile.png', fullPage: true })
  await page.getByRole('button', { name: 'New problem', exact: true }).click()
  assert.equal(await page.locator('.attachment').count(), 0)
  assert.equal(await page.locator('.message').count(), 0)
  assert.ok(deletes.length > 0)
  await input.setInputFiles({ name: 'again.csv', mimeType: 'text/csv', buffer: Buffer.from('Item,Quantity\nC16,3') })
  await page.getByText('Ready for questions', { exact: true }).waitFor()
  await submit('New problem: unrelated question')
  assert.equal(await page.locator('.attachment').count(), 0)
  ready = false
  await input.setInputFiles({ name: 'expired.csv', mimeType: 'text/csv', buffer: Buffer.from('Item,Quantity\nC16,3') })
  await page.getByLabel('Use expired.csv in questions').waitFor()
  expired = true
  await page.getByText('These attachments expired. Remove and reattach the files.', { exact: true }).waitFor()
  const expiredPolls = polls
  await page.waitForTimeout(2300)
  assert.equal(polls, expiredPolls, 'Expired workspaces must stop polling')
  await page.getByRole('button', { name: 'Remove expired.csv', exact: true }).click()
  await page.waitForFunction(() => document.querySelectorAll('.attachment').length === 0)
  expired = false; ready = true
  await input.setInputFiles({ name: 'refresh.csv', mimeType: 'text/csv', buffer: Buffer.from('Item,Quantity\nC16,3') })
  await page.getByText('Ready for questions', { exact: true }).waitFor()
  await page.reload()
  assert.equal(await page.locator('.attachment').count(), 0)
  assert.equal(await page.locator('.message').count(), 0)
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0)
  assert.equal((await page.context().cookies()).length, 0)
  assert.deepEqual(errors, [])
  console.log('PASS: multi-file selection/drop, processing/error/expiry states, removal/selection, retry, reuse, citations, responsive layouts, reset/refresh, no persistence')
} finally {
  await browser?.close(); server.kill('SIGTERM')
}
