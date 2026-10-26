import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { chromium } from 'playwright'

// Exercise the built frontend with isolated API fixtures, without AI credentials.
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', 'preview', '--host', '127.0.0.1', '--port', '0'], { cwd: process.cwd(), stdio: ['ignore', 'pipe', 'pipe'] })
let browser
try {
  const base = await new Promise((resolve, reject) => {
    let output = ''
    const timer = setTimeout(() => reject(new Error(`Preview did not start: ${output}`)), 15000)
    server.stdout.on('data', chunk => {
      output += chunk
      const match = output.match(/http:\/\/127\.0\.0\.1:\d+/)
      if (match) { clearTimeout(timer); resolve(match[0]) }
    })
    server.stderr.on('data', chunk => { output += chunk })
    server.once('exit', code => { clearTimeout(timer); reject(new Error(`Preview exited (${code}): ${output}`)) })
  })
  browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  const errors = []
  const requests = []
  page.on('pageerror', error => errors.push(error.message))
  let releaseFirst
  const firstReply = new Promise(resolve => { releaseFirst = resolve })
  let failOnce = true
  await page.route('**/api/chat', async route => {
    const body = route.request().postDataJSON()
    requests.push(body)
    if (requests.length === 1) await firstReply
    if (body.message === 'Retry this' && failOnce) {
      failOnce = false
      await route.fulfill({ status: 503, json: { error: 'Temporary test failure. Please retry.' } })
      return
    }
    const contextReset = body.message === 'New problem: Hello'
    const reply = body.message === 'What is my name?' && body.problemContext.includes('My name is Eric.')
      ? 'Your name is **Eric**.' : '**Remembered.**\n\n- A useful reply\n\n```text\nTest code\n```'
    await route.fulfill({ json: { reply, contextReset } })
  })
  await page.goto(base)
  const field = page.getByRole('textbox', { name: 'Message E.R.R.O. AI' })
  const send = page.getByRole('button', { name: 'Send message' })
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    assert.ok(await field.isVisible())
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth))
  }
  await field.fill('My name is Eric.')
  await field.press('Enter')
  await page.getByText('Waiting for a reply…', { exact: true }).waitFor()
  assert.ok(await send.isDisabled())
  await field.press('Enter')
  assert.equal(requests.length, 1, 'Pending sends cannot duplicate the memory entry')
  releaseFirst()
  await page.locator('.message-assistant strong').waitFor()
  assert.deepEqual(requests[0], { message: 'My name is Eric.', problemContext: [] })
  await page.getByText('Remembering 1 of your recent messages.', { exact: false }).waitFor()
  assert.equal(await page.locator('.message-assistant li').count(), 1)
  assert.equal(await page.locator('.message-assistant pre code').textContent(), 'Test code\n')

  const submit = async text => {
    const count = await page.locator('.message-assistant').count()
    await field.fill(text)
    await send.click()
    await page.waitForFunction(expected => document.querySelectorAll('.message-assistant').length === expected, count + 1)
    await page.waitForFunction(() => !document.querySelector('textarea').readOnly)
  }
  await page.getByRole('navigation').getByRole('link', { name: 'About us' }).click()
  await page.getByRole('heading', { name: 'Eric Rand' }).waitFor()
  await page.getByRole('navigation').getByRole('link', { name: 'Chat', exact: true }).click()
  await submit('What is my name?')
  assert.deepEqual(requests.at(-1).problemContext, ['My name is Eric.'])
  assert.equal(await page.locator('.message-assistant strong').last().textContent(), 'Eric')

  const memoryBeforeError = ['My name is Eric.', 'What is my name?']
  await field.fill('Retry this')
  await send.click()
  await page.getByRole('alert').waitFor()
  assert.equal(await field.inputValue(), 'Retry this')
  assert.deepEqual(requests.at(-1).problemContext, memoryBeforeError)
  await submit('Retry this')
  assert.deepEqual(requests.at(-1).problemContext, memoryBeforeError, 'Failed attempts are not remembered')
  assert.equal(await page.getByRole('alert').count(), 0)

  const successful = [...memoryBeforeError, 'Retry this']
  for (let i = 0; i < 7; i++) {
    const message = `Follow-up ${i}: continue using my project details.`
    await submit(message)
    assert.deepEqual(requests.at(-1).problemContext, successful.slice(-8), 'Only recent successful user inputs are sent')
    successful.push(message)
  }
  await page.getByText('Remembering 8 of your recent messages.', { exact: false }).waitFor()
  await page.getByRole('button', { name: 'New problem', exact: true }).click()
  assert.equal(await page.locator('.message').count(), 0)
  await submit('First after reset')
  assert.deepEqual(requests.at(-1).problemContext, [])
  await submit('New problem: Hello')
  await page.getByText('Chat memory restarted because the problem changed.', { exact: true }).waitFor()
  await submit('Follow-up on the new problem')
  assert.deepEqual(requests.at(-1).problemContext, ['New problem: Hello'])

  await page.getByRole('button', { name: 'New problem', exact: true }).click()
  const longMessages = Array.from({ length: 4 }, (_, i) => `Detail ${i}: ` + 'x'.repeat(7990))
  for (const message of longMessages) await submit(message)
  await submit('After long details')
  assert.deepEqual(requests.at(-1).problemContext, longMessages.slice(-3), 'The oldest input is dropped at the 24,000-character limit')
  for (const request of requests) {
    assert.ok(request.problemContext.length <= 8)
    assert.ok(request.problemContext.join('').length <= 24000)
    assert.ok(request.problemContext.every(text => !text.includes('Remembered.') && !text.includes('Your name is **Eric**.')), 'Assistant output is never reused as user evidence')
  }
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), `Chat overflow at ${width}`)
  }
  await page.screenshot({ path: '/tmp/erro-chat-memory-mobile.png', fullPage: true })
  await page.reload()
  assert.equal(await page.locator('.message').count(), 0)
  await submit('First after refresh')
  assert.deepEqual(requests.at(-1).problemContext, [])
  assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0)
  assert.equal((await page.context().cookies()).length, 0)
  assert.deepEqual(errors, [])
  console.log('PASS: short-term user memory, follow-ups, navigation, count/character caps, pending and failed sends, explicit/automatic reset, refresh, Markdown, responsive layouts, no persistence')
} finally {
  await browser?.close()
  server.kill('SIGTERM')
}
