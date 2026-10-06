import { useEffect, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import { sendMessage, type ChatReply } from './chat'
import { AnswerDetails } from './AnswerDetails'
import { Arrow } from './Brand'

const starterPrompts = [
  { title: 'Protect a motor', description: 'Work through the protection requirements.', message: 'I have a 400 V three-phase 7.5 kW motor and need protection for it. What information do we need to select the components?' },
  { title: 'Compare components', description: 'Understand ratings and compatibility.', message: 'Help me compare two contactors for my application. Which ratings and manufacturer details should I provide?' },
  { title: 'Plan a parts list', description: 'Turn your project into a starting list.', message: 'Help me plan a parts list for a small motor control panel. Ask me about the equipment, supply and quantities first, and flag anything that needs confirmation.' },
]

export function Chat({ active }: { active: boolean }) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<
    { id: number; role: 'user' | 'assistant'; text: string; answer?: ChatReply }[]
  >([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const sending = useRef(false)
  const problemContext = useRef<string[]>([])
  const nextId = useRef(0)
  const messageList = useRef<HTMLElement>(null)
  const composer = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const log = messageList.current
    if (active) log?.scrollTo({ top: log.scrollHeight })
  }, [messages, pending, active])

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
      const answer = await sendMessage(text, problemContext.current)
      problemContext.current = [...(answer.contextReset ? [] : problemContext.current), text].slice(-8)
      while (problemContext.current.join('').length > 24000) problemContext.current.shift()
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
    <div className={`chat-content ${messages.length ? 'has-messages' : 'is-empty'}`}>
      {messages.length > 0 && <div className="problem-toolbar">
        <div><p className="eyebrow">Electrical assistant · Estonia / EU</p><h1>Your project</h1></div>
        <button className="problem-reset" disabled={pending} onClick={() => {
          problemContext.current = []
          setMessages([])
          setDraft('')
          setError('')
          composer.current?.focus({ preventScroll: true })
        }}><span aria-hidden="true">+</span> New problem</button>
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
            <span>Send</span><Arrow />
          </button>
        </div>
      </form>
      {error && (
        <p className="chat-error" role="alert">
          {error}
        </p>
      )}
      <p className="chat-status" id="chat-status">
        {messages.length ? 'Keep adding details here, or start a new problem for different equipment. ' : ''}Messages stay in this tab and clear on refresh.
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
