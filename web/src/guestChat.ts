import type { ChatReply } from './chat'

export type SavedChat = { id: string; title: string; createdAt: string; updatedAt: string }
export type SavedMessage = { id: number; role: 'user' | 'assistant'; text: string; answer: ChatReply | null; filenames: string[]; createdAt: string }
export type SavedAttachment = { id: string; filename: string; bytes: number; status: 'QUEUED' | 'PROCESSING' | 'READY' | 'ERROR'; progress: string; error: string; warnings: string[]; excerpts: number }
export type ChatRequest = { id: string; status: 'QUEUED' | 'PROCESSING' | 'COMPLETE' | 'FAILED'; message: string; attachmentIds: string[]; error: string; createdAt: string }
export type GuestSession = { csrfToken: string; chats: SavedChat[]; hasMore: boolean }
export type ChatDetail = { chat: SavedChat; messages: SavedMessage[]; hasMore: boolean; attachments: SavedAttachment[]; pending: ChatRequest | null }
export class ApiError extends Error {
  readonly status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}
export async function guestApi<T>(path: string, csrf?: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/${path}`, { ...options, credentials: 'same-origin', cache: 'no-store',
      signal: options.signal ?? AbortSignal.timeout(20_000),
      headers: { ...(csrf ? { 'X-CSRF-Token': csrf } : {}), ...options.headers } })
  } catch (failure) {
    if (failure instanceof DOMException && failure.name === 'AbortError') throw failure
    throw new Error('Could not reach saved chats. Check your connection and retry.')
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(data?.error ?? 'Saved chats are unavailable. Please retry.', response.status)
  if (response.status !== 204 && data == null) throw new Error('The chat service returned an invalid response. Please retry.')
  return data as T
}
let bootstrap: Promise<GuestSession> | null = null
export function updateGuestSession(session: GuestSession) { bootstrap = Promise.resolve(session) }
export function guestSession(fresh = false): Promise<GuestSession> {
  if (!bootstrap || fresh) bootstrap = guestApi<GuestSession>('guest').catch(error => { bootstrap = null; throw error })
  return bootstrap
}
export const chatDetail = (id: string, before?: number) => guestApi<ChatDetail>(`chats/${id}${before ? `?before=${before}` : ''}`)
export const chatList = (offset = 0) => guestApi<{ chats: SavedChat[]; hasMore: boolean }>(`chats?offset=${offset}`)
export const createChat = (csrf: string) => guestApi<SavedChat>('chats', csrf, { method: 'POST' })
export const eraseGuest = (csrf: string) => guestApi<GuestSession>('guest', csrf, { method: 'DELETE' })
export const deleteChat = (id: string, csrf: string) => guestApi<void>(`chats/${id}`, csrf, { method: 'DELETE' })
export const queueMessage = (id: string, csrf: string, requestId: string, message: string, attachmentIds: string[]) =>
  guestApi<ChatRequest>(`chats/${id}/messages`, csrf, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ requestId, message, attachmentIds }) })
export const requestStatus = (id: string, requestId: string) => guestApi<ChatRequest>(`chats/${id}/requests/${requestId}`)
export const cancelRequest = (id: string, csrf: string, requestId: string) => guestApi<void>(`chats/${id}/requests/${requestId}`, csrf, { method: 'DELETE' })
