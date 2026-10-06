// Test fixture only. Never configure a production deployment to use this provider.
import http from 'node:http'
const tableReply = [
  'Here is the information to collect:',
  '',
  '| Observation | Engineering meaning |',
  '| --- | --- |',
  '| Conductor size is unknown | **Confirm the cross-section.** Record the conductor material, insulation, installation method and protection rating before selecting equipment. |',
  '| Manufacturer documentation | Review the [reference](https://example.com/reference) and the application ratings. |',
  '| Marking `AC\\|DC` | Preserve the literal pipe inside this cell; this is a formatting fixture, not a compatibility claim. |',
  '',
  '- Keep the original observations.',
  '- Confirm missing specifications.',
  '',
  '```text',
  '| Literal | Code |',
  '| --- | --- |',
  '```',
  '',
  '<script>window.tableHtmlExecuted = true</script>',
  '<img src="https://example.com/hidden-image.png" onerror="window.tableHtmlExecuted = true">',
  '',
  '![Image description](https://example.com/markdown-image.png)',
  '[Unsafe link](javascript:alert(1))',
].join('\n')
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
  const text = input.userQuestion.includes('test-table') ? tableReply : `**Engineering reply.** Confirm the nameplate and protection coordination.${id ? ` [${id}]` : ''}\n\n- Review the supplied conditions.\n- Resolve the missing information.`
  response.end(JSON.stringify({ status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text }] }] }))
}).listen(3000, '0.0.0.0')
