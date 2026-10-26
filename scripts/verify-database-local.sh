#!/usr/bin/env bash
# Disposable native PostgreSQL test cluster; never connects to an existing database.
set -euo pipefail
cd "$(dirname "$0")/.."
pg_bin="${PG_BINDIR:-/usr/lib/postgresql/16/bin}"
test_dir="$(mktemp -d /tmp/erro-db-test-XXXXXX)"
test_port="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
cleanup() {
  "$pg_bin/pg_ctl" -D "$test_dir/data" -m immediate stop >/dev/null 2>&1 || true
  rm -rf "$test_dir"
}
trap cleanup EXIT
"$pg_bin/initdb" -D "$test_dir/data" --auth=trust --no-locale --encoding=UTF8 --username=erro_test > "$test_dir/init.log"
"$pg_bin/pg_ctl" -D "$test_dir/data" -l "$test_dir/server.log" -o "-h 127.0.0.1 -p $test_port -k $test_dir" -w start
"$pg_bin/createdb" -h 127.0.0.1 -p "$test_port" -U erro_test erro_knowledge_test
export RAG_TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:$test_port/erro_knowledge_test"
export RAG_TEST_DATABASE_USER=erro_test RAG_TEST_DATABASE_PASSWORD=local-test-only
"$pg_bin/postgres" --version
cd backend
./mvnw --batch-mode --no-transfer-progress verify
