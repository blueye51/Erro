import { useEffect, useRef, useState } from 'react'
import { ApiError, cancelRequest, chatDetail, chatList, createChat, deleteChat, eraseGuest, guestSession,
  queueMessage, requestStatus, updateGuestSession, type ChatDetail, type ChatRequest, type GuestSession, type SavedAttachment, type SavedChat, type SavedMessage } from './guestChat'

export function useSavedChat() {
  const [session, setSession] = useState<GuestSession | null>(null)
  const [chats, setChats] = useState<SavedChat[]>([])
  const [moreChats, setMoreChats] = useState(false)
  const [chatId, setChatId] = useState<string | null>(null)
  const [messages, setMessages] = useState<SavedMessage[]>([])
  const [files, setFiles] = useState<SavedAttachment[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [pendingRequest, setPendingRequest] = useState<ChatRequest | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [error, setError] = useState('')
  const [draft, setDraft] = useState('')
  const epoch = useRef(0), sending = useRef(false)
  const changing = useRef(false)
  const attempt = useRef<{ id: string; text: string; files: string } | null>(null)
  const channel = useRef<BroadcastChannel | null>(null)
  const failure = (e: unknown) => e instanceof Error ? e.message : 'Something went wrong. Please retry.'
  function apply(detail: ChatDetail) {
    setMessages(detail.messages); setFiles(detail.attachments); setHasMore(detail.hasMore); setPendingRequest(detail.pending)
    setChats(previous => [detail.chat, ...previous.filter(c => c.id !== detail.chat.id)])
  }
  async function select(id: string) {
    const generation = ++epoch.current
    const url = new URL(window.location.href); url.searchParams.set('chat', id); window.history.replaceState(null, '', url)
    setChatId(id); setMessages([]); setFiles([]); setPendingRequest(null); setDraft(''); setError(''); setLoading(true)
    sending.current = false; attempt.current = null; setSubmitting(false)
    try { const detail = await chatDetail(id); if (generation === epoch.current) apply(detail); return true }
    catch (e) { if (generation === epoch.current) setError(failure(e)); return false }
    finally { if (generation === epoch.current) setLoading(false) }
  }
  async function initialize(fresh = false) {
    if (fresh) { ++epoch.current; setChatId(null); setMessages([]); setFiles([]); setPendingRequest(null); setDraft('') }
    setLoading(true); setError('')
    try {
      const guest = await guestSession(fresh)
      setSession(guest); setChats(guest.chats); setMoreChats(guest.hasMore)
      const requested = new URL(window.location.href).searchParams.get('chat')
      const id = !fresh && requested && /^[0-9a-f-]{36}$/i.test(requested) ? requested : guest.chats[0]?.id
      if (id && !await select(id) && guest.chats[0]?.id !== id) await select(guest.chats[0].id)
    } catch (e) { setError(failure(e)); setLoading(false) }
  }
  useEffect(() => {
    let cancelled = false
    guestSession().then(async guest => {
      if (cancelled) return
      setSession(guest); setChats(guest.chats); setMoreChats(guest.hasMore)
      const requested = new URL(window.location.href).searchParams.get('chat')
      const id = requested && /^[0-9a-f-]{36}$/i.test(requested) ? requested : guest.chats[0]?.id
      if (id && !await select(id) && guest.chats[0]?.id !== id) await select(guest.chats[0].id)
    }).catch(e => { if (!cancelled) { setError(failure(e)); setLoading(false) } })
    const notifications = new BroadcastChannel('erro-guest-data')
    channel.current = notifications
    notifications.onmessage = event => { if (event.data === 'deleted') void initialize(true) }
    return () => {
      cancelled = true
      // This ref is a lifecycle counter, not a DOM element; invalidating it prevents stale responses.
      // oxlint-disable-next-line react-hooks/exhaustive-deps
      epoch.current++
      notifications.close(); channel.current = null
    }
  }, [])
  const pendingId = pendingRequest?.id
  useEffect(() => {
    if (!pendingId || !chatId || submitting) return
    let cancelled = false, timer: ReturnType<typeof setTimeout>
    const generation = epoch.current
    async function poll() {
      try {
        const status = await requestStatus(chatId!, pendingId!)
        if (cancelled || generation !== epoch.current) return
        if (status.status === 'COMPLETE') {
          const detail = await chatDetail(chatId!)
          if (cancelled || generation !== epoch.current) return
          apply(detail); attempt.current = null; setError('')
          return
        }
        if (status.status === 'FAILED') {
          setPendingRequest(null); setDraft(status.message); setError(status.error || 'The reply failed. Please retry.'); attempt.current = null
          return
        }
        setPendingRequest(status)
      } catch (e) {
        if (cancelled || generation !== epoch.current) return
        setError(failure(e))
        if (e instanceof ApiError && [401, 404].includes(e.status)) { setPendingRequest(null); return }
      }
      if (!cancelled) timer = setTimeout(poll, 1200)
    }
    timer = setTimeout(poll, 300)
    return () => { cancelled = true; clearTimeout(timer) }
  }, [chatId, pendingId, submitting])
  async function send(ids: string[]) {
    const text = draft.trim()
    if (!text || !chatId || !session || pendingRequest || sending.current || loading) return
    const id = chatId, generation = epoch.current, filesKey = JSON.stringify(ids)
    const requestId = attempt.current?.text === text && attempt.current.files === filesKey ? attempt.current.id : crypto.randomUUID()
    attempt.current = { id: requestId, text, files: filesKey }; sending.current = true; setSubmitting(true); setError(''); setDraft('')
    setPendingRequest({ id: requestId, status: 'QUEUED', message: text, attachmentIds: ids, error: '', createdAt: new Date().toISOString() })
    try {
      const result = await queueMessage(id, session.csrfToken, requestId, text, ids)
      if (generation === epoch.current) {
        if (result.status === 'COMPLETE') { apply(await chatDetail(id)); attempt.current = null }
        else if (result.status === 'FAILED') { setPendingRequest(null); setDraft(text); setError(result.error); attempt.current = null }
        else setPendingRequest(result)
      }
    } catch (e) {
      if (generation !== epoch.current) return
      if (e instanceof ApiError && e.status === 409) {
        try {
          const detail = await chatDetail(id)
          if (generation === epoch.current && detail.pending) { apply(detail); setDraft(text); return }
        } catch { /* Preserve the original error below. */ }
      }
      try {
        const existing = await requestStatus(id, requestId)
        if (generation === epoch.current) {
          if (existing.status === 'COMPLETE') { apply(await chatDetail(id)); attempt.current = null }
          else if (existing.status === 'FAILED') { setPendingRequest(null); setDraft(text); setError(existing.error); attempt.current = null }
          else setPendingRequest(existing)
        }
      } catch {
        if (generation === epoch.current) { setPendingRequest(null); setDraft(text); setError(failure(e)) }
      }
    } finally { if (generation === epoch.current) { sending.current = false; setSubmitting(false) } }
  }
  async function newChat() {
    if (!session || changing.current) return
    changing.current = true; setLoading(true)
    try { const chat = await createChat(session.csrfToken); setChats(previous => [chat, ...previous]); await select(chat.id) }
    catch (e) { setError(failure(e)) } finally { changing.current = false; setLoading(false) }
  }
  async function removeChat() {
    if (!session || !chatId || changing.current || !window.confirm('Delete this chat and its document data?')) return
    changing.current = true; setLoading(true)
    try { await deleteChat(chatId, session.csrfToken); await initialize(true) } catch (e) { setError(failure(e)) }
    finally { changing.current = false; setLoading(false) }
  }
  async function erase() {
    if (!session || changing.current || !window.confirm('Permanently delete all your chats and document data, and start as a new guest?')) return
    changing.current = true; setLoading(true)
    try {
      const guest = await eraseGuest(session.csrfToken)
      updateGuestSession(guest)
      setSession(guest); setChats(guest.chats); setMoreChats(guest.hasMore); setDraft(''); setFiles([]); setMessages([]); setPendingRequest(null)
      channel.current?.postMessage('deleted')
      if (guest.chats[0]) await select(guest.chats[0].id)
    } catch (e) { setError(failure(e)) } finally { changing.current = false; setLoading(false) }
  }
  async function cancel() {
    if (!chatId || !session || !pendingRequest) return
    try { await cancelRequest(chatId, session.csrfToken, pendingRequest.id); setDraft(pendingRequest.message); setPendingRequest(null); attempt.current = null; setError('') }
    catch (e) { setError(failure(e)) }
  }
  async function older() {
    if (!chatId || !hasMore || loadingOlder || !messages.length) return false
    setLoadingOlder(true)
    try { const detail = await chatDetail(chatId, messages[0].id); setMessages(previous => [...detail.messages, ...previous]); setHasMore(detail.hasMore); return true }
    catch (e) { setError(failure(e)); return false } finally { setLoadingOlder(false) }
  }
  async function more() {
    try { const page = await chatList(chats.length); setChats(previous => [...previous, ...page.chats.filter(c => !previous.some(p => p.id === c.id))]); setMoreChats(page.hasMore) }
    catch (e) { setError(failure(e)) }
  }
  return { session, chats, moreChats, chatId, messages, files, hasMore, pendingRequest, pending: submitting || !!pendingRequest,
    loading, loadingOlder, error, draft, setDraft, setError, select, send, newChat, removeChat, erase, cancel, older, more, initialize }
}
