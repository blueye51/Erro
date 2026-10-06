# Homepage

On 2026-10-06 Eric made the electrical assistant the main website experience.
Visitors can start typing immediately. This replaces the earlier founder-first
homepage and collapsed AI corner, while retaining the cream/green palette, serif
headings, brand mark, founder photos and supplied contact email.

## Usage

- **Chat** (the default view, also `/#home`) opens the main assistant workspace.
  The initial view offers motor protection, component comparison and parts-list
  prompts. Clicking one fills the composer for editing; only Send or Enter submits.
- **About us** (`/#about`) shows Eric Rand and Robin Robert Antonis, their supplied
  photos and draft bios. **Contact us** (`/#contact`) shows the supplied
  `eric.rand66@gmail.com` address and a `mailto:` button. The site does not send
  email or collect form submissions.
- Top navigation and browser back/forward switch views. The same Chat component
  stays mounted while hidden, preserving messages, draft and active problem
  context. Requests can finish while viewing About us or Contact us without
  stealing focus. Direct links work after refresh.
- **New problem** clears messages, draft and active problem context. A full refresh
  also clears them; no chat data enters local/session storage or cookies.
- Enter sends; Shift+Enter inserts a newline. Pending requests disable duplicate
  sends. Errors restore the draft for retry. Replies retain safe Markdown and
  expandable sources, calculations, assumptions, warnings and missing information.
- **Knowledge management** remains available in the footer at `/#knowledge`, with
  backend token protection. Leaving that view unmounts its token state.

The chat occupies the main reading area. During a conversation, messages scroll
within that area while the composer remains below them. Smaller screens use
stacked starter prompts and compact navigation. The layout uses dynamic viewport
height for the conversation and allows page scrolling on short screens.

## Scope and planned product data

Eric plans to connect product information, quantities, shipping and prices to
support customer project plans. This change prepares the entry experience and
parts-list prompt, but introduces no stock, pricing, delivery or ordering API.
The frontend does not display invented products, quotes or availability. Existing
backend knowledge retrieval, technical validation and catalog candidates remain
in use without changes to the chat API or Railway variables.

## Components, copy and photos

- `web/src/App.tsx`: hash navigation, page titles, focus, header and footer.
- `web/src/Chat.tsx`: existing chat flow, composer, starter prompts and active
  problem context; reuses `chat.ts` and `AnswerDetails.tsx`.
- `web/src/InfoPages.tsx`: founder cards and contact view.
- `web/src/Brand.tsx`: existing brand mark and arrow icons.
- `web/src/App.css`: responsive layout and visual styling.
- `web/index.html`: initial page title and search description.

The photos remain unchanged at `web/public/photos/eric.png` and `robin.png`.
The names/email were supplied by Eric. Founder bios are editable provisional
copy, not verified qualifications or business credentials. No external fonts,
tracking scripts or new UI dependencies are introduced.

Navigation uses browser hashes with no router dependency. The active navigation
link uses `aria-current`; navigation focuses main content and scrolls to the top.
The skip link focuses the current view without changing it. Visible keyboard
focus, reduced-motion support and semantic chat status/error announcements remain.

## Local development and verification

```sh
cd web
npm ci
npm run dev
```

Vite proxies `/api` to the existing backend. See [Chat](chat.md) for provider
configuration. Run `npm run build` and `npm run lint` from `web/`. For the isolated
browser fixture, run `./scripts/verify-browser.sh` from the repository root after
installing Playwright Chromium as described in [Electrical knowledge](knowledge.md).
It uses the production Nginx image, real backend and a mock AI provider.

Rebuild/redeploy the existing web service for production. Backend configuration,
database schema, domain and Railway variables need no changes for this UI update.

## Verification — 2026-10-06

- `npm run build` and `npm run lint` passed.
- The production web Docker image built successfully. The isolated browser
  fixture passed against the real backend, PostgreSQL, Redis and local MinIO,
  with successful backend startup connection checks and a mock AI provider.
- Chromium checked widths 1440, 768, 390 and 320px: no horizontal overflow, and
  the composer was visible in the initial viewport. Desktop/mobile screenshots
  were inspected; small-screen navigation keeps each link on one line.
- Checks covered editable starter prompts, About/contact navigation, contact
  email links, draft preservation, browser back, direct links after refresh,
  sending/loading/errors, citations, calculations, follow-up context, New problem,
  refresh clearing, and protected admin ingestion/debugging. No browser errors
  or local/session storage entries occurred.

No live AI provider or Railway deployment is exercised by the local fixture.

## Table rendering — 2026-10-06

Chat now uses `remark-gfm` to render Markdown tables as actual rows, column headers
and cells. Table contents are preserved, with cell borders, alternating backgrounds
and horizontal scrolling by touch or keyboard on smaller screens. This fixes the
raw pipe/separator text previously visible in replies. See
[Reply formatting](chat.md#reply-formatting) for parser and security behavior.

The browser fixture includes a table reply with long prose, bold text, a link and
an escaped pipe inside inline code. It checks the table structure, code/list
formatting, raw HTML and unsafe-link handling, page overflow and keyboard scrolling
at desktop/mobile sizes, and refresh clearing. It uses generated fixture content
rather than saving a customer's installation details.

`npm run build`, `npm run lint`, the production web image build and the isolated
browser suite passed. Table rendering was checked at 1440, 768, 390 and 320px;
desktop/mobile screenshots were inspected. Header/cell structure, escaped pipes,
inline formatting, preserved code blocks, keyboard horizontal scrolling and
HTML/URL safety checks all passed. Railway deployment remains outside this local
verification.
