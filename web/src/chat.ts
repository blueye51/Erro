export type Source = {
  id: string; documentId: string; chunkId: string; title: string; publisher: string
  url: string; section: string; standardNumber: string; documentVersion: string
  edition: string; amendment: string; jurisdiction: string; sourceType: string
  copyrightStatus: string; relevance: number; licenseNotes?: string
}
export type Calculation = {
  name: string; value: number; unit: string; formula: string
  inputs: Record<string, number>; assumptions: string[]
}
export type Check = { rule: string; status: 'PASS' | 'FAIL' | 'UNKNOWN' | 'WARNING'; explanation: string }
export type ChatReply = {
  reply: string; requestId?: string; sources: Source[]; calculations: Calculation[]
  assumptions: string[]; warnings: string[]; missingInformation: string[]
  validations: Check[]; products: { id: string; specifications: Record<string, unknown>; checks: Check[] }[]
  contextReset: boolean
}
export async function sendMessage(message: string, problemContext: string[] = []): Promise<ChatReply> {
  let response: Response
  try {
    response = await fetch('/api/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, problemContext }),
      signal: AbortSignal.timeout(90_000), credentials: 'omit',
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') throw new Error('The reply took too long. Please try again.')
    throw new Error('Could not reach the chat service. Please try again.')
  }
  const data: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data && typeof data === 'object' && 'error' in data
      && typeof data.error === 'string' ? data.error : 'Chat is unavailable. Please try again.'
    throw new Error(message)
  }
  if (!data || typeof data !== 'object' || !('reply' in data)
      || typeof data.reply !== 'string' || !data.reply.trim()) throw new Error('The chat service returned an empty reply.')
  // Accept older backend replies during rolling deployment.
  return { sources: [], calculations: [], assumptions: [], warnings: [], missingInformation: [],
    validations: [], products: [], contextReset: false, ...data } as ChatReply
}
