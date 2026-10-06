import { Arrow } from './Brand'

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

export function AboutUs() {
  return (
        <section
          className="about-section page-width"
          id="about"
          aria-labelledby="about-title"
        >
          <div className="section-heading">
            <div>
              <p className="eyebrow">The people behind E.R.R.O.</p>
              <h1 id="about-title">
                Different minds.
                <br />
                <em>A shared ambition.</em>
              </h1>
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
              We’re building a clearer starting point for electrical projects.
            </p>
          </div>
        </section>

  )
}

export function ContactUs() {
  return (
        <section
          className="contact-section"
          id="contact"
          aria-labelledby="contact-title"
        >
          <div className="contact-inner page-width">
            <div>
              <p className="eyebrow">Contact us</p>
              <h1 id="contact-title">
                Good things start
                <br />
                <em>with a conversation.</em>
              </h1>
            </div>
            <div className="contact-copy">
              <p>
                Have a question about E.R.R.O., feedback on the assistant, or an
                idea for working together? We’d love to hear from you.
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
  )
}
