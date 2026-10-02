# Erro

The `web` folder contains the default [Vite React + TypeScript demo](https://vite.dev/guide/).

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
