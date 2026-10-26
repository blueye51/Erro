import { useEffect, useRef, useState } from 'react'

export const FILE_TYPES = '.pdf,.png,.jpg,.jpeg,.webp,.docx,.doc,.xlsx,.xls,.csv,.tsv,.txt,.md'
const MAX_FILE_BYTES = 20 * 1024 * 1024
type FileStatus = {
  id: string; filename: string; status: 'QUEUED' | 'PROCESSING' | 'READY' | 'ERROR'
  progress: string; error: string; warnings: string[]; expiresAt: string
}
export type Attachment = {
  key: string; filename: string; state: FileStatus['status'] | 'UPLOADING'
  id?: string; progress: string; error: string; warnings: string[]; selected: boolean
}
class AttachmentError extends Error {
  readonly status: number
  constructor(message: string, status: number) { super(message); this.status = status }
}

async function request<T>(path: string, token: string | null, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/attachments/${path}`, {
      ...options, credentials: 'omit', cache: 'no-store',
      signal: options.signal ?? AbortSignal.timeout(60_000),
      headers: { ...(token ? { 'X-Problem-Token': token } : {}), ...options.headers },
    })
  } catch (failure) {
    if (failure instanceof DOMException && failure.name === 'AbortError') throw failure
    throw new Error('Could not reach file processing. Check your connection and retry.')
  }
  const data = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) throw new AttachmentError(data?.error ?? (response.status === 413 ? 'File exceeds the upload limit (20 MiB).' : 'File processing is unavailable. Please retry.'), response.status)
  return data as T
}
function discard(token: string) {
  // Best effort on refresh/tab close; the server expiry is the reliable cleanup mechanism.
  void fetch('/api/attachments/problem', { method: 'DELETE', headers: { 'X-Problem-Token': token },
    credentials: 'omit', keepalive: true }).catch(() => {})
}

export function useAttachments() {
  const [items, setItems] = useState<Attachment[]>([])
  const [error, setError] = useState('')
  const token = useRef<string | null>(null)
  const creation = useRef<Promise<string> | null>(null)
  const generation = useRef(0)
  const current = useRef<Attachment[]>([])
  const controllers = useRef(new Set<AbortController>())
  function update(fn: (previous: Attachment[]) => Attachment[]) {
    current.current = fn(current.current)
    setItems(current.current)
  }
  function reset() {
    generation.current++
    controllers.current.forEach(c => c.abort()); controllers.current.clear()
    if (token.current) discard(token.current)
    token.current = null; creation.current = null
    update(() => []); setError('')
  }
  useEffect(() => {
    const activeControllers = controllers.current
    const clearServer = () => { if (token.current) discard(token.current) }
    window.addEventListener('pagehide', clearServer)
    return () => {
      window.removeEventListener('pagehide', clearServer)
      clearServer()
      activeControllers.forEach(c => c.abort())
    }
  }, [])

  const pendingIds = items.filter(i => i.state === 'QUEUED' || i.state === 'PROCESSING').map(i => i.id).join(',')
  useEffect(() => {
    if (!pendingIds || !token.current) return
    const controller = new AbortController()
    const epoch = generation.current
    let timer: ReturnType<typeof setTimeout>
    async function poll() {
      try {
        const statuses = await request<FileStatus[]>('files', token.current, { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]) })
        if (epoch !== generation.current || controller.signal.aborted) return
        update(previous => previous.map(item => {
          const status = statuses.find(s => s.id === item.id)
          return status ? { ...item, state: status.status, progress: status.progress, error: status.error, warnings: status.warnings } : item
        }))
        setError('')
      } catch (failure) {
        if (controller.signal.aborted || epoch !== generation.current) return
        if (failure instanceof AttachmentError && failure.status === 410) {
          update(previous => previous.map(item => ({ ...item, state: 'ERROR', progress: '', error: failure.message })))
          return
        }
        setError(failure instanceof Error ? failure.message : 'Could not check file processing.')
      }
      if (!controller.signal.aborted) timer = setTimeout(poll, 2000)
    }
    timer = setTimeout(poll, 500)
    return () => { controller.abort(); clearTimeout(timer) }
    // Only restart when pending IDs change; status text changes must not create polling storms.
  }, [pendingIds])

  async function add(files: File[]) {
    const epoch = generation.current
    setError('')
    const slots = 8 - current.current.length
    if (files.length > slots) setError('A problem supports up to 8 files. Extra files were not added.')
    const accepted = files.slice(0, Math.max(0, slots)).map(file => ({ file, key: crypto.randomUUID() }))
    update(previous => [...previous, ...accepted.map(({ file, key }): Attachment => ({
      key, filename: file.name, state: 'UPLOADING', progress: 'Uploading…', error: '', warnings: [], selected: true,
    }))])
    for (const { file, key } of accepted) {
      if (epoch !== generation.current) return
      const controller = new AbortController(); controllers.current.add(controller)
      try {
        if (!FILE_TYPES.split(',').some(ext => file.name.toLowerCase().endsWith(ext))) throw new Error('This file type is unsupported. Use PDF, an image, Office, CSV or text.')
        if (!file.size || file.size > MAX_FILE_BYTES) throw new Error('Files must be nonempty and at most 20 MiB.')
        if (!token.current) {
          if (!creation.current) creation.current = request<{ token: string }>('problem', null, { method: 'POST', signal: AbortSignal.timeout(15_000) }).then(problem => {
            if (epoch !== generation.current) { discard(problem.token); throw new DOMException('Cancelled', 'AbortError') }
            token.current = problem.token
            return problem.token
          }).catch(failure => { if (epoch === generation.current) creation.current = null; throw failure })
          await creation.current
        }
        const body = new FormData(); body.append('file', file)
        const status = await request<FileStatus>('files', token.current, {
          method: 'POST', body, signal: AbortSignal.any([controller.signal, AbortSignal.timeout(60_000)]),
        })
        if (epoch !== generation.current) return
        update(previous => {
          if (previous.some(i => i.id === status.id && i.key !== key)) return previous.filter(i => i.key !== key)
          return previous.map(i => i.key === key ? { ...i, id: status.id, state: status.status, progress: status.progress, error: status.error, warnings: status.warnings } : i)
        })
      } catch (failure) {
        if (epoch !== generation.current) return
        update(previous => previous.map(i => i.key === key ? { ...i, state: 'ERROR', progress: '',
          error: failure instanceof Error ? failure.message : 'Upload failed. Remove this file and retry.' } : i))
      } finally { controllers.current.delete(controller) }
    }
  }
  async function remove(key: string) {
    const item = current.current.find(i => i.key === key)
    const epoch = generation.current
    try {
      if (item?.id && token.current) await request(`files/${item.id}`, token.current, { method: 'DELETE' })
      if (epoch === generation.current) update(previous => previous.filter(i => i.key !== key))
    } catch (failure) {
      if (epoch !== generation.current) return
      if (failure instanceof AttachmentError && failure.status === 410) reset()
      else setError(failure instanceof Error ? failure.message : 'Could not remove attachment.')
    }
  }
  function select(key: string) { update(previous => previous.map(i => i.key === key ? { ...i, selected: !i.selected } : i)) }
  const blocked = items.some(i => i.selected && i.state !== 'READY')
  return { items, error, token, add, remove, select, reset, blocked,
    ids: items.filter(i => i.selected && i.state === 'READY' && i.id).map(i => i.id!) }
}
