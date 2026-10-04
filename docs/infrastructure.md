# Local infrastructure

The backend is a Spring Boot application with JDBC, Redis, and an AWS SDK S3
client. It also serves the stateless AI [chat API](chat.md), using Spring MVC and
Tomcat on port 8080. Chat does not store data in the infrastructure services.

## Where things live

| File or directory | Purpose |
| --- | --- |
| `compose.yaml` | Starts Nginx web, backend, PostgreSQL, Redis, and MinIO; provides optional Vite through the `dev` profile. Configures credentials, ports, health checks, and persistent volumes. |
| `web/Dockerfile`, `web/nginx/` | Build and serve React with an Nginx reverse proxy to the backend; see [Web proxy](web-proxy.md). |
| `.env.example` | Available local Compose overrides and development defaults; `.env` holds ignored machine-specific overrides. |
| `backend/pom.xml` | Spring Boot, Java, and AWS SDK versions and backend dependencies. |
| `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/` | Maven Wrapper for Unix and Windows, with the pinned Maven distribution and checksum. |
| `backend/src/main/resources/application.yaml` | Database, Redis, S3, and startup settings. |
| `backend/src/main/java/ink/erro/backend/` | Application entry point and infrastructure configuration: S3 client, storage properties, and startup connection checks. |
| `backend/src/main/java/ink/erro/backend/ai/` and `chat/` | Configurable AI service, chat endpoint, and safe API error responses; documented in [Chat](chat.md). |
| `backend/src/test/java/ink/erro/backend/chat/` | API tests against a local mock provider, without real credentials. |
| `backend/Dockerfile` | Maven build on JDK 25, followed by a JRE 25 runtime under an unprivileged user. |
| `backend/.dockerignore` | Limits the backend build context to Maven configuration, the wrapper, and sources. |
| `infra/minio/Dockerfile` | Builds the pinned MinIO source and runs it under an unprivileged user on Alpine 3.24.2. |
| `.gitignore` | Excludes local environment files, backend build output, and IDE files; keeps `.env.example` trackable. |

## Versions

Selected on 2026-10-03 from stable releases, excluding milestones, release
candidates, and non-LTS Java versions. Spring Boot manages the JDBC driver,
Lettuce, and other Spring dependency versions.

