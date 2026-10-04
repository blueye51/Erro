import { useEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import { sendMessage } from './chat'
import './App.css'

const iconPaths = {
  chat: <path d="M20 11.5a8 8 0 0 1-8 8H4l1.6-4A8 8 0 1 1 20 11.5Z" />,
  arrow: <path d="M12 19V5m-6 6 6-6 6 6" />,
  bolt: <path d="m13 3-8 11h6l-1 7 9-12h-6l1-6Z" />,
  spark: (
    <path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z" />
  ),
  folder: (
    <path d="M3 7a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
  ),
  chevron: <path d="m9 5 7 7-7 7" />,
}

function Icon({ name }: { name: keyof typeof iconPaths }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {iconPaths[name]}
    </svg>
  )
}

function BrandMark() {
  return (
    <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <path d="M14 9h9v9M9 14v9h9" stroke="currentColor" strokeWidth="2.5" />
      <rect x="4" y="4" width="11" height="11" rx="3" fill="currentColor" />
      <rect x="17" y="17" width="11" height="11" rx="3" fill="currentColor" />
    </svg>
  )
}

const possibilities = [
  {
    icon: 'bolt',
    title: 'Follow a spark',
    description: 'Explore ideas around electrical work.',
  },
  {
    icon: 'spark',
    title: 'Think a little differently',
    description: 'Find new ways to create and connect.',
  },
  {
    icon: 'folder',
    title: 'Make something ours',
    description: 'A future home for the things we build.',
  },
] as const

function App() {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<{ id: number; role: 'user' | 'assistant'; text: string }[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const sending = useRef(false)
  const nextId = useRef(0)
  const messageEnd = useRef<HTMLDivElement>(null)
  const composer = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    messageEnd.current?.scrollIntoView({ block: 'nearest' })
  }, [messages, pending])

  async function submitMessage() {
    const text = draft.trim()
    if (!text || sending.current) return
    sending.current = true
    setPending(true)
    setError('')
    const id = nextId.current++
    setMessages((current) => [...current, { id, role: 'user', text }])
    setDraft('')
    try {
      const reply = await sendMessage(text)
      const replyId = nextId.current++
      setMessages((current) => [...current, { id: replyId, role: 'assistant', text: reply }])
    } catch (failure) {
      setMessages((current) => current.filter((message) => message.id !== id))
      setDraft(text)
      setError(failure instanceof Error ? failure.message : 'Something went wrong. Please try again.')
    } finally {
      sending.current = false
      setPending(false)
      composer.current?.focus()
    }
  }

  return (
    <div className={`workspace${messages.length ? ' is-chatting' : ''}`}>
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <aside className="sidebar" aria-label="Workspace sidebar">
        <a className="brand" href="#main" aria-label="E.R.R.O. home">
          <span className="brand-symbol">
            <BrandMark />
          </span>
          <span>E.R.R.O.</span>
        </a>

        <nav className="workspace-nav" aria-label="Workspace">
          <p className="section-label">Your workspace</p>
          <a className="current-conversation" href="#main" aria-current="page">
            <Icon name="chat" />
            Chat
            <span className="nav-dot" />
          </a>
        </nav>

        <div className="sidebar-bottom">
          <details className="project-note">
            <summary>
              <span className="note-dot" />
              A project in the making
              <Icon name="chevron" />
            </summary>
            <p>
              We’re Eric and Robin. This is our starting point for building
              something together. The direction is still open. That’s part of
              the adventure.
            </p>
          </details>

          <div className="founders">
            <div className="avatars" aria-hidden="true">
              <span>E</span>
              <span>R</span>
            </div>
            <div>
              <p>Eric &amp; Robin</p>
              <span>Two friends. One beginning.</span>
            </div>
          </div>
        </div>
      </aside>

      <main className="main-panel" id="main" tabIndex={-1}>
        <header className="workspace-header">
          <span className="desktop-header">
            The workspace <span>/</span> <strong>Chat</strong>
          </span>
          <a className="mobile-brand" href="#main" aria-label="E.R.R.O. home">
            <BrandMark />
            <span>E.R.R.O.</span>
          </a>
          <span className="preview-badge">
            <span />
            Early preview
          </span>
        </header>

        <div className={`chat-content${messages.length ? ' has-messages' : ''}`}>
          {!messages.length && <section className="welcome" aria-labelledby="welcome-title">
            <div className="welcome-mark">
              <BrandMark />
            </div>
            <p className="welcome-eyebrow">A space for what comes next</p>
            <h1 id="welcome-title">
              Every idea starts
              <br />
              <em>somewhere.</em>
            </h1>
            <p className="welcome-description">
              A little space to think, explore, and build something together.
              <br className="desktop-break" /> Welcome to the beginning of
              E.R.R.O.
            </p>
          </section>}

          {messages.length > 0 && <section className="message-list" role="log" tabIndex={0} aria-label="Chat messages" aria-live="polite" aria-relevant="additions text">
            {messages.map((message) => (
              <article className={`message message-${message.role}`} key={message.id}>
                <h2 className="message-author">{message.role === 'user' ? 'You' : 'E.R.R.O. AI'}</h2>
                <div className="message-body">
                  {message.role === 'user' ? <p>{message.text}</p> : (
                    <Markdown
                      skipHtml
                      components={{
                        img: ({ alt }) => <span>{alt}</span>,
                        pre: ({ children }) => <pre tabIndex={0} aria-label="Code block">{children}</pre>,
                        a: ({ href, children }) => <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>,
                      }}
                    >{message.text}</Markdown>
                  )}
                </div>
              </article>
            ))}
            {pending && <p className="reply-pending" role="status">Thinking…</p>}
            <div ref={messageEnd} />
          </section>}

          <form
            className="chat-composer"
            aria-label="Send a message"
            aria-describedby="chat-status"
            onSubmit={(event) => { event.preventDefault(); void submitMessage() }}
          >
            <label className="sr-only" htmlFor="message">
              Message E.R.R.O.
            </label>
            <textarea
              id="message"
              rows={2}
              placeholder="A place for your next idea…"
              ref={composer}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                  event.preventDefault()
                  void submitMessage()
                }
              }}
              readOnly={pending}
              maxLength={8000}
              aria-describedby="chat-status"
            />
            <div className="composer-toolbar">
              <span className="composer-status">
                <span />
                {pending ? 'Waiting for a reply' : 'Ask something, explore an idea'}
              </span>
              <button
                className="send-button"
                type="submit"
                disabled={pending || !draft.trim()}
                aria-label="Send message"
              >
                <Icon name="arrow" />
              </button>
            </div>
          </form>
          {error && <p className="chat-error" role="alert">{error}</p>}
          <p className="chat-status" id="chat-status">
            Each message starts fresh. This page clears when you leave or refresh.
          </p>

          {!messages.length && <section
            className="possibilities"
            aria-labelledby="possibilities-title"
          >
            <h2 className="section-label" id="possibilities-title">
              A few things on our minds
            </h2>
            <ul>
              {possibilities.map(({ icon, title, description }) => (
                <li key={title}>
                  <span className="possibility-icon">
                    <Icon name={icon} />
                  </span>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </li>
              ))}
            </ul>
          </section>}
        </div>

        <footer className="workspace-footer">
          <span>
            A project by <strong>Eric &amp; Robin</strong>
          </span>
          <span className="footer-note">
            <span />
            Still taking shape
          </span>
        </footer>
      </main>
    </div>
  )
}

export default App
