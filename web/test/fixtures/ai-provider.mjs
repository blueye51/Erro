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
  if (Array.isArray(envelope.input)) {
    response.setHeader('Content-Type', 'application/json')
    const filename = envelope.input[0].content[0].text
    if (filename.includes('broken.png')) { response.writeHead(503); return response.end(JSON.stringify({ error: 'Fixture vision failure' })) }
    const text = JSON.stringify({ coordinateSpace: 'NORMALIZED_1000', items: [{ reference: 'Q1', description: 'C16 fixture breaker', quantity: 1, unit: 'pcs', basis: 'SYMBOL_COUNT', x: 500, y: 500 }], notes: 'Local fixture only' })
    return response.end(JSON.stringify({ status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text }] }] }))
  }
  const input = JSON.parse(envelope.input)
  if ('previousConversation' in input && envelope.input.length > 32000) {
    response.writeHead(400); return response.end(JSON.stringify({ error: 'Fixture context limit exceeded' }))
  }
  await new Promise(resolve => setTimeout(resolve, input.userQuestion.includes('test-slow') ? 5000 : 500))
  response.setHeader('Content-Type', 'application/json')
  if (input.userQuestion.includes('test-error')) {
    response.writeHead(503)
    return response.end(JSON.stringify({ error: 'Fixture upstream failure' }))
  }
  if (input.userQuestion.includes('test-long-reply')) {
    // Reproduce a provider truncation if the old reply budget returns.
    if (envelope.max_output_tokens < 8192) return response.end(JSON.stringify({ status: 'incomplete', incomplete_details: { reason: 'max_output_tokens' } }))
    const text = Array.from({ length: 60 }, (_, index) => `### Reading section ${index + 1}\n\nThis is a long response fixture for checking paragraph readability, scrolling, and saved conversation restoration. These are interface test observations, not electrical specifications or equipment recommendations.`).join('\n\n')
    return response.end(JSON.stringify({ status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text }] }] }))
  }
  const id = input.referenceData.retrieval.hits[0]?.source.id
  const document = input.documentData?.excerpts[0]?.id
  const text = input.userQuestion.includes('test-table') ? tableReply : `**Engineering reply.** Confirm the nameplate and protection coordination.${id ? ` [${id}]` : ''}${document ? ` [${document}]` : ''}\n\n- Review the supplied conditions.\n- Resolve the missing information.`
  response.end(JSON.stringify({ status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text }] }] }))
}).listen(Number(process.env.FIXTURE_PORT || 3000), process.env.FIXTURE_HOST || '0.0.0.0')
