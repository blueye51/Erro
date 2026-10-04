export async function sendMessage(message: string): Promise<string> {
  let response: Response
  try {
    response = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
      signal: AbortSignal.timeout(65_000),
      credentials: 'omit',
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') {
      throw new Error('The reply took too long. Please try again.')
    }
    throw new Error('Could not reach the chat service. Please try again.')
  }

  const data: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data && typeof data === 'object' && 'error' in data
      && typeof data.error === 'string' ? data.error : 'Chat is unavailable. Please try again.'
    throw new Error(message)
  }
  if (!data || typeof data !== 'object' || !('reply' in data)
      || typeof data.reply !== 'string' || !data.reply.trim()) {
    throw new Error('The chat service returned an empty reply. Please try again.')
  }
  return data.reply
}
