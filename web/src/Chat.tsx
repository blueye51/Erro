import { useEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { rememberMessage, sendMessage, type ChatReply } from './chat'
import { AnswerDetails } from './AnswerDetails'
import { Arrow } from './Brand'
import { FILE_TYPES, useAttachments } from './attachments'

const starterPrompts = [
  { title: 'Protect a motor', description: 'Work through the protection requirements.', message: 'I have a 400 V three-phase 7.5 kW motor and need protection for it. What information do we need to select the components?' },
  { title: 'Compare components', description: 'Understand ratings and compatibility.', message: 'Help me compare two contactors for my application. Which ratings and manufacturer details should I provide?' },
  { title: 'Plan a parts list', description: 'Turn your project into a starting list.', message: 'Help me plan a parts list for a small motor control panel. Ask me about the equipment, supply and quantities first, and flag anything that needs confirmation.' },
]

export function Chat({ active }: { active: boolean }) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<
    { id: number; role: 'user' | 'assistant'; text: string; answer?: ChatReply; filenames?: string[] }[]
  >([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const sending = useRef(false)
  const [problemContext, setProblemContext] = useState<string[]>([])
  const [memoryNotice, setMemoryNotice] = useState('')
  const nextId = useRef(0)
  const messageList = useRef<HTMLElement>(null)
  const composer = useRef<HTMLTextAreaElement>(null)
  const picker = useRef<HTMLInputElement>(null)
  const attachments = useAttachments()
  const [dragging, setDragging] = useState(false)

  function newProblem() {
    attachments.reset()
    setProblemContext([]); setMemoryNotice(''); setMessages([]); setDraft(''); setError('')
    composer.current?.focus({ preventScroll: true })
  }

  useEffect(() => {
    const restored = (event: PageTransitionEvent) => { if (event.persisted) window.location.reload() }
    window.addEventListener('pageshow', restored)
    return () => window.removeEventListener('pageshow', restored)
  }, [])

  useEffect(() => {
    const log = messageList.current
    if (active) log?.scrollTo({ top: log.scrollHeight })
  }, [messages, pending, active])

  async function submitMessage() {
    const text = draft.trim()
    if (!text || sending.current || attachments.blocked) return
    sending.current = true
    setPending(true)
    setError('')
    const id = nextId.current++
    setMessages((current) => [...current, { id, role: 'user', text,
      filenames: attachments.items.filter(i => i.selected && i.state === 'READY').map(i => i.filename) }])
    setDraft('')
    try {
      const answer = await sendMessage(text, problemContext, attachments.token.current, attachments.ids)
      if (answer.contextReset) attachments.reset()
      setProblemContext(rememberMessage(problemContext, text, answer.contextReset))
      setMemoryNotice(answer.contextReset ? 'Chat memory restarted because the problem changed.' : '')
      const replyId = nextId.current++
      setMessages((current) => [
        ...current,
        { id: replyId, role: 'assistant', text: answer.reply, answer },
      ])
    } catch (failure) {
      setMessages((current) => current.filter((message) => message.id !== id))
      setDraft(text)
      setError(
        failure instanceof Error
          ? failure.message
          : 'Something went wrong. Please try again.',
      )
    } finally {
      sending.current = false
      setPending(false)
      if (composer.current?.offsetParent) composer.current.focus({ preventScroll: true })
    }
  }

  return (
    <div className={`chat-content ${messages.length ? 'has-messages' : 'is-empty'} ${dragging ? 'is-dragging' : ''}`}
      onDragOver={event => { if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); if (!pending) setDragging(true) } }}
      onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false) }}
      onDrop={event => { event.preventDefault(); setDragging(false); if (!pending) void attachments.add(Array.from(event.dataTransfer.files)) }}>
      {(messages.length > 0 || attachments.items.length > 0) && <div className="problem-toolbar">
        <div><p className="eyebrow">Electrical assistant · Estonia / EU</p><h1>Your project</h1></div>
        <button className="problem-reset" disabled={pending} onClick={newProblem}><span aria-hidden="true">+</span> New problem</button>
      </div>}
      {messages.length > 0 ? (
        <section
          className="message-list"
          ref={messageList}
          role="log"
          tabIndex={0}
          aria-label="Chat messages"
          aria-live="polite"
          aria-relevant="additions text"
        >
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
            <p className="reply-pending" role="status">
              Thinking…
            </p>
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
          rows={3}
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
          readOnly={pending}
          maxLength={8000}
          aria-describedby="chat-status"
        />
        <div className="composer-toolbar">
          <input className="sr-only" ref={picker} type="file" multiple accept={FILE_TYPES} tabIndex={-1} aria-label="Attach project files"
            onChange={event => { void attachments.add(Array.from(event.target.files ?? [])); event.target.value = '' }} />
          <button className="attach-button" type="button" disabled={pending || attachments.items.length >= 8} onClick={() => picker.current?.click()}>
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
            disabled={pending || attachments.blocked || !draft.trim()}
            aria-label="Send message"
          >
            <span>Send</span><Arrow />
          </button>
        </div>
      </form>
      <p className="attachment-help">Drop files here · PDF, images, Word, Excel, CSV or text · 20 MiB each, up to 8 files.
        {attachments.items.length > 0 && ' Selected files stay available for follow-ups. Use New problem for a different project.'}
        {' '}Files are processed temporarily; relevant content is sent to the AI provider.</p>
      {attachments.error && <p className="chat-error" role="alert">{attachments.error}</p>}
      {error && (
        <p className="chat-error" role="alert">
          {error}
        </p>
      )}
      {memoryNotice && <p className="chat-status" role="status">{memoryNotice}</p>}
      <p className="chat-status" id="chat-status">
        {problemContext.length > 0
          ? `Remembering ${problemContext.length} of your recent messages. `
          : 'Remembers up to 8 of your recent messages. '}
        New problem or refresh clears chat memory and attachments.
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
