// Test fixture only. Never configure a production deployment to use this provider.
import http from 'node:http'
http.createServer(async (request, response) => {
  let body = ''
  for await (const chunk of request) body += chunk
  const envelope = JSON.parse(body)
  const input = JSON.parse(envelope.input)
  await new Promise(resolve => setTimeout(resolve, 500))
  response.setHeader('Content-Type', 'application/json')
  if (input.userQuestion.includes('test-error')) {
    response.writeHead(503)
    return response.end(JSON.stringify({ error: 'Fixture upstream failure' }))
  }
  const id = input.referenceData.retrieval.hits[0]?.source.id
  const text = `**Engineering reply.** Confirm the nameplate and protection coordination.${id ? ` [${id}]` : ''}\n\n- Review the supplied conditions.\n- Resolve the missing information.`
  response.end(JSON.stringify({ status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text }] }] }))
}).listen(3000, '0.0.0.0')