| Component | Pinned version | Official source |
| --- | --- | --- |
| Spring Boot | 4.1.1 | [Spring Boot](https://spring.io/projects/spring-boot) |
| Java | 25 LTS, Temurin 25.0.4+7 | [Temurin images](https://hub.docker.com/_/eclipse-temurin) |
| Maven | 3.10.0 via Maven Wrapper | [Maven downloads](https://maven.apache.org/download.cgi) |
| PostgreSQL | 18.6 | [PostgreSQL releases](https://www.postgresql.org/docs/current/release.html) |
| Redis | 8.10.1 | [Redis 8.10 release notes](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/release-notes/redisce/redisos-8.10-release-notes/) |
| Nginx | 1.30.5, stable unprivileged Alpine image pinned by digest | [NGINX unprivileged images](https://github.com/nginx/docker-nginx-unprivileged) |
| MinIO community | RELEASE.2025-10-15T17-29-55Z | [Final community release](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z) |
| AWS SDK for Java | 2.55.11 | [Published SDK versions](https://repo.maven.apache.org/maven2/software/amazon/awssdk/bom/maven-metadata.xml) |

MinIO's community repository is archived. Eric explicitly chose its final
community release for local development, with Amazon S3 planned later.
`infra/minio/Dockerfile` builds that source release with Go 1.27.1 and verifies
the source archive's SHA-256 checksum. The runtime includes its upstream AGPL
license. This is a local development service, not the intended production store.

## Start and stop

From the repository root, with Docker and Docker Compose v2 installed:

```sh
# Optional, only if .env does not already exist; then edit it as needed.
cp .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs -f backend
```

Infrastructure starts without a `.env` file. AI replies require `AI_API_KEY` in
backend configuration; see [Chat](chat.md). `.env` is ignored by Git.
The first build downloads Maven dependencies and compiles MinIO, so allow a few
minutes. Subsequent builds use Docker and dependency caches. No host Java or
Maven installation is required for the Compose workflow.

The backend waits for healthy PostgreSQL, Redis, and MinIO containers, then
verifies a JDBC connection, Redis `PING`, and authenticated S3 `ListBuckets`.
Look for `Infrastructure ready` in its logs. A failed connection aborts startup.
These checks do not create application tables, Redis keys, buckets, or objects.
The embedded HTTP server keeps the backend running afterward.
The checks happen once at startup, not as continuous monitoring.

Set `S3_ENABLED=false` when deploying without object storage. This omits the S3
client and its check, while PostgreSQL and Redis are still verified. Local
Compose defaults to `true`; changing this flag does not remove the MinIO service
or its Compose startup dependency. See [Railway setup](railway.md) for the
deployment with only PostgreSQL and Redis.

```sh
# Rebuild the backend after Java or Maven changes.
docker compose up -d --build backend

# Stop the stack, preserving its named data volumes.
docker compose down

# Run just the production web server (chat also needs backend).
docker compose up web

# Optional Vite live reload at localhost:5174, alongside the default web service.
docker compose --profile dev up -d web-dev
```

PostgreSQL data, Redis append-only persistence, and MinIO data each have a named
volume. PostgreSQL 18 uses `/var/lib/postgresql` as the mount target, following
the [official image's PostgreSQL 18 layout](https://hub.docker.com/_/postgres).
Removing volumes deletes their data. PostgreSQL initialization credentials apply
only when its volume is first created; editing `.env` does not change an existing
database user's password.

## Local access

| Service | Host address | Default local credentials |
| --- | --- | --- |
| Nginx website and chat proxy | http://localhost:5173 | None |
| Vite frontend (optional `dev` profile) | http://localhost:5174 | None |
| Backend | http://localhost:8080/api/chat (POST) | AI key configured on the server; no visitor login |
| PostgreSQL | localhost:5432, database `erro` | `erro` / `erro-local-postgres` |
| Redis | localhost:6379 | Password `erro-local-redis` |
| MinIO S3 endpoint | http://localhost:9000 | `erro-local` / `erro-local-minio-secret` |
| MinIO console | http://localhost:9001 | Same as MinIO S3 |

Infrastructure ports bind to `127.0.0.1`. Compose uses internal service names
(`postgres`, `redis`, `minio`) and internal ports for backend connections; host
port overrides do not change these. These credentials are development defaults.
If a host port is already occupied, set a different one in `.env` (for example,
`POSTGRES_PORT=5433`). Use that port in database tools and host Java configuration.

On Eric's machine, the initial setup on 2026-10-03 found port 5432 already occupied
and created an ignored `.env` containing `POSTGRES_PORT=5433`. PostgreSQL is
therefore available at `localhost:5433` there. The shared Compose default remains
5432, and the backend still connects internally to `postgres:5432`. Preserve this
override when editing the existing `.env`.

## Configuration reference

Compose reads the root `.env` for variable interpolation. Spring reads its own
process environment. The Compose file maps the local service credentials into
the backend's environment; the two sets of names are deliberately separate.

| Compose variable | Purpose and default |
| --- | --- |
| `WEB_PORT`, `VITE_PORT` | Published host ports for Nginx and optional Vite, defaults 5173 and 5174. Nginx always listens on 8080 inside its container. |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database name, user, and password; also mapped to backend `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Defaults are listed above. |
| `POSTGRES_PORT` | Published host port, default 5432; the internal database port stays 5432. |
| `REDIS_PASSWORD` | Redis password shared with the backend, default `erro-local-redis`. |
| `REDIS_PORT` | Published host port, default 6379; Compose always gives the backend internal port 6379. |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | Local MinIO credentials, mapped to backend `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`. Defaults are listed above. |
| `MINIO_PORT`, `MINIO_CONSOLE_PORT` | Published S3 and console ports, default 9000 and 9001. |
| `S3_ENABLED` | Passed to the backend, default `true`; set `false` to omit the S3 client and its startup check. |
| `AWS_REGION` | Region used by both MinIO and the backend, default `us-east-1`. |
| `BACKEND_PORT` | Published backend host port, default 8080. |

Compose also passes `AI_ENDPOINT`, `AI_API_KEY`, `AI_MODEL`, `AI_REASONING_EFFORT`,
`AI_MAX_OUTPUT_TOKENS`, `AI_TIMEOUT`, and `CHAT_ALLOWED_ORIGINS` to the backend.
See the complete [chat configuration reference](chat.md#configuration).

Backend-only settings below must be supplied to the Java process or added to
the backend's Compose `environment` section. Putting an arbitrary backend setting
in the root `.env` does not automatically pass it into the container.

| Backend variable | Behavior |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | JDBC URL, username, and password. Host defaults are `jdbc:postgresql://localhost:5432/erro` and `erro`; a password must be supplied. |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection settings. Host defaults are `localhost` and 6379; a password must be supplied. |
| `SPRING_DATA_REDIS_USERNAME` | Optional standard Spring Boot setting for a Redis ACL username. Railway can reference its template's `REDISUSER`; local Compose uses password-only authentication for the default user. |
| `S3_ENABLED` | Defaults to `true`. Set `false` to omit the S3 client and skip only its connection check; AWS credentials are then unnecessary. |
| `S3_ENDPOINT` | Optional endpoint override. Unset or empty uses Amazon S3; local Compose sets `http://minio:9000`. |
| `S3_PATH_STYLE_ACCESS` | Defaults to `false`; local Compose sets `true` for MinIO. |
| `AWS_REGION` | S3 region, default `us-east-1`. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Local Compose supplies MinIO credentials. Other deployments use the AWS default credential chain. |
| `AWS_EC2_METADATA_DISABLED` | Local Compose sets `true` to disable EC2 credential lookup. |
| `INFRASTRUCTURE_VERIFY_ON_STARTUP` | Defaults to `true`. Setting `false` disables all startup checks, while retaining enabled clients. Leave it `true` when disabling just S3. |

JDBC uses a Hikari pool capped at five connections with a 10-second acquisition
timeout. Redis connection and command timeouts are five seconds. The S3 client
uses the URL connection HTTP client, a five-second connection timeout, a
10-second socket and attempt timeout, and a 30-second total call timeout. SQL
initialization and Redis repository discovery are disabled; there is no schema
or repository layer yet.

## Maven and host development

With a JDK 25 installed (and `unzip` on Linux/macOS), use the checked-in wrapper
from `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

On Windows, use `mvnw.cmd`. The wrapper pins Maven and verifies its distribution
checksum. Maven `verify` runs the chat API tests against a local mock provider.
Verify infrastructure connectivity separately by running the Compose stack and
checking the startup result.

To run Java from an IDE or the host, start the dependencies:

```sh
docker compose up -d postgres redis minio
```

Configure these environment variables in the IDE or shell, adjusting them to
match `.env` if customized. Compose's `.env` is not loaded automatically by Spring.

```sh
export DATABASE_URL=jdbc:postgresql://localhost:5432/erro
export DATABASE_USERNAME=erro
export DATABASE_PASSWORD=erro-local-postgres
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=erro-local-redis
export S3_ENDPOINT=http://localhost:9000
export S3_PATH_STYLE_ACCESS=true
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=erro-local
export AWS_SECRET_ACCESS_KEY=erro-local-minio-secret
export AWS_EC2_METADATA_DISABLED=true
cd backend
./mvnw spring-boot:run
```

## Switching to Amazon S3 later

The backend uses the standard AWS `S3Client`. For a future AWS deployment:

1. Set `S3_ENABLED=true` and leave `S3_ENDPOINT` unset or empty so the SDK uses
   Amazon's regional endpoint.
2. Set `S3_PATH_STYLE_ACCESS=false` and `AWS_REGION` to the intended AWS region.
3. Remove the MinIO credentials and provide AWS credentials through the
   [default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html),
   such as a workload IAM role or a local AWS profile. Remove
   `AWS_EC2_METADATA_DISABLED` if using an EC2 instance role.
4. The current read-only startup check requires `s3:ListAllMyBuckets`. If a future
   IAM policy limits access to one bucket, replace that check with a suitable
   bucket check or set `INFRASTRUCTURE_VERIFY_ON_STARTUP=false` to disable the
   startup connection checks.

Compose explicitly supplies the local MinIO endpoint and credentials; change
those backend environment entries and its MinIO dependency if adapting this
Compose file for AWS. No bucket is selected or created yet, and changing the
endpoint does not migrate existing data. The AWS SDK supports custom endpoints
through [endpoint configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/endpoint-config.html).

## Scope and deployment

This Compose stack is for local development. Its default web service now builds
the same Nginx image intended for Railway; an optional Vite service keeps live
reload available. Railway should build `web/Dockerfile` with root directory
`/web`. This setup does not deploy cloud resources or change the website's domain.
See [Railway setup](railway.md) for adding the backend, PostgreSQL 18, and Redis,
all variables, and connecting the existing frontend. The backend Dockerfile uses
normal Docker layers to cache dependencies, avoiding service-specific BuildKit
cache IDs required by Railway. It runs Maven `verify` during the build and starts
the Java application directly; no pre-deploy command is needed.

## Initial verification — 2026-10-03

The following checks passed during the infrastructure-only implementation,
before the chat API was added:

- Compose configuration validation and both custom Docker image builds. The
  backend build ran Maven `verify` with Java 25; no application tests existed then.
- The full Compose stack started, with PostgreSQL, Redis, and MinIO health checks
  passing and the backend logging all three successful connection checks.
- PostgreSQL reported version 18.6 and zero tables in the `public` schema; Redis
  reported zero keys. MinIO reported the pinned community release.
- A separate backend run with an invalid S3 secret failed startup with an S3 403,
  confirming that the check verifies authenticated access.
- A separate run with an empty S3 endpoint, path-style access disabled, and
  startup checks disabled successfully initialized and exited. This verifies
  AWS endpoint configuration only; no real Amazon S3 connection was tested.
- The Vite server responded locally, and the frontend source files were
  unchanged. The backend published no HTTP port.

These are dated setup results, not a guarantee of current service health. After
future infrastructure changes, rerun the Compose build and startup checks and
record any new verification results or limitations.

## Nginx verification — 2026-10-04

The web image build and Compose configuration (including the optional `dev`
profile) passed. The default stack started with Nginx serving the website and
forwarding to Spring Boot. Backend startup again verified PostgreSQL, Redis,
and MinIO. The optional Vite proxy also reached the backend successfully.
Six production-image proxy tests and a browser flow with the real backend and
a mock AI provider passed; see [Web proxy verification](web-proxy.md#results--2026-10-04).
