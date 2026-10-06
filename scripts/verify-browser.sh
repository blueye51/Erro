#!/usr/bin/env bash
# Isolated end-to-end fixture using the actual Nginx/backend images and a mock AI.
set -euo pipefail
cd "$(dirname "$0")/.."
repository_root="$PWD"
run_id="erro-browser-test-$$"
override=$(mktemp)
cleanup() {
  cd "$repository_root"
  docker compose --env-file /dev/null -p "$run_id" -f compose.yaml -f "$override" down -v >/dev/null 2>&1 || true
  rm -f "$override"
}
trap cleanup EXIT
export WEB_PORT=0 BACKEND_PORT=0 POSTGRES_PORT=0 REDIS_PORT=0 MINIO_PORT=0 MINIO_CONSOLE_PORT=0
export KNOWLEDGE_ADMIN_TOKEN=local-browser-fixture-admin-token-only
cat > "$override" <<YAML
services:
  web:
    image: erro-web
  backend:
    image: erro-backend
    environment:
      AI_ENDPOINT: http://mock-provider:3000/responses
      AI_API_KEY: fixture-only-key
      EMBEDDING_ENDPOINT: ''
      EMBEDDING_MODEL: ''
  minio:
    image: erro-minio
  mock-provider:
    image: node:24-alpine
    command: [node, /fixture/ai-provider.mjs]
    volumes:
      - "$PWD/web/test/fixtures:/fixture:ro"
YAML
docker compose build web backend minio
docker compose --env-file /dev/null -p "$run_id" -f compose.yaml -f "$override" up -d --no-build --wait
address=$(docker compose --env-file /dev/null -p "$run_id" -f compose.yaml -f "$override" port web 8080)
export ERRO_TEST_URL="http://$address" ERRO_TEST_ADMIN_TOKEN="$KNOWLEDGE_ADMIN_TOKEN"
# Compose waits for infrastructure; the backend also needs time for migration/startup.
for attempt in $(seq 1 60); do
  status=$(curl -s -o /dev/null -w '%{http_code}' "$ERRO_TEST_URL/api/admin/knowledge/documents")
  if [ "$status" = 401 ]; then break; fi
  sleep 1
done
docker compose --env-file /dev/null -p "$run_id" -f compose.yaml -f "$override" logs backend
cd web
node test/knowledge.browser.mjs
