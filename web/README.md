# E.R.R.O. web

React + TypeScript on Vite. The homepage opens directly into the electrical
assistant, with About us and Contact us available through hash navigation.
Messages go to `POST /api/chat` and replies render as Markdown with expandable
engineering details. Messages, drafts and bounded prior user inputs stay in page
memory across navigation; refresh or New problem clears them. See
[Homepage](../docs/homepage.md) for components and behavior.

Read [project context](../docs/project-context.md) before adding features.
See [Chat](../docs/chat.md) for backend setup and the complete API contract.

```sh
npm ci
npm run dev
```

```sh
npm run lint
npm run build
```

Alternatively, run `docker compose up` from the repository root. See the
[root README](../README.md) for details.

Production uses `Dockerfile` to build React and run Nginx as an unprivileged user.
Nginx serves the website and forwards `/api/*` to `API_UPSTREAM`, defaulting to
`http://backend:8080`. Set the private backend origin on the Railway web service.
The browser always calls `/api/chat`; remove the old `VITE_API_BASE_URL` variable.
Use `/healthz` for the web service's healthcheck and target port 8080.

Compose's default `web` service runs this image at <http://localhost:5173>.
For live reload, run `docker compose --profile dev up -d web-dev` and use
<http://localhost:5174>. Vite proxies `/api` to `http://localhost:8080` on the host;
Compose sets `BACKEND_PROXY_TARGET=http://backend:8080` for `web-dev`.

See [Web proxy](../docs/web-proxy.md) for settings, request routing, and tests,
and [Railway setup](../docs/railway.md) for the deployment migration. API keys
must never use a `VITE_` prefix or enter `web/`.

## Electrical knowledge UI tests

The existing chat now renders source, calculation, assumption, warning and missing
information details. Knowledge management is at `/#knowledge`; the operator token
stays in page memory. See [the knowledge guide](../docs/knowledge.md).

Replies also render Markdown tables using `remark-gfm`, with horizontally
scrollable cells on narrow screens. See [reply formatting](../docs/chat.md#reply-formatting).

After `npm ci` and `npx playwright install chromium`, run
`../scripts/verify-browser.sh` from `web/` (or `./scripts/verify-browser.sh` from
the repository root). The runner creates an isolated real web/backend/database
stack with a mock AI and cleans up its own resources. The underlying
`npm run test:browser` expects its local fixture URL/token. No real AI keys are used.
