# E.R.R.O. project context

Last updated: 2026-10-02

## People and name

E.R.R.O. is a shared project by **Eric and Robin**. Eric is the repository owner
and the person working with the AI assistant. Robin is Eric's friend. The name
comes from their names; no expanded phrase or other acronym has been agreed.

They want a starting point for making a project together. They do not yet have
a fixed business plan, product specification, or final direction.

## Ideas being explored

Possible directions include electrical work, marketing, and AI that could help
with electrical topics or tasks. These are initial ideas, not confirmed services
or product commitments. The site might instead become a portfolio or a home for
their future projects. Other directions remain possible.

Do not present E.R.R.O. as an established business, a working AI assistant, or a
finished product. Do not invent credentials, customers, projects, testimonials,
or promises about what Eric or Robin can provide.

## Current website

The immediate goal is a branded, responsive chat interface as a visual starting
point. It should feel like a normal chat workspace while clearly showing that
messaging is not available yet.

Current requirements:

- Frontend only: React and TypeScript on Vite, in `web/`.
- Visitors cannot type or send messages through the disabled composer.
- No chat submission handlers, generated replies, fake conversation history,
  AI integrations, API requests, backend, uploads, accounts, or data persistence.
- Attachment, send, and new-conversation controls remain visibly disabled.
- Local presentation interactions, such as opening the project note, are fine.
- Serve assets locally; no third-party fonts, analytics, or external services.
- Keep the branding and copy open enough to support a later change of direction.

Network requests for ordinary page assets are expected. The restriction is on
sending messages, contacting APIs, or adding other service integrations. Vite's
development tooling also uses a connection for live updates during local work.

The displayed possibilities are ideas, not clickable prompts or working tools.
The page identifies E.R.R.O. as a project by Eric and Robin and labels the chat
as an early preview.

## Repository and hosting

- Repository: <https://github.com/blueye51/Erro>.
- `develop` is the working branch; `main` is used for the Railway deployment.
- The website domain is **erro.ink**.
- Railway builds the `web/` directory with Railpack and `npm run build`.
- Root `compose.yaml` runs the Vite development server at
  <http://localhost:5173> with live updates.
- See the [root README](../README.md) for local setup and checks.

## Guidance for future AI work

Read this document before proposing features or changing the site's identity.
Treat Eric and Robin's uncertainty as intentional. Ask about consequential
product decisions instead of selecting a business model on their behalf.

Keep the chat inactive until the user explicitly requests working chat.
Do not add an API, AI provider, backend, authentication, tracking, or an electrical
advice workflow just because the page resembles a chat product. Record later
decisions here so future work builds on confirmed direction.
