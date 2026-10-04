# E.R.R.O. web

React + TypeScript on Vite. This is a minimal AI chat for Eric and Robin's shared
project. Messages go to `POST /api/chat` and replies render as Markdown. The page
keeps its messages in memory only; refresh clears them, and each request sends
only the current message.

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
