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

Vite proxies `/api` to `http://localhost:8080` by default; Compose sets
`BACKEND_PROXY_TARGET=http://backend:8080`. For a separately hosted backend, set
`VITE_API_BASE_URL` to its HTTPS origin before building the frontend, and allow
the frontend origin through backend `CHAT_ALLOWED_ORIGINS`. `.env.example` shows
the frontend settings. API keys must never use a `VITE_` prefix or enter `web/`.
