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
  documentSources: { id: string; filename: string; location: string; method: string }[]
}
export const MAX_CONTEXT_MESSAGES = 8
export const MAX_CONTEXT_CHARS = 24000

export function rememberMessage(previous: string[], message: string, reset = false): string[] {
  const recent = [...(reset ? [] : previous), message].slice(-MAX_CONTEXT_MESSAGES)
  let characters = recent.reduce((total, text) => total + text.length, 0)
  while (characters > MAX_CONTEXT_CHARS) characters -= recent.shift()!.length
  return recent
}

export async function sendMessage(message: string, problemContext: string[] = [], token?: string | null, attachmentIds: string[] = []): Promise<ChatReply> {
  let response: Response
  try {
    response = await fetch('/api/chat', {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Problem-Token': token } : {}) },
      body: JSON.stringify({ message, problemContext, ...(attachmentIds.length ? { attachmentIds } : {}) }),
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
    validations: [], products: [], contextReset: false, documentSources: [], ...data } as ChatReply
}
