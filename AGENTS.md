# Project context

Read `docs/project-context.md` before working on this repository. It records
E.R.R.O.'s founders, tentative direction, and current website requirements.

The confirmed product is an Estonia/EU electrical equipment selection assistant
with knowledge retrieval, citations, deterministic calculations/validation and
protected source/catalog management. Read `docs/knowledge.md` for boundaries.
Chat/problem context stays in page memory; only bounded prior user inputs may be
sent with a follow-up. Do not add saved chats, visitor accounts, arbitrary URL
fetching, file uploads, tools or purchasing without a new request. Knowledge and
catalog data use Flyway-managed PostgreSQL tables in `erro_knowledge`. Credentials
and the admin token belong only in backend configuration. Do not index unlicensed
standards or treat scope metadata as full requirements.

Run `npm run build` and `npm run lint` from `web/` for frontend changes. Check
responsive layout, chat sending, loading/error states, reply formatting, and
clearing messages on refresh when changing the interface.

For backend or infrastructure changes, validate `docker compose config --quiet`,
build the affected images, and verify the backend's startup connection checks
against the Compose services. See `docs/infrastructure.md` for setup and versions.
Run Maven `verify` and `scripts/verify-knowledge.sh` for backend changes; the chat tests use a local mock AI provider
and must not require real credentials. See `docs/chat.md` for the API contract.

Document every new feature, infrastructure change, configuration option, and
development workflow in `docs/` as part of the same change. Include setup and
usage, relevant decisions, and verification results or limitations. Update
`docs/README.md` when adding a document, and `docs/project-context.md` when scope
or product decisions change. Keep the root README's quick-start instructions
consistent with the detailed docs. Do not document real secrets.
