import { useEffect, useRef, useState } from 'react'
import { BrandMark } from './Brand'
import { Chat } from './Chat'
import { AboutUs, ContactUs } from './InfoPages'
import { KnowledgeAdmin } from './KnowledgeAdmin'
import './App.css'

type Page = 'chat' | 'about' | 'contact' | 'knowledge'
function currentPage(): Page {
  const hash = window.location.hash.slice(1)
  return hash === 'about' || hash === 'contact' || hash === 'knowledge' ? hash : 'chat'
}

function App() {
  const [page, setPage] = useState(currentPage)
  const main = useRef<HTMLElement>(null)
  const previousPage = useRef(page)
  useEffect(() => {
    const changed = () => setPage(currentPage())
    window.addEventListener('hashchange', changed)
    return () => window.removeEventListener('hashchange', changed)
  }, [])
  useEffect(() => {
    const titles = { chat: 'Your electrical project starts here', about: 'About us', contact: 'Contact us', knowledge: 'Electrical knowledge' }
    document.title = `E.R.R.O. — ${titles[page]}`
    if (previousPage.current !== page) {
      if (page !== 'knowledge') main.current?.focus({ preventScroll: true })
      window.scrollTo({ top: 0, behavior: 'instant' })
      previousPage.current = page
    }
  }, [page])

  return <>
    <div className="site" hidden={page === 'knowledge'}>
      <a className="skip-link" href="#main" onClick={event => {
        event.preventDefault()
        main.current?.focus()
      }}>Skip to content</a>
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#home" aria-label="E.R.R.O. home">
            <BrandMark /><span>E.R.R.O.</span>
          </a>
          <nav className="site-nav" aria-label="Main navigation">
            <a href="#home" aria-current={page === 'chat' ? 'page' : undefined}>Chat</a>
            <a href="#about" aria-current={page === 'about' ? 'page' : undefined}>About us</a>
            <a href="#contact" aria-current={page === 'contact' ? 'page' : undefined}>Contact us</a>
          </nav>
        </div>
      </header>
      <main id="main" ref={main} tabIndex={-1}>
        <section className="chat-workspace" aria-label="Electrical assistant" hidden={page !== 'chat'}>
          <Chat active={page === 'chat'} />
        </section>
        {page === 'about' && <AboutUs />}
        {page === 'contact' && <ContactUs />}
      </main>
      <footer className="site-footer page-width">
        <p>A project by <a href="#about">Eric &amp; Robin</a>.</p>
        <a href="#knowledge">Knowledge management</a>
      </footer>
    </div>
    {page === 'knowledge' && <KnowledgeAdmin />}
  </>
}

export default App
