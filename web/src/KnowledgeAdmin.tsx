import { useState } from 'react'
import './KnowledgeAdmin.css'

type DocumentRow = {
  id: string; title: string; publisher: string; status: string; chunk_count: number
  enabled: boolean; error?: string; [key: string]: unknown
}
const sourceTypes = ['THEORY', 'DESIGN_GUIDE', 'LAW', 'NATIONAL_REQUIREMENT', 'STANDARD', 'MANUFACTURER_DATASHEET', 'MANUFACTURER_GUIDE', 'EDUCATIONAL', 'INFORMATIONAL']
const exampleProduct = { manufacturer: 'Manufacturer', partNumber: 'EXACT-PART-NUMBER', productType: 'CONTACTOR', name: 'Catalog name', sourceDocumentId: 'replace-with-indexed-datasheet-uuid', ratings: [], standards: [] }

export function KnowledgeAdmin() {
  const [token, setToken] = useState('')
  const [connected, setConnected] = useState(false)
  const [documents, setDocuments] = useState<DocumentRow[]>([])
  const [offset, setOffset] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [inspection, setInspection] = useState<unknown>(null)
  const [query, setQuery] = useState('400 V 3-phase 7.5 kW motor protection')
  const [debug, setDebug] = useState<unknown>(null)
  const [productJson, setProductJson] = useState(JSON.stringify(exampleProduct, null, 2))
  const [productKind, setProductKind] = useState('products')
  const [deleteId, setDeleteId] = useState('')

  async function api(path: string, method = 'GET', body?: unknown) {
    const response = await fetch(`/api/admin/knowledge/${path}`, { method, credentials: 'omit', cache: 'no-store',
      headers: { Authorization: `Bearer ${token}`, ...(method !== 'GET' && method !== 'DELETE' ? { 'Content-Type': 'application/json' } : {}) },
      body: method !== 'GET' && method !== 'DELETE' ? JSON.stringify(body ?? {}) : undefined,
      signal: AbortSignal.timeout(60_000) })
    const result: unknown = await response.json().catch(() => null)
    if (!response.ok) throw new Error(result && typeof result === 'object' && 'error' in result ? String(result.error) : 'Knowledge operation failed.')
    return result
  }
  async function action(work: () => Promise<void>) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try { await work() } catch (failure) { setError(failure instanceof Error ? failure.message : 'Request failed.') } finally { setBusy(false) }
  }
  async function reload(page = offset) {
    setDocuments(await api(`documents?offset=${page}`) as DocumentRow[]); setOffset(page); setConnected(true)
  }
  function lock() { setToken(''); setConnected(false); setDocuments([]); setInspection(null); setDebug(null); setError(''); setNotice('') }

  return <main className="knowledge-admin page-width">
    <a href="#home">← Back to E.R.R.O.</a>
    <h1>Electrical knowledge</h1>
    <p>Manage source evidence and inspect retrieval. The administrator token stays in this page’s memory and clears on refresh.</p>
    {!connected ? <form onSubmit={e => { e.preventDefault(); void action(() => reload(0)) }}>
      <label>Administrator token<input type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} required minLength={32} /></label>
      <button disabled={busy} type="submit">Unlock knowledge management</button>
    </form> : <>
      <button onClick={lock} disabled={busy}>Lock</button>
      <section><h2>Sources</h2>
        <button disabled={busy} onClick={() => void action(() => reload())}>Refresh sources</button>
        <ul className="source-list">{documents.map(d => <li key={d.id}>
          <h3>{d.title}</h3><p>{d.publisher} · {d.status} · {d.chunk_count} chunks · {d.enabled ? 'Enabled' : 'Disabled'}</p>
          {d.error && <p className="chat-error">{d.error}</p>}
          <div className="admin-actions">
            <button disabled={busy} onClick={() => setInspection(d)}>Metadata</button>
            <button disabled={busy} onClick={() => void action(async () => setInspection(await api(`documents/${d.id}/chunks`)))}>Inspect chunks</button>
            <button disabled={busy} onClick={() => void action(async () => { await api(`documents/${d.id}`, 'PATCH', { enabled: !d.enabled }); await reload() })}>{d.enabled ? 'Disable' : 'Enable'}</button>
            <button disabled={busy} onClick={() => void action(async () => { await api(`documents/${d.id}/reindex`, 'POST'); await reload() })}>Re-index</button>
            <button disabled={busy} onClick={() => setDeleteId(d.id)}>Remove</button>
          </div>
          {deleteId === d.id && <p>Remove this source and its chunks? <button disabled={busy} onClick={() => void action(async () => { await api(`documents/${d.id}`, 'DELETE'); setDeleteId(''); await reload() })}>Confirm removal</button> <button onClick={() => setDeleteId('')}>Cancel</button></p>}
        </li>)}</ul>
        <div className="admin-actions"><button disabled={busy || offset === 0} onClick={() => void action(() => reload(Math.max(0, offset - 50)))}>Previous</button><span>Page {Math.floor(offset / 50) + 1}</span><button disabled={busy || documents.length < 50} onClick={() => void action(() => reload(offset + 50))}>Next</button></div>
        {inspection !== null && <details open><summary>Source inspection</summary><pre>{JSON.stringify(inspection, null, 2)}</pre></details>}
      </section>
      <section><h2>Add a source</h2><p>Paste Markdown or plain text you have rights to index and send to the AI provider. A source URL is a citation link; it is not downloaded. Use metadata-only summaries for unlicensed standards.</p>
        <form onSubmit={e => { e.preventDefault(); const form = e.currentTarget; const data = Object.fromEntries(new FormData(form)); void action(async () => {
          await api('documents', 'POST', { ...data, rightsConfirmed: data.rightsConfirmed === 'on', publicationDate: data.publicationDate || null, effectiveDate: data.effectiveDate || null })
          await reload(0); form.reset(); setNotice('Source saved. Duplicate content retains its existing entry; use re-index to retry a failed entry.')
        }) }}>
          <div className="admin-fields">
            <label>Title<input name="title" required maxLength={300} /></label>
            <label>Publisher<input name="publisher" required maxLength={200} /></label>
            <label>Source URL<input name="sourceUrl" type="url" maxLength={2000} /></label>
            <label>Source type<select name="sourceType">{sourceTypes.map(t => <option key={t}>{t}</option>)}</select></label>
            <label>Jurisdiction<select name="jurisdiction">{['ESTONIA', 'EU', 'IEC', 'INTERNATIONAL', 'MANUFACTURER', 'US'].map(t => <option key={t}>{t}</option>)}</select></label>
            <label>Language<input name="language" defaultValue="en" pattern="[a-z]{2,3}(-[A-Z]{2})?" /></label>
            <label>Content rights<select name="copyrightStatus">{['METADATA_ONLY', 'OPEN_LICENSE', 'PUBLIC', 'USER_PROVIDED', 'LICENSED', 'RESTRICTED'].map(t => <option key={t}>{t}</option>)}</select></label>
            <label>Document version<input name="documentVersion" maxLength={100} /></label>
            <label>Edition<input name="edition" maxLength={100} /></label>
            <label>Amendment<input name="amendment" maxLength={100} /></label>
            <label>Publication date<input name="publicationDate" type="date" /></label>
            <label>Effective date<input name="effectiveDate" type="date" /></label>
            <label>Standard number<input name="standardNumber" maxLength={100} placeholder="IEC 60947-2" /></label>
            <label>Standard family<input name="standardFamily" maxLength={100} /></label>
            <label>Manufacturer<input name="manufacturer" maxLength={200} /></label>
            <label>Product family<input name="productFamily" maxLength={200} /></label>
          </div>
          <label>Description<textarea name="description" maxLength={2000} rows={2} /></label>
          <label>License, attribution and permission notes<textarea name="licenseNotes" required maxLength={2000} rows={2} /></label>
          <label>Markdown or plain text<textarea name="content" required maxLength={50000} rows={12} /></label>
          <label className="admin-checkbox"><input name="rightsConfirmed" type="checkbox" required />I have permission to index this content and send retrieved excerpts to the configured AI/embedding providers.</label>
          <button disabled={busy} type="submit">Ingest source</button>
        </form>
      </section>
      <section><h2>Test retrieval</h2><p>Inspect intent, extracted parameters, evidence scores, checks, and the exact generation input. This does not call the answer model; configured embeddings may be called.</p>
        <form onSubmit={e => { e.preventDefault(); void action(async () => setDebug(await api('debug', 'POST', { question: query }))) }}>
          <label>Question<textarea required maxLength={8000} value={query} onChange={e => setQuery(e.target.value)} rows={3} /></label>
          <button type="submit" disabled={busy}>Inspect retrieval</button>
        </form>
        {debug !== null && <details open><summary>Retrieval and generation context</summary><pre>{JSON.stringify(debug, null, 2)}</pre></details>}
      </section>
      <section><h2>Product catalog</h2><p>Import documented specifications or verified compatibility as JSON. Attach an indexed manufacturer document. Use null for unknown values. See the repository’s knowledge guide for fields and units.</p>
        <form onSubmit={e => { e.preventDefault(); void action(async () => { const result = await api(productKind, 'POST', JSON.parse(productJson)); setNotice(`Catalog data saved${result ? `: ${JSON.stringify(result)}` : '.'}`) }) }}>
          <label>Import type<select value={productKind} onChange={e => setProductKind(e.target.value)}><option value="products">Product</option><option value="compatibility">Compatibility relationship</option></select></label>
          <label>Catalog JSON<textarea rows={12} value={productJson} onChange={e => setProductJson(e.target.value)} required maxLength={50000} spellCheck={false} /></label>
          <button disabled={busy} type="submit">Import catalog data</button>
          <button disabled={busy} type="button" onClick={() => void action(async () => setInspection(await api('products')))}>Inspect first 50 products</button>
        </form>
      </section>
    </>}
    {busy && <p role="status">Working…</p>}{error && <p className="chat-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  </main>
}
