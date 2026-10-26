#!/usr/bin/env bash
# Actual built frontend + Nginx + backend + disposable PostgreSQL; local mock AI only.
set -euo pipefail
cd "$(dirname "$0")/.."
repository_root="$PWD"
pg_bin="${PG_BINDIR:-/usr/lib/postgresql/16/bin}"
nginx_bin="${NGINX_BIN:-nginx}"
mime_file="${NGINX_MIME_FILE:-/etc/nginx/mime.types}"
test_dir="$(mktemp -d /tmp/erro-browser-local-XXXXXX)"
read -r pg_port backend_port provider_port web_port < <(python3 - <<'PY'
import socket
sockets=[socket.socket() for _ in range(4)]
for s in sockets:s.bind(('127.0.0.1',0))
print(*(s.getsockname()[1] for s in sockets))
for s in sockets:s.close()
PY
)
backend_pid='' provider_pid='' nginx_pid=''
cleanup() {
  for pid in "$nginx_pid" "$backend_pid" "$provider_pid"; do if [ -n "$pid" ]; then kill "$pid" >/dev/null 2>&1 || true; fi; done
  "$pg_bin/pg_ctl" -D "$test_dir/data" -m immediate stop >/dev/null 2>&1 || true
  if [ "${ERRO_KEEP_TEST_LOGS:-false}" = true ]; then echo "Local fixture logs: $test_dir"; else rm -rf "$test_dir"; fi
}
trap cleanup EXIT
"$pg_bin/initdb" -D "$test_dir/data" --auth=trust --no-locale --encoding=UTF8 --username=erro_test > "$test_dir/init.log"
"$pg_bin/pg_ctl" -D "$test_dir/data" -l "$test_dir/postgres.log" -o "-h 127.0.0.1 -p $pg_port -k $test_dir" -w start
"$pg_bin/createdb" -h 127.0.0.1 -p "$pg_port" -U erro_test erro_browser_test
FIXTURE_PORT="$provider_port" FIXTURE_HOST=127.0.0.1 node web/test/fixtures/ai-provider.mjs > "$test_dir/provider.log" 2>&1 & provider_pid=$!
export ERRO_TEST_ADMIN_TOKEN=local-browser-fixture-admin-token-only
DATABASE_URL="jdbc:postgresql://127.0.0.1:$pg_port/erro_browser_test" DATABASE_USERNAME=erro_test DATABASE_PASSWORD=local-test-only \
  REDIS_PASSWORD=local-test-only S3_ENABLED=false INFRASTRUCTURE_VERIFY_ON_STARTUP=false \
  AI_ENDPOINT="http://127.0.0.1:$provider_port/responses" AI_API_KEY=local-fixture-only CHAT_COOKIE_SECURE=false \
  KNOWLEDGE_ADMIN_TOKEN="$ERRO_TEST_ADMIN_TOKEN" CHAT_ALLOWED_ORIGINS="http://127.0.0.1:$web_port" PORT="$backend_port" SERVER_ADDRESS=127.0.0.1 \
  "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar backend/target/backend-0.0.1-SNAPSHOT.jar > "$test_dir/backend.log" 2>&1 & backend_pid=$!
python3 - "$test_dir" "$repository_root" "$mime_file" "$web_port" "$backend_port" <<'PY'
from pathlib import Path
import sys
temp,root,mime,web,backend=sys.argv[1:]
main=(Path(root)/'web/nginx/nginx.conf').read_text().replace('worker_processes auto','worker_processes 1')
main=main.replace('/etc/nginx/mime.types',mime).replace('/tmp/nginx-conf/*.conf',temp+'/server.conf')
for name in ('nginx.pid','client_temp','proxy_temp','fastcgi_temp','uwsgi_temp','scgi_temp'):main=main.replace('/tmp/'+name,temp+'/'+name)
(Path(temp)/'nginx.conf').write_text(main)
server=(Path(root)/'web/nginx/default.conf.template').read_text().replace('${PORT}',web).replace('${API_UPSTREAM}','http://127.0.0.1:'+backend).replace('${API_DNS_RESOLVERS}','127.0.0.1')
server=server.replace('/usr/share/nginx/html',root+'/web/dist')
(Path(temp)/'server.conf').write_text(server)
PY
"$nginx_bin" -t -c "$test_dir/nginx.conf"
"$nginx_bin" -c "$test_dir/nginx.conf" -g 'daemon off;' > "$test_dir/nginx.log" 2>&1 & nginx_pid=$!
export ERRO_TEST_URL="http://127.0.0.1:$web_port"
ready=false
for attempt in $(seq 1 90); do
  status=$(curl -s -o /dev/null -w '%{http_code}' "$ERRO_TEST_URL/api/admin/knowledge/documents" || true)
  if [ "$status" = 401 ]; then ready=true; break; fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then tail -50 "$test_dir/backend.log"; exit 1; fi
  sleep 1
done
if [ "$ready" != true ]; then tail -50 "$test_dir/backend.log"; exit 1; fi
cd web
node test/knowledge.browser.mjs
node test/guest-live.browser.mjs
