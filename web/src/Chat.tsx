import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useSavedChat } from './useSavedChat'
import { AnswerDetails } from './AnswerDetails'
import { Arrow } from './Brand'
import { FILE_TYPES, useAttachments } from './attachments'

const starterPrompts = [
  { title: 'Protect a motor', description: 'Work through the protection requirements.', message: 'I have a 400 V three-phase 7.5 kW motor and need protection for it. What information do we need to select the components?' },
  { title: 'Compare components', description: 'Understand ratings and compatibility.', message: 'Help me compare two contactors for my application. Which ratings and manufacturer details should I provide?' },
  { title: 'Plan a parts list', description: 'Turn your project into a starting list.', message: 'Help me plan a parts list for a small motor control panel. Ask me about the equipment, supply and quantities first, and flag anything that needs confirmation.' },
]

export function Chat({ active }: { active: boolean }) {
  const saved = useSavedChat()
  const { draft, setDraft, messages, pending, error, setError } = saved
  const messageList = useRef<HTMLElement>(null)
  const olderScroll = useRef<{ height: number; top: number } | null>(null)
  const composer = useRef<HTMLTextAreaElement>(null)
  const picker = useRef<HTMLInputElement>(null)
  const attachments = useAttachments(saved.chatId, saved.session?.csrfToken, saved.files)
  const [dragging, setDragging] = useState(false)

  useLayoutEffect(() => {
    const field = composer.current
    if (!active || !field) return
    const resize = () => {
      field.style.height = 'auto'
      field.style.height = `${field.scrollHeight}px`
    }
    resize()
    window.addEventListener('resize', resize)
    return () => window.removeEventListener('resize', resize)
  }, [draft, active])

  useEffect(() => {
    const log = messageList.current
    if (log && olderScroll.current) {
      log.scrollTop = olderScroll.current.top + log.scrollHeight - olderScroll.current.height
      olderScroll.current = null; return
    }
    if (!active || !log) return
    const latestReply = !pending && messages.at(-1)?.role === 'assistant'
      ? log.querySelector<HTMLElement>('.message-assistant:last-of-type') : null
    log.scrollTo({ top: latestReply
      ? log.scrollTop + latestReply.getBoundingClientRect().top - log.getBoundingClientRect().top
      : log.scrollHeight })
    if (latestReply) log.scrollIntoView({ block: 'nearest', behavior: 'instant' })
  }, [messages, pending, active])

  async function submitMessage() {
    if (!attachments.blocked) await saved.send(attachments.ids)
  }

  return (
    <div className={`chat-content ${messages.length || pending ? 'has-messages' : 'is-empty'} ${dragging ? 'is-dragging' : ''}`}
      onDragOver={event => { if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); if (!pending) setDragging(true) } }}
      onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false) }}
      onDrop={event => { event.preventDefault(); setDragging(false); if (!pending) void attachments.add(Array.from(event.dataTransfer.files)) }}>
      <div className="saved-chat-toolbar">
        <label>Guest chats<select aria-label="Saved chats" value={saved.chatId ?? ''} disabled={saved.loading}
          onChange={event => void saved.select(event.target.value)}>
          {!saved.chatId && <option value="">Loading chats…</option>}
          {saved.chats.map(chat => <option key={chat.id} value={chat.id}>{chat.title}</option>)}
        </select></label>
        <button className="problem-reset" disabled={!saved.session || saved.loading} onClick={() => void saved.newChat()}>+ New chat</button>
        <button className="guest-delete" disabled={!saved.session || saved.loading} onClick={() => void saved.removeChat()}>Delete chat</button>
        <button className="guest-delete" disabled={!saved.session || saved.loading} onClick={() => void saved.erase()}>Delete all my data</button>
        {saved.moreChats && <button className="guest-delete" onClick={() => void saved.more()}>Load more chats</button>}
      </div>
      {saved.loading && <p className="chat-status" role="status">Loading your saved chat…</p>}
      {messages.length > 0 || pending ? (
        <section
          className="message-list"
          ref={messageList}
          role="log"
          tabIndex={0}
          aria-label="Chat messages"
          aria-live="polite"
          aria-relevant="additions text"
        >
          {saved.hasMore && <button className="load-earlier" disabled={saved.loadingOlder} onClick={() => {
            const log = messageList.current
            if (log) olderScroll.current = { height: log.scrollHeight, top: log.scrollTop }
            void saved.older().then(loaded => { if (!loaded) olderScroll.current = null })
          }}>{saved.loadingOlder ? 'Loading…' : 'Load earlier messages'}</button>}
          {messages.map((message) => (
            <article
              className={`message message-${message.role}`}
              key={message.id}
            >
              <h3 className="message-author">
                {message.role === 'user' ? 'You' : 'E.R.R.O. AI'}
              </h3>
              <div className="message-body">
                {message.role === 'user' ? (
                  <><p>{message.text}</p>{!!message.filenames?.length && <p className="message-files">Files: {message.filenames.join(', ')}</p>}</>
                ) : (
                  <Markdown
                    skipHtml
                    remarkPlugins={[remarkGfm]}
                    components={{
                      img: ({ alt }) => <span>{alt}</span>,
                      table: ({ children }) => (
                        <div
                          className="message-table"
                          role="region"
                          aria-label="Table in assistant reply"
                          tabIndex={0}
                        >
                          <table>{children}</table>
                        </div>
                      ),
                      pre: ({ children }) => (
                        <pre tabIndex={0} aria-label="Code block">
                          {children}
                        </pre>
                      ),
                      a: ({ href, children }) => (
                        <a
                          href={href}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {children}
                        </a>
                      ),
                    }}
                  >
                    {message.text}
                  </Markdown>
                )}
              </div>
              {message.answer && <AnswerDetails answer={message.answer} />}
            </article>
          ))}
          {pending && (
            <><article className="message message-user"><h3 className="message-author">You</h3><div className="message-body"><p>{saved.pendingRequest?.message}</p></div></article>
              <p className="reply-pending" role="status">Thinking… <button type="button" onClick={() => void saved.cancel()}>Cancel reply</button></p></>
          )}
        </section>
      ) : (
        <div className="chat-welcome">
          <p className="eyebrow"><span className="status-dot" /> Your electrical project starts here</p>
          <h1>What are you<br /><em>working on?</em></h1>
          <p className="chat-intro">From a single component to a project plan. Tell us what you need, and let’s work through the details.</p>
        </div>
      )}

      <form
        className="chat-composer"
        aria-label="Send a message"
        aria-describedby="chat-status"
        onSubmit={(event) => {
          event.preventDefault()
          void submitMessage()
        }}
      >
        {attachments.items.length > 0 && <ul className="attachment-list" aria-label="Project attachments">
          {attachments.items.map(item => <li key={item.key} className={`attachment attachment-${item.state.toLowerCase()}`}>
            <label className="attachment-selection"><input type="checkbox" checked={item.selected} disabled={pending}
              onChange={() => attachments.select(item.key)} aria-label={`Use ${item.filename} in questions`} />
              <span className="attachment-name">{item.filename}</span></label>
            <button type="button" className="attachment-remove" disabled={pending || item.state === 'UPLOADING'}
              onClick={() => void attachments.remove(item.key)} aria-label={`Remove ${item.filename}`}>×</button>
            <span className="attachment-progress" role="status">{item.error || item.progress}</span>
            {item.warnings.length > 0 && <details className="attachment-notes"><summary>Extraction notes</summary><ul>{item.warnings.map(w => <li key={w}>{w}</li>)}</ul></details>}
          </li>)}
        </ul>}
        <label className="sr-only" htmlFor="message">
          Message E.R.R.O. AI
        </label>
        <textarea
          id="message"
          rows={5}
          placeholder="Describe your project or ask about a component…"
          ref={composer}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (
              event.key === 'Enter' &&
              !event.shiftKey &&
              !event.nativeEvent.isComposing
            ) {
              event.preventDefault()
              void submitMessage()
            }
          }}
          readOnly={pending || saved.loading}
          maxLength={8000}
          aria-describedby="chat-status"
        />
        <div className="composer-toolbar">
          <input className="sr-only" ref={picker} type="file" multiple accept={FILE_TYPES} tabIndex={-1} aria-label="Attach project files"
            onChange={event => { void attachments.add(Array.from(event.target.files ?? [])); event.target.value = '' }} />
          <button className="attach-button" type="button" disabled={pending || saved.loading || !saved.chatId || attachments.items.length >= 8} onClick={() => picker.current?.click()}>
            <span aria-hidden="true">+</span> Attach files
          </button>
          <span className="composer-status">
            {pending
              ? 'Waiting for a reply…'
              : 'Enter to send · Shift + Enter for a new line'}
          </span>
          <button
            className="send-button"
            type="submit"
            disabled={pending || saved.loading || !saved.chatId || attachments.blocked || !draft.trim()}
            aria-label="Send message"
          >
            <span>Send</span><Arrow />
          </button>
        </div>
      </form>
      <p className="attachment-help">Drop files here · PDF, images, Word, Excel, CSV or text · 20 MiB each, up to 8 files.
        {attachments.items.length > 0 && ' Selected files stay with this chat for follow-ups.'}
        {' '}Extracted content is saved with the chat; relevant content is sent to the AI provider.</p>
      {attachments.blocked && <p className="attachment-help" role="status">Selected files are processing or need attention. Deselect them to ask without them.</p>}
      {attachments.error && <p className="chat-error" role="alert">{attachments.error}</p>}
      {error && (
        <p className="chat-error" role="alert">
          {error} {!saved.session && <button type="button" onClick={() => void saved.initialize(true)}>Retry</button>}
        </p>
      )}
      <p className="chat-status" id="chat-status">
        Chats are saved and linked to this browser. No sign-in needed. Use “Delete all my data” to start anew.
      </p>
      {messages.length === 0 && <div className="starter-prompts" aria-label="Ideas to get started">
        {starterPrompts.map(prompt => <button key={prompt.title} type="button" onClick={() => {
          setDraft(prompt.message)
          setError('')
          composer.current?.focus({ preventScroll: true })
        }}>
          <span className="starter-heading">{prompt.title}<Arrow diagonal /></span>
          <span className="starter-description">{prompt.description}</span>
        </button>)}
      </div>}
      {messages.length === 0 && <p className="market-note">Electrical equipment &amp; components <span aria-hidden="true">·</span> Estonia / EU</p>}
    </div>
  )
}
