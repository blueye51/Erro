# Homepage

Eric requested a homepage on 2026-10-05 to introduce E.R.R.O. and its founders
before presenting the AI chat. The cream and green branding stays, with a top
navigation bar and a page that works on desktop and mobile.

## Usage

- **Home** returns to the introduction, “Small beginnings. Big ambitions.”
- **About us** and the introduction's button scroll to the founder cards for
  Eric Rand and Robin Robert Antonis.
- **Contact** scrolls to `eric.rand66@gmail.com`. The address and “Say hello”
  button open the visitor's email application using `mailto:`. The website
  does not send email or collect contact-form submissions.
- The **AI corner** below About us expands when its heading is selected.
  It retains the existing stateless chat: Enter sends, Shift+Enter adds a line,
  failed messages can be retried, and assistant replies render as Markdown.
  Collapsing the panel keeps messages until the page is refreshed or closed.

Navigation uses ordinary section anchors. No routing dependency or separate
backend endpoints were added. Keyboard users have a skip link and a native
details/summary control for the chat panel. Smooth scrolling is disabled when
the browser requests reduced motion.

## Photos and copy

The supplied `photos/eric.png` and `photos/robin.png` are copied unchanged into
`web/public/photos/`, and the frontend loads `/photos/eric.png` and
`/photos/robin.png`. Keeping the deployed assets within `web/` ensures the web
Docker build includes them without needing the repository's parent directory.
To replace a photo, update its copy under `web/public/photos/` and rebuild web.
The About us photos load lazily and use fixed containers to avoid layout jumps.

The names and contact email were supplied by Eric. Eric explicitly requested
provisional copy about two young entrepreneurs with big dreams. The founder
bios are editable draft copy, not verified personal history. No customers,
qualifications, completed projects, or established service offerings are claimed.
The hero's decorative artwork is CSS and text; no external assets, fonts, or
tracking scripts are loaded.

Edit content in `web/src/App.tsx`, section styles in `web/src/App.css`, and page
metadata in `web/index.html`. Provider credentials remain on the backend.

## Local development and verification

Use the existing frontend workflow:

```sh
cd web
npm ci
npm run dev
```

Vite proxies `/api/chat` to the existing backend. See [Chat](chat.md) for provider
configuration. Rebuild web for the production Nginx/Compose or Railway image;
this change does not require backend or infrastructure changes.

Required checks are `npm run build` and `npm run lint` from `web/`, plus browser
checks for navigation, portraits, email links, desktop/mobile layout, chat
sending/loading/errors, Markdown, and clearing messages on refresh.

## Results — 2026-10-05

- `npm run build` and `npm run lint` passed from `web/`.
- Chromium checks on the production Vite preview verified Home/About us/Contact
  navigation, both loaded photos, the two mailto links, and layouts at 1440,
  1024, 768, 700, 390, and 320px without page overflow. Desktop and mobile
  screenshots were inspected.
- Browser tests with intercepted API responses verified Enter/Shift+Enter,
  loading state, duplicate-send prevention, current-message-only payloads,
  safe errors and retry, Markdown/code formatting, and horizontal code scrolling.
  Raw HTML did not execute and Markdown images did not load external images.
- Toggling the AI corner retained messages; refresh cleared them and reset the
  panel. No local/session storage or cookies were created, and no browser
  JavaScript errors occurred.
- Axe scans of the mobile homepage with the chat closed and expanded reported
  no WCAG 2 A/AA or WCAG 2.1 AA violations. These automated checks complement
  keyboard checks and visual inspection; they are not a full accessibility audit.

Chat browser tests used mock responses and did not call a paid AI provider or
revalidate the deployed Railway origin settings. The email links were checked,
but no email was sent. Railway deployment and a browser's configured email
application remain environment-specific. Temporary browser test dependencies,
scripts, and screenshots were kept outside the repository under
`/tmp/erro-homepage-check/`; no frontend dependencies were added.
