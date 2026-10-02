# E.R.R.O.

A project by Eric and Robin, starting with a React + TypeScript chat interface
in `web`. The chat is an early visual preview: message entry, sending,
attachments, and new conversations are disabled. There is no backend or API
integration.

Read [the project context](docs/project-context.md) for the background, possible
future directions, and guidance for AI assistants. The direction is still open;
electrical work, marketing, AI, and a future portfolio are possibilities.

## Run with Docker Compose

From the repository root:

```sh
docker compose up
```

Open <http://localhost:5173> once Vite is ready. The container installs dependencies
from the lockfile on startup. Source files are mounted from `web` for live updates,
and container dependencies are stored in a separate Docker volume.

To stop and remove the container:

```sh
docker compose down
```

## Run locally

With Node.js 24 and npm installed:

```sh
cd web
npm ci
npm run dev
```

## Check the app

From `web`:

```sh
npm run lint
npm run build
```
