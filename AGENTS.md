# Project context

Read `docs/project-context.md` before working on this repository. It records
E.R.R.O.'s founders, tentative direction, and current website requirements.

The confirmed feature is a minimal stateless AI chat: frontend → backend chat
API → AI provider → frontend reply. Keep messages in page memory only and send
only the current message. Do not add saved history, conversation management,
accounts, uploads, tools, or database storage without a new request. AI credentials
belong only in backend configuration. Preserve the distinction between possible
future ideas and confirmed product decisions.

Run `npm run build` and `npm run lint` from `web/` for frontend changes. Check
responsive layout, chat sending, loading/error states, reply formatting, and
clearing messages on refresh when changing the interface.

For backend or infrastructure changes, validate `docker compose config --quiet`,
build the affected images, and verify the backend's startup connection checks
against the Compose services. See `docs/infrastructure.md` for setup and versions.
Run Maven `verify` for backend changes; the chat tests use a local mock AI provider
and must not require real credentials. See `docs/chat.md` for the API contract.

Document every new feature, infrastructure change, configuration option, and
development workflow in `docs/` as part of the same change. Include setup and
usage, relevant decisions, and verification results or limitations. Update
`docs/README.md` when adding a document, and `docs/project-context.md` when scope
or product decisions change. Keep the root README's quick-start instructions
consistent with the detailed docs. Do not document real secrets.
