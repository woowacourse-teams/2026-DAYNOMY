#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
frontend_dir="$(cd "$script_dir/.." && pwd)"
test_dir="$(mktemp -d /tmp/daynomy-deploy-test.XXXXXX)"
chmod 755 "$test_dir"
prefix="daynomy-deploy-test-$RANDOM-$$"
proxy="$prefix-proxy"
image_name="$prefix"
observer_pid=''
slow_pid=''
rollback_slow_pid=''

cleanup() {
  local status=$?
  trap - EXIT
  touch "$test_dir/stop-observer"
  if [ -n "$observer_pid" ]; then wait "$observer_pid" || true; fi
  if [ -n "$slow_pid" ]; then wait "$slow_pid" || true; fi
  if [ -n "$rollback_slow_pid" ]; then wait "$rollback_slow_pid" || true; fi
  if [ "$status" -ne 0 ]; then
    for log in "$test_dir"/*.log; do [ ! -f "$log" ] || cat "$log" >&2; done
    docker logs "$proxy" 2>&1 | tail -30 || true
  fi
  for name in "$prefix" "$prefix-$FRONTEND_BLUE_PORT" "$prefix-$FRONTEND_GREEN_PORT" "$proxy"; do
    docker rm --force "$name" >/dev/null 2>&1 || true
  done
  for port in "$FRONTEND_BLUE_PORT" "$FRONTEND_GREEN_PORT"; do
    [ -n "$port" ] || continue
    FRONTEND_IMAGE="$image_name:one" FRONTEND_CONTAINER_NAME="$prefix-$port" FRONTEND_PORT="$port" \
      docker compose --project-name "$prefix-$port" --file "$frontend_dir/compose.yml" \
      down --timeout 1 >/dev/null 2>&1 || true
  done
  docker image rm "$image_name:one" "$image_name:two" "$image_name:bad-marker" \
    "$image_name:public-bad" "$image_name:unhealthy" "$image_name:four" "$image_name:proxy" >/dev/null 2>&1 || true
  rm -rf "$test_dir"
  exit "$status"
}
# Set before cleanup can run, including failed image builds.
export FRONTEND_BLUE_PORT='' FRONTEND_GREEN_PORT=''
trap cleanup EXIT

read -r FRONTEND_BLUE_PORT FRONTEND_GREEN_PORT proxy_port < <(python3 - <<'PY'
import socket
listeners = [socket.socket() for _ in range(3)]
for listener in listeners:
    listener.bind(('127.0.0.1', 0))
print(*(listener.getsockname()[1] for listener in listeners))
for listener in listeners:
    listener.close()
PY
)
export FRONTEND_BLUE_PORT FRONTEND_GREEN_PORT
export TEST_PROXY="$proxy" TEST_ROOT="$test_dir" TMPDIR="$test_dir"
export COMPOSE_FILE="$frontend_dir/compose.yml" FRONTEND_CONTAINER_PREFIX="$prefix"
export FRONTEND_UPSTREAM_FILE="$test_dir/nginx/frontend.conf" FRONTEND_ASSETS_DIR="$test_dir/assets"
export DEPLOY_ENV=development DEPLOY_URL="http://127.0.0.1:$proxy_port"
export DEPLOY_WAIT_TIMEOUT=10 DEPLOY_DRAIN_TIMEOUT=45 GITHUB_STEP_SUMMARY="$test_dir/summary.md"
mkdir -p "$test_dir"/{assets,bin,nginx,dist/assets}
cp "$frontend_dir/Dockerfile" "$test_dir/Dockerfile"
cp "$frontend_dir/nginx.conf" "$test_dir/nginx.conf"
python3 - "$test_dir" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
config = root / 'nginx.conf'
config.write_text(config.read_text().replace('    location / {',
    '    location = /slow { limit_rate 32k; }\n\n    location / {'))
(root / 'dist/slow').write_bytes(b'S' * 1048576)
PY
cat >> "$test_dir/Dockerfile" <<'EOF'
HEALTHCHECK --interval=1s --timeout=1s --start-period=1s --retries=2 CMD wget --quiet --spider http://127.0.0.1/ || exit 1
EOF

sha_one="$(printf '%040d' 1)"
sha_two="$(printf '%040d' 2)"
sha_three="$(printf '%040d' 3)"
sha_four="$(printf '%040d' 4)"

build_fixture() {
  local tag="$1" version="$2" commit="$3" marker="$4" asset="$5"
  rm -f "$test_dir/dist/assets/"*
  printf 'console.log("%s");\n' "$tag" > "$test_dir/dist/assets/$asset"
  printf '<html><head><meta name="daynomy-deployment" content="development:%s" /><script src="/assets/%s"></script></head><body><div id="root"></div></body></html>\n' \
    "$marker" "$asset" > "$test_dir/dist/index.html"
  docker build --quiet --tag "$image_name:$tag" \
    --label "org.opencontainers.image.version=$version" \
    --label "org.opencontainers.image.revision=$commit" "$test_dir" >/dev/null
}
build_fixture one v1.0.0 "$sha_one" "$sha_one" app-AbCd0001.js
build_fixture two v1.0.1 "$sha_two" "$sha_two" app-AbCd0002.js
build_fixture bad-marker v1.0.2 "$sha_three" "$sha_two" app-AbCd0003.js
build_fixture public-bad v1.0.2 "$sha_three" "$sha_three" app-AbCd0003.js
build_fixture four v1.0.3 "$sha_four" "$sha_four" app-AbCd0004.js
printf '\nCMD ["false"]\n' >> "$test_dir/Dockerfile"
build_fixture unhealthy v1.0.2 "$sha_three" "$sha_three" app-AbCd0003.js

# The host Nginx is real; wrappers only run host control commands inside its Linux container.
cat > "$test_dir/ProxyDockerfile" <<'EOF'
FROM nginx:stable-alpine
RUN apk add --no-cache curl procps python3
EOF
docker build --quiet --file "$test_dir/ProxyDockerfile" --tag "$image_name:proxy" "$test_dir" >/dev/null
sed "s/:3000;/:$FRONTEND_BLUE_PORT;/" "$frontend_dir/host-nginx.upstream.conf" > "$FRONTEND_UPSTREAM_FILE"
cat > "$test_dir/nginx/site.conf" <<EOF
server {
    listen $proxy_port;
    location ~ "^/assets/([^/]+-[A-Za-z0-9_-]{8}\\.[^/]+)$" {
        alias $test_dir/assets/\$1;
        add_header Cache-Control "public, max-age=31536000, immutable";
    }
    location / {
        proxy_pass http://daynomy_frontend;
        add_header X-Daynomy-Frontend \$upstream_addr always;
        proxy_buffering off;
        sub_filter '$sha_three' '$sha_two';
        sub_filter_once off;
    }
}
EOF
cat > "$test_dir/bin/sudo" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = -n ]; then shift; fi
exec "$@"
EOF
cat > "$test_dir/bin/nginx" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = -t ] && [ -f "$TEST_ROOT/fail-nginx-check" ]; then
  rm "$TEST_ROOT/fail-nginx-check"
  echo 'Injected Nginx configuration validation failure' >&2
  exit 1
fi
exec docker exec "$TEST_PROXY" nginx "$@"
EOF
cat > "$test_dir/bin/systemctl" <<'EOF'
#!/usr/bin/env bash
[ "$*" = 'reload nginx' ] || exit 2
exec docker exec "$TEST_PROXY" nginx -s reload
EOF
for command in curl pgrep; do
  cat > "$test_dir/bin/$command" <<EOF
#!/usr/bin/env bash
exec docker exec "$proxy" $command "\$@"
EOF
done
chmod +x "$test_dir/bin/"*
export PATH="$test_dir/bin:$PATH"

# Start the legacy single container; the first migration must leave it serving.
docker run --detach --name "$prefix" --publish "127.0.0.1:$FRONTEND_BLUE_PORT:80" "$image_name:one" >/dev/null
docker exec "$prefix" sed -i "s|<meta name=\"daynomy-deployment\" content=\"development:$sha_one\" />||" /usr/share/nginx/html/index.html
docker cp "$prefix:/usr/share/nginx/html/assets/." "$test_dir/assets/"
docker run --detach --name "$proxy" --network host \
  --volume "$test_dir:$test_dir" \
  --volume "$test_dir/nginx:/etc/nginx/conf.d:ro" "$image_name:proxy" >/dev/null
curl --fail --silent --retry 10 --retry-all-errors --retry-delay 1 "$DEPLOY_URL/" >/dev/null
for ((attempt=0; attempt<20; attempt++)); do
  [ "$(docker inspect --format '{{.State.Health.Status}}' "$prefix")" != healthy ] || break
  sleep 1
done
test "$(docker inspect --format '{{.State.Health.Status}}' "$prefix")" = healthy

cat > "$test_dir/observe.py" <<'PY'
import pathlib
import re
import sys
import time
import urllib.request

root, url = pathlib.Path(sys.argv[1]), sys.argv[2]
count = 0
while not (root / 'stop-observer').exists():
    with urllib.request.urlopen(url + '/', timeout=3) as response:
        html = response.read().decode()
    assert '<div id="root"></div>' in html
    for asset in re.findall(r'src="(/assets/[^"]+)"', html):
        try:
            with urllib.request.urlopen(url + asset, timeout=3) as response:
                assert response.status == 200
        except Exception:
            print(f'Failed asset: {asset}; HTML: {html}; archive: {list((root / "assets").iterdir())}', flush=True)
            raise
    with urllib.request.urlopen(url + '/assets/app-AbCd0001.js', timeout=3) as response:
        assert response.read() == b'console.log("one");\n'
    count += 1
    time.sleep(0.02)
assert count > 0
print(f'Continuous HTML/current assets/old assets: {count} rounds, zero failures')
PY
docker exec "$proxy" python3 "$test_dir/observe.py" "$test_dir" "$DEPLOY_URL" > "$test_dir/observer.log" 2>&1 &
observer_pid=$!

run_deploy() {
  FRONTEND_IMAGE="$image_name:$1" DEPLOY_VERSION="$2" GITHUB_SHA="$3" \
    bash "$script_dir/deploy-frontend.sh"
}
assert_public() {
  curl --fail --silent "$DEPLOY_URL/" | grep -Fq "development:$1"
}
assert_failed() {
  local log="$1"
  shift
  if run_deploy "$@" > "$test_dir/$log.log" 2>&1; then
    echo "Deployment should fail: $log" >&2
    exit 1
  fi
}
for path in / /index.html /news/1 /login /admin/news; do
  curl --fail --silent --head "$DEPLOY_URL$path" | grep -qi '^Cache-Control: no-cache'
done
curl --fail --silent --head "$DEPLOY_URL/assets/app-AbCd0001.js" \
  | grep -qi '^Cache-Control: public, max-age=31536000, immutable'
test "$(curl --silent --output /dev/null --write-out '%{http_code}' "$DEPLOY_URL/assets/missing-AbCd0001.js")" = 404

# Legacy deployments may lack a marker; rollback must match their original HTML.
assert_failed migration public-bad v1.0.2 "$sha_three"
grep -Fq 'Rolled back: previous container retained; deployment remains failed' "$test_dir/migration.log"
docker inspect "$prefix" >/dev/null
if curl --fail --silent "$DEPLOY_URL/" | grep -Fq 'daynomy-deployment'; then
  echo 'Rollback should restore the legacy HTML without a deployment marker' >&2
  exit 1
fi

# Keep a slow request on the old worker while switching to green.
cat > "$test_dir/slow.py" <<'PY'
import pathlib
import sys
import time
import urllib.request
root, url = pathlib.Path(sys.argv[1]), sys.argv[2]
if len(sys.argv) > 4:
    deadline = time.monotonic() + 70
    while True:
        with urllib.request.urlopen(url + '/', timeout=3) as response:
            html = response.read().decode()
        if sys.argv[4] in html:
            break
        assert time.monotonic() < deadline, 'New version was never exposed'
        time.sleep(0.05)
with urllib.request.urlopen(url + '/slow', timeout=50) as response:
    first = response.read(1024)
    (root / sys.argv[3]).touch()
    assert first + response.read() == b'S' * 1048576
print(f'In-flight response completed: {sys.argv[3]}')
PY
docker exec "$proxy" python3 "$test_dir/slow.py" "$test_dir" "$DEPLOY_URL" slow-started > "$test_dir/slow.log" 2>&1 &
slow_pid=$!
for ((attempt=0; attempt<50; attempt++)); do
  [ ! -f "$test_dir/slow-started" ] || break
  sleep 0.1
done
test -f "$test_dir/slow-started"
run_deploy two v1.0.1 "$sha_two"
assert_public "$sha_two"
docker inspect "$prefix" >/dev/null
previous_image="$(docker inspect --format '{{.Image}}' "$prefix-$FRONTEND_GREEN_PORT")"

# A mutable old tag must not affect rollback: keep the running old container itself.
docker tag "$image_name:public-bad" "$image_name:two"
docker exec "$proxy" python3 "$test_dir/slow.py" "$test_dir" "$DEPLOY_URL" \
  rollback-slow-started app-AbCd0003.js > "$test_dir/rollback-slow.log" 2>&1 &
rollback_slow_pid=$!
assert_failed public two v1.0.2 "$sha_three"
assert_public "$sha_two"
test -f "$test_dir/rollback-slow-started"
docker inspect "$prefix-$FRONTEND_BLUE_PORT" >/dev/null
test "$(docker inspect --format '{{.Image}}' "$prefix-$FRONTEND_GREEN_PORT")" = "$previous_image"
grep -Fq 'Rolled back: previous container retained; deployment remains failed' "$test_dir/public.log"
wait "$slow_pid"
slow_pid=''
cat "$test_dir/slow.log"
if docker inspect "$prefix" >/dev/null 2>&1; then
  echo 'The drained legacy container should be retired before slot reuse' >&2
  exit 1
fi

assert_failed labels one v1.0.0 "$sha_four"
assert_public "$sha_two"
assert_failed marker bad-marker v1.0.2 "$sha_three"
assert_public "$sha_two"
wait "$rollback_slow_pid"
rollback_slow_pid=''
cat "$test_dir/rollback-slow.log"
assert_failed health unhealthy v1.0.2 "$sha_three"
assert_public "$sha_two"
touch "$test_dir/fail-nginx-check"
assert_failed nginx four v1.0.3 "$sha_four"
assert_public "$sha_two"
grep -Fq 'Rolled back: previous container retained; deployment remains failed' "$test_dir/nginx.log"

run_deploy four v1.0.3 "$sha_four"
assert_public "$sha_four"
curl --fail --silent "$DEPLOY_URL/assets/app-AbCd0003.js" >/dev/null
# Interrupted reload with identical commits: never replace the publicly active slot.
run_deploy four v1.0.3 "$sha_four"
sed "s/:3000;/:$FRONTEND_BLUE_PORT;/" "$frontend_dir/host-nginx.upstream.conf" > "$FRONTEND_UPSTREAM_FILE"
assert_failed drift four v1.0.3 "$sha_four"
grep -Fq 'verification failed' "$test_dir/drift.log"
docker inspect "$prefix-$FRONTEND_GREEN_PORT" >/dev/null
sed "s/:3000;/:$FRONTEND_GREEN_PORT;/" "$frontend_dir/host-nginx.upstream.conf" > "$FRONTEND_UPSTREAM_FILE"
touch "$test_dir/stop-observer"
wait "$observer_pid"
observer_pid=''
cat "$test_dir/observer.log"

# A failed first deployment has no previous container to restore.
docker rm --force "$prefix-$FRONTEND_BLUE_PORT" "$prefix-$FRONTEND_GREEN_PORT" >/dev/null
sed "s/:3000;/:$FRONTEND_BLUE_PORT;/" "$frontend_dir/host-nginx.upstream.conf" > "$FRONTEND_UPSTREAM_FILE"
nginx -t
systemctl reload nginx
assert_failed initial bad-marker v1.0.2 "$sha_three"
grep -Fq 'no healthy previous frontend; manual recovery required' "$test_dir/initial.log"
run_deploy one v1.0.0 "$sha_one"
assert_public "$sha_one"

echo 'Frontend blue/green deployment, rollback, cache and continuity checks passed'
