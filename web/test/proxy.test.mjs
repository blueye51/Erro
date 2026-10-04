import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { after, before, test } from 'node:test'
import { setTimeout as delay } from 'node:timers/promises'

// Run against the production image: docker compose build web
// Then: node --test web/test/proxy.test.mjs
const image = process.env.ERRO_WEB_IMAGE || 'erro-web'
const prefix = `erro-proxy-test-${randomUUID().slice(0, 8)}`
const network = `${prefix}-network`
const proxy = `${prefix}-web`
const upstream = `${prefix}-api`
const replacement = `${prefix}-replacement`
const containers = []
let base

function docker(...args) {
  return execFileSync('docker', args, {
    encoding: 'utf8', timeout: 30_000, stdio: ['ignore', 'pipe', 'pipe'],
  }).trim()
}

const fixture = `
  const http = require('node:http');
  http.createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk;
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Cache-Control', 'no-store');
    const path = new URL(req.url, 'http://fixture').pathname;
    if (path === '/api/slow') await new Promise(resolve => setTimeout(resolve, 16_000));
    if (path === '/api/fail') {
      res.writeHead(503);
      return res.end(JSON.stringify({ error: 'Provider temporarily unavailable.' }));
    }
    if (!['/api/chat', '/api/echo', '/api/slow'].includes(path)) {
      res.writeHead(404);
      return res.end(JSON.stringify({ error: 'Unknown API route.' }));
    }
    res.end(JSON.stringify({
      reply: '**Hello** from the proxy.\\n\\nA formatted reply.',
      server: process.env.SERVER_ID,
      method: req.method, url: req.url, body,
      host: req.headers.host, origin: req.headers.origin,
      contentType: req.headers['content-type']
    }));
  }).listen(3000, '0.0.0.0');
`

function startUpstream(name, id) {
  containers.push(name)
  docker('run', '-d', '--name', name, '--network', network, '--network-alias', 'api',
    '-e', `SERVER_ID=${id}`, 'node:24-alpine', 'node', '-e', fixture)
}

async function waitFor(check, description, timeout = 25_000) {
  const until = Date.now() + timeout
  let lastError
  while (Date.now() < until) {
    try {
      if (await check()) return
    } catch (error) { lastError = error }
    await delay(300)
  }
  throw new Error(`Timed out waiting for ${description}`, { cause: lastError })
}

function get(path, options = {}) {
  return fetch(`${base}${path}`, { signal: AbortSignal.timeout(22_000), ...options })
}

before(async () => {
  docker('network', 'create', network)
  containers.push(proxy)
  // A non-default port proves the image honors Railway's PORT at runtime.
  docker('run', '-d', '--name', proxy, '--network', network,
    '-p', '127.0.0.1::8081', '-e', 'PORT=8081',
    '-e', 'API_UPSTREAM=http://api:3000', image)
  const address = docker('port', proxy, '8081/tcp')
  base = `http://${address}`
  // Nginx must start even before the private upstream hostname exists.
  await waitFor(async () => (await get('/healthz')).status === 200, 'Nginx startup')
  startUpstream(upstream, 'original')
  await waitFor(async () => (await get('/api/echo')).status === 200, 'upstream startup')
})

after(() => {
  for (const name of containers) {
    try { docker('rm', '-f', name) } catch { /* Already removed by a test. */ }
  }
  try { docker('network', 'rm', network) } catch { /* Setup may not have completed. */ }
})

test('serves the website, SPA routes, and actual assets with suitable caching', async () => {
  const home = await get('/')
  assert.equal(home.status, 200)
  assert.match(home.headers.get('content-type'), /text\/html/)
  assert.match(home.headers.get('cache-control'), /no-cache/)
  const html = await home.text()
  assert.match(html, /id="root"/)
  assert.equal(await (await get('/a-client-side-route')).text(), html)
  const assetPath = html.match(/src="([^"]+\.js)"/)[1]
  const asset = await get(assetPath)
  assert.equal(asset.status, 200)
  assert.match(asset.headers.get('content-type'), /javascript/)
  assert.match(asset.headers.get('cache-control'), /max-age=31536000/)
  assert.doesNotMatch(await asset.text(), /api\.railway\.internal|backend:8080|VITE_API_BASE_URL/)
  assert.equal((await get('/assets/missing.js')).status, 404)
})

test('forwards API paths, queries, POST bodies, host and origin without rewriting', async () => {
  const body = JSON.stringify({ message: 'An electrical question: Ω & café.' })
  const response = await get('/api/echo?value=a%2Fb&second=2', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Origin: 'https://erro.ink' }, body,
  })
  assert.equal(response.status, 200)
  assert.equal(response.headers.get('cache-control'), 'no-store')
  const data = await response.json()
  assert.equal(data.method, 'POST')
  assert.equal(data.url, '/api/echo?value=a%2Fb&second=2')
  assert.equal(data.body, body)
  assert.equal(data.host, new URL(base).host)
  assert.equal(data.origin, 'https://erro.ink')
  assert.equal(data.contentType, 'application/json')
})

test('preserves upstream error responses and keeps API failures out of the SPA fallback', async () => {
  const failed = await get('/api/fail', { method: 'POST' })
  assert.equal(failed.status, 503)
  assert.deepEqual(await failed.json(), { error: 'Provider temporarily unavailable.' })
  for (const path of ['/api', '/api/missing']) {
    const response = await get(path)
    assert.equal(response.status, 404)
    assert.deepEqual(await response.json(), { error: 'Unknown API route.' })
  }
})

test('allows AI replies that take longer than the wiki proxy timeout', async () => {
  const response = await get('/api/slow', { method: 'POST' })
  assert.equal(response.status, 200)
  assert.match((await response.json()).reply, /formatted reply/)
})

test('refreshes private DNS after the API is replaced, without restarting Nginx', async () => {
  assert.equal((await (await get('/api/echo')).json()).server, 'original')
  const startedAt = docker('inspect', '-f', '{{.State.StartedAt}}', proxy)
  const oldIp = docker('inspect', '-f', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', upstream)
  // Start the new API before removing the old one to ensure a different IP.
  startUpstream(replacement, 'replacement')
  const newIp = docker('inspect', '-f', '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}', replacement)
  assert.notEqual(newIp, oldIp)
  docker('rm', '-f', upstream)
  await waitFor(async () => {
    const response = await get('/api/echo')
    return response.ok && (await response.json()).server === 'replacement'
  }, 'DNS refresh to the replacement API')
  assert.equal(docker('inspect', '-f', '{{.State.StartedAt}}', proxy), startedAt)
})

test('continues serving the website while the private backend is unavailable', async () => {
  docker('rm', '-f', replacement)
  assert.equal((await get('/healthz')).status, 200)
  assert.equal((await get('/')).status, 200)
  const response = await get('/api/chat', { method: 'POST' })
  assert.ok([502, 504].includes(response.status))
  assert.doesNotMatch(await response.text(), /id="root"/)
})
