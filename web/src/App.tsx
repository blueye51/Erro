import './App.css'

const iconPaths = {
  plus: <path d="M12 5v14M5 12h14" />,
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
  return (
    <div className="workspace">
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

        <button
          className="new-chat"
          type="button"
          disabled
          title="Conversations are coming soon"
        >
          <Icon name="plus" />
          New conversation
          <span className="soon-label">Soon</span>
        </button>

        <nav className="workspace-nav" aria-label="Workspace">
          <p className="section-label">Your workspace</p>
          <a className="current-conversation" href="#main" aria-current="page">
            <Icon name="chat" />
            A new beginning
            <span className="nav-dot" />
          </a>
        </nav>

        <div className="conversation-history">
          <p className="section-label">Conversations</p>
          <p className="history-placeholder">A blank page, for now.</p>
        </div>

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
            The workspace <span>/</span> <strong>A new beginning</strong>
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

        <div className="chat-content">
          <section className="welcome" aria-labelledby="welcome-title">
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
          </section>

          <section
            className="chat-composer"
            aria-label="Chat preview"
            aria-describedby="chat-status"
          >
            <label className="sr-only" htmlFor="message">
              Message E.R.R.O. — coming soon
            </label>
            <textarea
              id="message"
              rows={2}
              placeholder="A place for your next idea…"
              disabled
              aria-describedby="chat-status"
            />
            <div className="composer-toolbar">
              <button
                className="attachment-button"
                type="button"
                disabled
                aria-label="Add attachment (coming soon)"
              >
                <Icon name="plus" />
              </button>
              <span className="composer-status">
                <span />
                Chat is coming soon
              </span>
              <button
                className="send-button"
                type="button"
                disabled
                aria-label="Send message (coming soon)"
              >
                <Icon name="arrow" />
              </button>
            </div>
          </section>
          <p className="chat-status" id="chat-status">
            Just a first look. Messaging isn’t available yet.
          </p>

          <section
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
          </section>
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
