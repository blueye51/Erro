import { useEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import { sendMessage } from './chat'
import './App.css'

function Arrow({ diagonal = false }: { diagonal?: boolean }) {
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
      <path d={diagonal ? 'M6 18 18 6M6 6h12v12' : 'M4 12h16m-6-6 6 6-6 6'} />
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

const founders = [
  {
    name: 'Eric Rand',
    photo: '/photos/eric.png',
    description:
      'Big on curiosity. Bigger on ambition. Eric believes the best way to find out if an idea works is to roll up your sleeves and start building.',
    caption: 'Ideas into action.',
  },
  {
    name: 'Robin Robert Antonis',
    photo: '/photos/robin.png',
    description:
      'A fresh perspective and a head full of possibilities. Robin is all about asking “what if?” and seeing just how far a small beginning can go.',
    caption: 'Always looking ahead.',
  },
]

function Chat() {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<
    { id: number; role: 'user' | 'assistant'; text: string }[]
  >([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const sending = useRef(false)
  const nextId = useRef(0)
  const messageList = useRef<HTMLElement>(null)
  const composer = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const log = messageList.current
    log?.scrollTo({ top: log.scrollHeight })
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
      setMessages((current) => [
        ...current,
        { id: replyId, role: 'assistant', text: reply },
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
      composer.current?.focus({ preventScroll: true })
    }
  }

  return (
    <div className="chat-content">
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
                  <p>{message.text}</p>
                ) : (
                  <Markdown
                    skipHtml
                    components={{
                      img: ({ alt }) => <span>{alt}</span>,
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
            </article>
          ))}
          {pending && (
            <p className="reply-pending" role="status">
              Thinking…
            </p>
          )}
        </section>
      ) : (
        <p className="chat-intro">
          A question, a thought, a starting point. Give our AI a try.
        </p>
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
        <label className="sr-only" htmlFor="message">
          Message E.R.R.O. AI
        </label>
        <textarea
          id="message"
          rows={3}
          placeholder="What’s on your mind?"
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
          <span className="composer-status">
            {pending
              ? 'Waiting for a reply…'
              : 'Enter to send · Shift + Enter for a new line'}
          </span>
          <button
            className="send-button"
            type="submit"
            disabled={pending || !draft.trim()}
            aria-label="Send message"
          >
            <Arrow />
          </button>
        </div>
      </form>
      {error && (
        <p className="chat-error" role="alert">
          {error}
        </p>
      )}
      <p className="chat-status" id="chat-status">
        Each message starts fresh. Messages clear when you leave or refresh.
      </p>
    </div>
  )
}

function App() {
  return (
    <div className="site" id="home">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#home" aria-label="E.R.R.O. home">
            <BrandMark />
            <span>E.R.R.O.</span>
          </a>
          <nav className="site-nav" aria-label="Main navigation">
            <a href="#home">Home</a>
            <a href="#about">About us</a>
            <a href="#contact">
              Contact <Arrow diagonal />
            </a>
          </nav>
        </div>
      </header>

      <main id="main" tabIndex={-1}>
        <section className="hero page-width" aria-labelledby="hero-title">
          <div className="hero-copy">
            <p className="eyebrow">
              <span className="status-dot" /> Two friends. A world of
              possibilities.
            </p>
            <h1 id="hero-title">
              Small beginnings.
              <br />
              <em>Big ambitions.</em>
            </h1>
            <p className="hero-description">
              We’re Eric and Robin. Two young entrepreneurs with big dreams,
              restless curiosity, and the drive to make something of our own.
            </p>
            <a className="button button-green" href="#about">
              Meet the people behind it <Arrow />
            </a>
            <p className="hero-footnote">
              This is E.R.R.O. And we’re just getting started.
            </p>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="art-grid" />
            <span className="art-caption">A shared beginning</span>
            <div className="art-square art-square-eric">
              <span>E.</span>
              <small>Curiosity</small>
            </div>
            <div className="art-square art-square-robin">
              <span>R.</span>
              <small>Possibility</small>
            </div>
            <div className="art-asterisk">✳</div>
            <span className="art-bottom">
              Two minds. One direction: forward.
            </span>
          </div>
        </section>

        <div className="values-strip">
          <div className="page-width values-inner">
            <span>A little of what drives us</span>
            <p>Stay curious.</p>
            <span className="values-star" aria-hidden="true">
              ✳
            </span>
            <p>Dream bigger.</p>
            <span className="values-star" aria-hidden="true">
              ✳
            </span>
            <p>Build together.</p>
          </div>
        </div>

        <section
          className="about-section page-width"
          id="about"
          aria-labelledby="about-title"
        >
          <div className="section-heading">
            <div>
              <p className="eyebrow">01 / About us</p>
              <h2 id="about-title">
                Different minds.
                <br />
                <em>A shared ambition.</em>
              </h2>
            </div>
            <p className="section-description">
              A friendship, a few big ideas, and a name made from both of ours.
              We’re learning as we go, exploring what’s possible, and putting in
              the work to find our own way.
            </p>
          </div>
          <div className="founder-grid">
            {founders.map((founder, index) => (
              <article className="founder-card" key={founder.name}>
                <div className={`founder-photo founder-photo-${index}`}>
                  <img
                    src={founder.photo}
                    alt={`Photo chosen for ${founder.name}`}
                    width={index === 0 ? 934 : 800}
                    height={index === 0 ? 1127 : 1200}
                    loading="lazy"
                    decoding="async"
                  />
                  <span className="photo-caption">{founder.caption}</span>
                </div>
                <div className="founder-title">
                  <h3>{founder.name}</h3>
                  <span>0{index + 1}</span>
                </div>
                <p className="founder-role">Co-founder · E.R.R.O.</p>
                <p className="founder-description">{founder.description}</p>
              </article>
            ))}
          </div>
          <div className="about-note">
            <span className="status-dot" />
            <p>
              Our next chapter is still open. That’s what makes it exciting.
            </p>
          </div>
        </section>

        <section
          className="explore-section page-width"
          id="explore"
          aria-labelledby="explore-title"
        >
          <div className="explore-copy">
            <p className="eyebrow">A small experiment</p>
            <h2 id="explore-title">
              Curiosity starts
              <br />
              <em>with a question.</em>
            </h2>
            <p>
              We like trying things. Our AI corner is a little space to explore
              an idea or get a fresh starting point.
            </p>
          </div>
          <details className="chat-panel">
            <summary>
              <span>
                <span className="eyebrow">The AI corner</span>
                <span className="chat-summary-title">What’s on your mind?</span>
              </span>
              <span className="chat-toggle">
                <Arrow diagonal />
              </span>
              <span className="sr-only">Open or close AI chat</span>
            </summary>
            <Chat />
          </details>
        </section>

        <section
          className="contact-section"
          id="contact"
          aria-labelledby="contact-title"
        >
          <div className="contact-inner page-width">
            <div>
              <p className="eyebrow">02 / Contact</p>
              <h2 id="contact-title">
                Good things start
                <br />
                <em>with a conversation.</em>
              </h2>
            </div>
            <div className="contact-copy">
              <p>
                Got an idea? Curious about what comes next? We’d love to connect
                with people who think big, too.
              </p>
              <a className="contact-email" href="mailto:eric.rand66@gmail.com">
                eric.rand66@gmail.com
              </a>
              <a
                className="button button-light"
                href="mailto:eric.rand66@gmail.com"
              >
                Say hello <Arrow diagonal />
              </a>
              <p className="contact-note">
                A simple hello is a pretty good place to start.
              </p>
            </div>
          </div>
        </section>
      </main>

      <footer className="site-footer page-width">
        <a className="brand" href="#home" aria-label="E.R.R.O. home">
          <BrandMark />
          <span>E.R.R.O.</span>
        </a>
        <p>A project by Eric &amp; Robin.</p>
        <a href="#home">
          Back to top <Arrow diagonal />
        </a>
      </footer>
    </div>
  )
}

export default App
