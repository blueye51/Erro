# Railway setup

Use the existing Railway project and the same environment as `web`. The target
is four services: `web`, `backend`, `Postgres`, and `Redis`. These names are used
exactly in the variable references below; adjust references if services have
different names. MinIO stays local. Amazon S3 can be connected later.

This guide records the required deployment configuration. No Railway resources
have been created or inspected from this workspace. The dashboard labels below
follow Railway's documentation checked on 2026-10-04.

```mermaid
flowchart LR
    B[Visitor's browser] -->|HTTPS website and /api/chat| W[web: erro.ink, Nginx]
    W -->|private HTTP :8080| A[backend: Spring Boot]
    A -->|private connection| P[Postgres]
    A -->|private connection| R[Redis]
    A -->|HTTPS with server API key| AI[AI provider]
```

The browser calls `/api/chat` on `erro.ink`. Nginx forwards it to the backend's
private address. Only web needs a public HTTPS domain. Database connections also
use Railway's private network. Chat does not write to PostgreSQL or Redis; both
are connected and checked for future work. See [Web proxy](web-proxy.md) for the
implementation, matching the Nginx layout used in Eric's wiki.

## 1. Put the code on the deployment branch

Commit the backend, chat frontend, and configuration changes, then merge/push
them to GitHub `main`. The existing web service deploys from `main`; the new
backend service must use the same branch. Do not commit `.env` or real API keys.

Keep root `compose.yaml` for local development. This guide adds services in the
Railway dashboard so the existing web service and domain remain in place.

## 2. Add PostgreSQL and Redis

In the existing project/environment, use **+ New → Database → Add PostgreSQL**
and repeat for **Redis**. Name the services `Postgres` and `Redis`. Deploy staged
changes and wait for both to start. Keep their generated credentials, startup
commands, and persistent volumes. Select the same region as the backend.

