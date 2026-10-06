#!/usr/bin/env bash
# Isolated PostgreSQL integration tests: never connect this to a production database.
set -euo pipefail
cd "$(dirname "$0")/.."
run_id="erro-knowledge-test-$$"
cleanup() { docker rm -f "$run_id" >/dev/null 2>&1 || true; docker network rm "$run_id" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# The build stage runs all unit/API tests and contains Maven + the test classes.
docker build --target build -t erro-knowledge-test ./backend
docker network create "$run_id" >/dev/null
# Disposable, isolated test credentials, not project/production credentials.
docker run -d --name "$run_id" --network "$run_id" --network-alias test-db \
  -e POSTGRES_USER=knowledge_test -e POSTGRES_PASSWORD=temporary-test-only \
  -e POSTGRES_DB=erro_knowledge_test "${RAG_TEST_POSTGRES_IMAGE:-postgres:18.6-bookworm}" >/dev/null
for attempt in $(seq 1 60); do
  if docker exec "$run_id" pg_isready -U knowledge_test -d erro_knowledge_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker run --rm --network "$run_id" \
  -e RAG_TEST_DATABASE_URL=jdbc:postgresql://test-db:5432/erro_knowledge_test \
  -e RAG_TEST_DATABASE_USER=knowledge_test -e RAG_TEST_DATABASE_PASSWORD=temporary-test-only \
  erro-knowledge-test ./mvnw --batch-mode --no-transfer-progress verify
