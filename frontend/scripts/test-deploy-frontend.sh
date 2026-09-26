#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
frontend_dir="$(cd "$script_dir/.." && pwd)"
test_dir="$(mktemp -d)"
export COMPOSE_PROJECT_NAME="daynomy-deploy-test-$RANDOM-$$"
image_name="$COMPOSE_PROJECT_NAME"
image_ids=()

cleanup() {
  local status=$?
  set +u
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    for log in "$test_dir"/*.log; do
      [ ! -f "$log" ] || cat "$log" >&2
    done
  fi
  docker compose --file "$test_dir/compose.yml" down --timeout 1 >/dev/null 2>&1 || true
  for image_id in "${image_ids[@]}"; do
    docker image rm "daynomy-frontend-rollback:${image_id#sha256:}" >/dev/null 2>&1 || true
  done
  docker image rm "$image_name:one" "$image_name:two" "$image_name:bad-marker" \
    "$image_name:unhealthy" "$image_name:moved-tag" >/dev/null 2>&1 || true
  rm -rf "$test_dir"
  exit "$status"
}
trap cleanup EXIT

port="$(python3 - <<'PY'
import socket
with socket.socket() as listener:
    listener.bind(('127.0.0.1', 0))
    print(listener.getsockname()[1])
PY
)"
export COMPOSE_FILE="$test_dir/compose.yml"
export FRONTEND_IMAGE="$image_name:one"
export DEPLOY_ENV=development DEPLOY_URL="http://127.0.0.1:$port"
export DEPLOY_WAIT_TIMEOUT=10 GITHUB_STEP_SUMMARY="$test_dir/summary.md"

cat > "$COMPOSE_FILE" <<EOF
services:
  frontend:
    image: \${FRONTEND_IMAGE}
    ports:
      - '127.0.0.1:$port:80'
EOF

cp "$frontend_dir/Dockerfile" "$test_dir/Dockerfile"
cp "$frontend_dir/nginx.conf" "$test_dir/nginx.conf"
cat >> "$test_dir/Dockerfile" <<'EOF'
HEALTHCHECK --interval=1s --timeout=1s --start-period=1s --retries=2 CMD wget --quiet --spider http://127.0.0.1/ || exit 1
EOF
mkdir -p "$test_dir/dist/assets"
printf 'console.log("fixture");\n' > "$test_dir/dist/assets/app-AbCd0123.js"
printf '<svg/>\n' > "$test_dir/dist/icon.svg"
printf '<svg/>\n' > "$test_dir/dist/assets/plain.svg"

sha_one="$(printf '%040d' 1)"
sha_two="$(printf '%040d' 2)"
sha_three="$(printf '%040d' 3)"

build_fixture() {
  local tag="$1" version="$2" commit="$3" marker="$4"
  printf '<html><head><meta name="daynomy-deployment" content="development:%s" /><script src="/assets/app-AbCd0123.js"></script></head><body><div id="root"></div></body></html>\n' \
    "$marker" > "$test_dir/dist/index.html"
  docker build --quiet --tag "$image_name:$tag" \
    --label "org.opencontainers.image.version=$version" \
    --label "org.opencontainers.image.revision=$commit" \
    --label "org.opencontainers.image.source=$COMPOSE_PROJECT_NAME" "$test_dir" >/dev/null
  image_ids+=("$(docker image inspect --format '{{.Id}}' "$image_name:$tag")")
}

run_deploy() {
  FRONTEND_IMAGE="$image_name:$1" DEPLOY_VERSION="$2" GITHUB_SHA="$3" \
    bash "$script_dir/deploy-frontend.sh"
}

running_image() {
  docker inspect --format '{{.Image}}' "$(docker compose --file "$COMPOSE_FILE" ps --quiet frontend)"
}

build_fixture one v1.0.0 "$sha_one" "$sha_one"
build_fixture two v1.0.1 "$sha_two" "$sha_two"
build_fixture bad-marker v1.0.2 "$sha_three" "$sha_two"
printf '\nCMD ["false"]\n' >> "$test_dir/Dockerfile"
build_fixture unhealthy v1.0.3 "$sha_three" "$sha_three"

run_deploy one v1.0.0 "$sha_one"
for path in / /index.html /news/1 /login /admin/news /icon.svg /assets/plain.svg; do
  curl --fail --silent --head "$DEPLOY_URL$path" | grep -qi '^Cache-Control: no-cache'
done
curl --fail --silent --head "$DEPLOY_URL/assets/app-AbCd0123.js" \
  | grep -qi '^Cache-Control: public, max-age=31536000, immutable'
test "$(curl --silent --output /dev/null --write-out '%{http_code}' "$DEPLOY_URL/assets/missing-AbCd0123.js")" = 404

run_deploy two v1.0.1 "$sha_two"
if docker image inspect "daynomy-frontend-rollback:${image_ids[0]#sha256:}" >/dev/null 2>&1; then
  echo 'A successful deployment should remove its temporary rollback tag' >&2
  exit 1
fi
docker tag "$image_name:two" "$image_name:moved-tag"
run_deploy moved-tag v1.0.1 "$sha_two"
previous_image="$(running_image)"

# A rejected candidate must not replace the running container.
if run_deploy one v1.0.0 "$sha_three" > "$test_dir/labels.log" 2>&1; then
  echo 'An image with the wrong commit label should fail' >&2
  exit 1
fi
test "$(running_image)" = "$previous_image"

# Move a tag after the old container started: rollback must use the actual old
# image ID, not the new image now associated with that tag.
docker tag "$image_name:bad-marker" "$image_name:moved-tag"
if run_deploy moved-tag v1.0.2 "$sha_three" > "$test_dir/marker.log" 2>&1; then
  echo 'An image serving the wrong deployment marker should fail' >&2
  exit 1
fi
test "$(running_image)" = "$previous_image"
curl --fail --silent "$DEPLOY_URL/" | grep -Fq "development:$sha_two"
grep -Fq 'Rolled back: deployment remains failed' "$test_dir/marker.log"

if run_deploy unhealthy v1.0.3 "$sha_three" > "$test_dir/health.log" 2>&1; then
  echo 'An unhealthy deployment should fail' >&2
  exit 1
fi
test "$(running_image)" = "$previous_image"
grep -Fq 'Rolled back: deployment remains failed' "$test_dir/health.log"

docker compose --file "$COMPOSE_FILE" down --timeout 1 >/dev/null
if run_deploy bad-marker v1.0.2 "$sha_three" > "$test_dir/initial.log" 2>&1; then
  echo 'A failed first deployment should remain failed without a rollback target' >&2
  exit 1
fi
grep -Fq 'no healthy previous image; manual recovery required' "$test_dir/initial.log"

echo 'Frontend Docker deployment, rollback and cache checks passed'