PostgreSQL must be major **18**, matching the project's chosen major. Check the
running version in its database view or logs; do not assume `latest` means 18.
If the template starts on 16 or 17, use **Database → Config → Major Version
Upgrade**, choose 18, review preflight, and run the upgrade. This requires the
official `ghcr.io/railwayapp-templates/postgres-ssl` image and a pinned source
major. If its source tag is `latest`, first change that tag to the **currently
running major**, deploy, and then use the upgrade flow. Do not jump an initialized
data volume to a different major by editing its image tag. See Railway's
[PostgreSQL major upgrade instructions](https://docs.railway.com/databases/postgresql-major-upgrade).

For the new Redis service, use the project's tested image
`redis:8.10.1-alpine` in its source image settings, retaining the template's
password-aware start command and `/data` volume. Inspect the resolved start
command to ensure password authentication remains enabled. This is a new empty
service setup, not an instruction to downgrade an existing database.

The templates supply database credentials and connection variables. Nothing
from local `.env` needs copying onto these services. Public database domains and
TCP proxies are unnecessary for backend access. Railway documents the provided
variables in its [PostgreSQL](https://docs.railway.com/databases/postgresql) and
[Redis](https://docs.railway.com/databases/redis) guides. Services must share a
project and environment for [private networking](https://docs.railway.com/networking/private-networking).

## 3. Add the backend service

Add an empty service, name it `backend`, and configure it before deploying. Under
**Settings**, connect the GitHub repository `blueye51/Erro`, select branch `main`,
and set these values:

| Setting | Value |
| --- | --- |
| Root Directory | `/backend` |
| Builder | Detected Dockerfile (`backend/Dockerfile` in the repository) |
| Dockerfile Path, if selected manually | `Dockerfile`, relative to root directory `/backend`. |
| Build Command override | Empty; the Dockerfile runs Maven `verify`. |
| Start Command override | Empty; the Dockerfile starts the Java application. |
| Pre-deploy Command | Empty; there are no migrations. |
| Healthcheck Path | Empty; no GET health endpoint exists. |

An automatic deployment triggered before configuration is complete may fail;
apply all settings and variables, then deploy again. The root directory makes
the Docker build context `backend/`; do not use the repository root as its context.
See Railway's [monorepo](https://docs.railway.com/deployments/monorepo) and
[Dockerfile](https://docs.railway.com/builds/dockerfiles) instructions.

The Dockerfile uses ordinary dependency-layer caching. It does not require
Railway-specific BuildKit cache mount IDs or AI/database credentials at build
time. Do not add build arguments containing secrets.

## 4. Configure backend variables

In **backend → Variables → Raw Editor**, merge these settings into the existing
variables. The `${{...}}` expressions are Railway references, not values to
manually expand. Paste this in the dashboard, not into a shell or local `.env`.

```dotenv
PORT=8080
DATABASE_URL=jdbc:postgresql://${{Postgres.RAILWAY_PRIVATE_DOMAIN}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}?sslmode=require
DATABASE_USERNAME=${{Postgres.PGUSER}}
DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}
REDIS_HOST=${{Redis.RAILWAY_PRIVATE_DOMAIN}}
REDIS_PORT=${{Redis.REDISPORT}}
REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
SPRING_DATA_REDIS_USERNAME=${{Redis.REDISUSER}}
S3_ENABLED=false
INFRASTRUCTURE_VERIFY_ON_STARTUP=true
CHAT_ALLOWED_ORIGINS=https://erro.ink
AI_ENDPOINT=https://api.deepseek.com/responses
AI_MODEL=deepseek-flash
AI_REASONING_EFFORT=none
AI_MAX_OUTPUT_TOKENS=2048
AI_TIMEOUT=60s
```

Add `AI_API_KEY` separately with your real **DeepSeek** API key. It belongs on
`backend` only. Without it, the backend still starts but chat returns
`AI chat is not configured yet.` The model must be available to that account.
DeepSeek supports the Responses API schema this adapter uses. Its full endpoint
here is `/responses`, not `/chat/completions`. If you already entered OpenAI
values, replace the endpoint, model, and key together. `AI_REASONING_EFFORT=none`
disables thinking for this basic chat after the updated backend is deployed.
Apply the variables and redeploy backend; no frontend change is needed when only
the AI provider changes. See [DeepSeek setup](chat.md#deepseek-setup).

The JDBC prefix is required by this Java application. Railway's generated
`DATABASE_URL` is usually a `postgresql://` URL and must not be copied directly
into this backend's `DATABASE_URL`. Separate username/password references avoid
embedding credentials in the URL. `sslmode=require` uses TLS with Railway's
official SSL-enabled PostgreSQL image; it does not validate the server
certificate. The connection also stays on Railway's private network. See the
[PostgreSQL JDBC connection format](https://jdbc.postgresql.org/documentation/use/).

`SPRING_DATA_REDIS_USERNAME` is Spring Boot's standard Redis username property;
the default Railway Redis user is supplied by the template's `REDISUSER`.
`S3_ENABLED=false` prevents S3 client creation and skips only the S3 connection
check. No `AWS_*` or MinIO credentials are needed. PostgreSQL and Redis checks
remain enabled; a failed connection stops startup.

If visitors also use the generated web domain or `www.erro.ink`, append their
exact HTTPS origins, separated by commas, to `CHAT_ALLOWED_ORIGINS`. For example:

```dotenv
CHAT_ALLOWED_ORIGINS=https://erro.ink,https://web-example.up.railway.app
```

Replace the example with the actual **web** domain. Origins contain no path or
trailing slash. Only include `www.erro.ink` if that domain is configured and used.
Apply/deploy staged variable changes; editing a variable alone does not update
an existing container. See [Railway variable references](https://docs.railway.com/variables).

## 5. Deploy the private backend

Wait until PostgreSQL and Redis are ready, then deploy `backend`. In its runtime
logs, confirm all of:

```text
PostgreSQL connection verified
Redis connection verified
S3 storage disabled; skipping its connection check
Infrastructure ready
```

Keep backend `PORT=8080`. Its private address is available through
`${{backend.RAILWAY_PRIVATE_DOMAIN}}` in the same project/environment. It does
not need a generated public domain; a "No HTTP domain" notice on this backend
is expected. Web will receive public requests and forward them privately.

If a backend public domain already exists from the earlier setup, keep it until
the new web proxy has been deployed and verified, then remove that domain.
No Namecheap change is needed; `erro.ink` continues pointing at `web`.

The backend has no home page. Opening its root in a browser may return 404, and
opening `/api/chat` sends GET and returns 405. Neither tests the POST chat flow.
Do not use `/` or `/api/chat` as a Railway GET healthcheck path.

## 6. Deploy Nginx on the existing web service

Keep the existing **web** service and its `erro.ink` domain, but change its build
from Railpack to the new Dockerfile:

| Setting | Value |
| --- | --- |
| Repository / branch | `blueye51/Erro`, `main` |
| Root Directory | `/web` |
| Builder | Dockerfile |
| Dockerfile Path | `Dockerfile` |
| Build Command override | Empty; remove the old `npm run build` override. The Dockerfile runs it. |
| Start Command override | Empty; the image starts Nginx. |
| Pre-deploy Command | Empty |
| Healthcheck Path | `/healthz` |
| Public domain target port | `8080` |

In **web → Variables**, set:

```dotenv
PORT=8080
API_UPSTREAM=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080
```

Use `http://` for the private upstream, include backend's port, and do not append
`/api/chat` or a trailing slash. Nginx reads this server-only variable at startup;
the browser never uses or receives the private address. Railway's private network
encrypts inter-service traffic. [Private networking details](https://docs.railway.com/networking/private-networking/how-it-works).

Delete **`VITE_API_BASE_URL`** from web and deploy the new image. The client always
calls `/api/chat` on its own origin. `BACKEND_PROXY_TARGET` is used only by the
local Vite development server. No AI key or database credentials belong on web.
After this first migration, changing `API_UPSTREAM` requires a web redeploy to
load the new runtime setting; the JavaScript does not need rebuilding for it.

Nginx uses the container's private DNS and refreshes cached backend addresses,
so no resolver variable or extra proxy service is needed. The web-to-backend
connection stays private and avoids inter-service public egress. Replies to
visitors and requests to DeepSeek still use public networking. See
[Railway's egress billing guide](https://docs.railway.com/pricing/understanding-your-bill).

## 7. Verify the deployed chat

First open `https://erro.ink/healthz` and confirm `ok`. Then open `https://erro.ink`,
send a short message, and confirm a formatted reply. In browser developer tools,
Network should show a POST to **`https://erro.ink/api/chat`**, returning 200 with
a JSON `reply`. Refreshing should clear the page's messages. No chat tables,
Redis keys, or saved conversations are created.

To test through the same public web proxy:

```sh
curl -i 'https://erro.ink/api/chat' \
  -H 'Content-Type: application/json' \
  -H 'Origin: https://erro.ink' \
  --data '{"message":"Say hello in one sentence."}'
```

This uses the backend's provider account and makes a real AI request if a key is
configured. No provider key is sent from the browser or curl command. The current
API has no login or application rate limiter, so public requests use that
account; CORS is not authentication.

After these checks pass, remove any public domain on the backend service and
verify chat again. PostgreSQL, Redis, and backend can then remain private.

| Symptom | Check |
| --- | --- |
| Backend exits before `Infrastructure ready` | Check runtime logs, service names in references, ready databases, and `S3_ENABLED=false`. Redeploy after dependencies are available. |
| JDBC URL error | `DATABASE_URL` must start with `jdbc:postgresql://`; do not use the template URL unchanged. |
| Web does not load | Confirm the new web Dockerfile is deployed, custom start/build overrides are empty, and the web domain targets `PORT=8080`. |
| Nginx 502 / 504 on `/api/chat` | Check backend runtime logs, private service hostname, matching port 8080, and that both services are in the same project/environment. |
| Browser CORS rejection | Add the exact page origin to backend `CHAT_ALLOWED_ORIGINS`, then redeploy backend. |
| Browser still calls the old backend public URL | Deploy the new web image and reload the page; an old frontend bundle is still being served or open. |
| `/api/chat` returns the website HTML | The old static host may still be deployed. Confirm web runs the Nginx Dockerfile and proxy configuration. |
| `AI chat is not configured yet.` | Add `AI_API_KEY` on backend and deploy the variable change. |
| Provider rejected / unavailable | Check backend key, account access, configured model, and provider limits. See [chat errors](chat.md#api-contract). |
| `/healthz` works but chat fails | The web healthcheck tests Nginx only; check backend connectivity, provider configuration, and the returned chat error. |

## Earlier infrastructure verification

Verified on 2026-10-04:

- `docker compose config --quiet` passed, and the backend Docker image built
  successfully with all 17 Maven tests passing.
- A separate backend process connected to the real Compose PostgreSQL and Redis
  services with `S3_ENABLED=false` and empty AWS credentials. It logged both
  successful connection checks, skipped S3, and exited successfully after the
  checks in non-web mode. This also verified the Redis `default` ACL username.
- A second process with S3 disabled and an intentionally incorrect Redis
  password exited with failure, confirming Redis authentication is still checked.
- The normal Compose backend was recreated with S3 enabled. PostgreSQL, Redis,
  and MinIO checks all passed, and the HTTP chat endpoint returned the expected
  400 validation response for an empty message without calling an AI provider.

Railway deployment and a live AI provider call require the user's Railway
project and provider credentials; neither is available to this workspace.

## Nginx migration verification — 2026-10-04

The web Docker build, frontend lint/build, Compose validation, and six proxy
regression tests passed. Browser checks exercised the production Nginx image
with the real Spring Boot backend and a mock AI provider, including errors,
retries, Markdown, mobile layout, and clearing messages on refresh. Backend
startup checks again verified PostgreSQL, Redis, and local MinIO. See
[Web proxy verification](web-proxy.md#results--2026-10-04) for results and limits.
The actual Railway migration still needs the dashboard steps above.
