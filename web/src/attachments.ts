import { useEffect, useRef, useState } from 'react'
import { guestApi, type SavedAttachment } from './guestChat'

export const FILE_TYPES = '.pdf,.png,.jpg,.jpeg,.webp,.docx,.doc,.xlsx,.xls,.csv,.tsv,.txt,.md'
export type Attachment = { key: string; id?: string; filename: string; state: SavedAttachment['status'] | 'UPLOADING'; progress: string; error: string; warnings: string[]; selected: boolean }
export function useAttachments(chatId: string | null, csrf: string | undefined, saved: SavedAttachment[]) {
  const [items, setItems] = useState<Attachment[]>([])
  const [error, setError] = useState('')
  const current = useRef<Attachment[]>([])
  const generation = useRef(0)
  const previousChat = useRef<string | null>(null)
  const controllers = useRef(new Set<AbortController>())
  function update(fn: (previous: Attachment[]) => Attachment[]) { current.current = fn(current.current); setItems(current.current) }
  useEffect(() => {
    const changed = previousChat.current !== chatId
    if (changed) {
      generation.current++; controllers.current.forEach(c => c.abort()); controllers.current.clear()
      previousChat.current = chatId; setError('')
    }
    update(previous => saved.map(file => ({ key: file.id, id: file.id, filename: file.filename, state: file.status,
      progress: file.progress, error: file.error, warnings: file.warnings,
      selected: changed ? true : previous.find(i => i.id === file.id)?.selected ?? true })))
  }, [chatId, saved])
  useEffect(() => { const active = controllers.current; return () => { active.forEach(c => c.abort()) } }, [])
  const pendingIds = items.filter(i => i.state === 'QUEUED' || i.state === 'PROCESSING').map(i => i.id).join(',')
  useEffect(() => {
    if (!pendingIds || !chatId) return
    const controller = new AbortController(), epoch = generation.current
    let timer: ReturnType<typeof setTimeout>
    async function poll() {
      try {
        const statuses = await guestApi<SavedAttachment[]>(`chats/${chatId}/attachments`, undefined,
          { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]) })
        if (controller.signal.aborted || epoch !== generation.current) return
        update(previous => previous.map(item => {
          const file = statuses.find(s => s.id === item.id)
          return file ? { ...item, state: file.status, progress: file.progress, error: file.error, warnings: file.warnings } : item
        })); setError('')
      } catch (failure) {
        if (controller.signal.aborted || epoch !== generation.current) return
        setError(failure instanceof Error ? failure.message : 'Could not check file processing.')
      }
      if (!controller.signal.aborted) timer = setTimeout(poll, 1500)
    }
    timer = setTimeout(poll, 400)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [chatId, pendingIds])
  async function add(files: File[]) {
    if (!chatId || !csrf) return
    const epoch = generation.current; setError('')
    const slots = 8 - current.current.length
    if (files.length > slots) setError('A chat supports up to eight files. Extra files were not added.')
    const accepted = files.slice(0, Math.max(0, slots)).map(file => ({ file, key: crypto.randomUUID() }))
    update(previous => [...previous, ...accepted.map(({ file, key }): Attachment => ({ key, filename: file.name,
      state: 'UPLOADING', progress: 'Uploading…', error: '', warnings: [], selected: true }))])
    for (const { file, key } of accepted) {
      if (epoch !== generation.current) return
      const controller = new AbortController(); controllers.current.add(controller)
      try {
        if (!FILE_TYPES.split(',').some(ext => file.name.toLowerCase().endsWith(ext))) throw new Error('This file type is unsupported. Use PDF, images, Office, CSV or text.')
        if (!file.size || file.size > 20 * 1024 * 1024) throw new Error('Files must be nonempty and at most 20 MiB.')
        const body = new FormData(); body.append('file', file)
        const status = await guestApi<SavedAttachment>(`chats/${chatId}/attachments`, csrf, { method: 'POST', body,
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(60_000)]) })
        if (epoch !== generation.current) return
        update(previous => previous.some(i => i.id === status.id && i.key !== key) ? previous.filter(i => i.key !== key)
          : previous.map(i => i.key === key ? { ...i, id: status.id, key: status.id, state: status.status,
            progress: status.progress, error: status.error, warnings: status.warnings } : i))
      } catch (failure) {
        if (epoch === generation.current) update(previous => previous.map(i => i.key === key ? { ...i, state: 'ERROR', progress: '',
          error: failure instanceof Error ? failure.message : 'Upload failed. Remove this file and retry.' } : i))
      } finally { controllers.current.delete(controller) }
    }
  }
  async function remove(key: string) {
    const item = current.current.find(i => i.key === key), epoch = generation.current
    try {
      if (item?.id && chatId && csrf) await guestApi(`chats/${chatId}/attachments/${item.id}`, csrf, { method: 'DELETE' })
      if (epoch === generation.current) update(previous => previous.filter(i => i.key !== key))
    } catch (failure) { if (epoch === generation.current) setError(failure instanceof Error ? failure.message : 'Could not remove file.') }
  }
  function select(key: string) { update(previous => previous.map(i => i.key === key ? { ...i, selected: !i.selected } : i)) }
  return { items, error, add, remove, select, blocked: items.some(i => i.selected && i.state !== 'READY'),
    ids: items.filter(i => i.selected && i.state === 'READY' && i.id).map(i => i.id!) }
}
