#!/usr/bin/env bash
set -euo pipefail

: "${COMPOSE_FILE:?COMPOSE_FILE is required}"
: "${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
: "${DEPLOY_ENV:?DEPLOY_ENV is required}"
: "${DEPLOY_URL:?DEPLOY_URL is required}"
: "${DEPLOY_VERSION:?DEPLOY_VERSION is required}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"

blue_port="${FRONTEND_BLUE_PORT:-3000}"
green_port="${FRONTEND_GREEN_PORT:-3001}"
prefix="${FRONTEND_CONTAINER_PREFIX:-frontend}"
upstream_file="${FRONTEND_UPSTREAM_FILE:-/etc/nginx/conf.d/daynomy-frontend-upstream.conf}"
assets_dir="${FRONTEND_ASSETS_DIR:-/opt/daynomy/frontend/assets}"
wait_timeout="${DEPLOY_WAIT_TIMEOUT:-120}"
drain_timeout="${DEPLOY_DRAIN_TIMEOUT:-120}"
[[ "$prefix" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo 'Invalid frontend container prefix' >&2; exit 1; }
for port in "$blue_port" "$green_port"; do
  [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 1024 ] && [ "$port" -le 65535 ] \
    || { echo 'Invalid frontend port' >&2; exit 1; }
done
[ "$blue_port" != "$green_port" ] || { echo 'Frontend ports must differ' >&2; exit 1; }

work_dir="$(mktemp -d)"
active_port=''
candidate_port=''
active_id=''
previous_image=''
previous_marker=''
previous_html=''
previous_version=''
previous_revision=''
candidate_started=false
switch_started=false

summary() {
  echo "$1"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '\n### Frontend deployment: %s\n\n- Environment: %s\n- Requested version: %s\n- Requested commit: %s\n- Previous image: %s\n- Previous version/commit: %s / %s\n- Previous/candidate ports: %s / %s\n' \
      "$1" "$DEPLOY_ENV" "$DEPLOY_VERSION" "$GITHUB_SHA" "${previous_image:-none}" \
      "${previous_version:-unknown}" "${previous_revision:-unknown}" \
      "${active_port:-unknown}" "${candidate_port:-unknown}" >> "$GITHUB_STEP_SUMMARY" || true
  fi
}

verify_http() {
  local url="$1" marker="$2" attempts="${3:-1}" expected_html="${4:-}" expected_port="${5:-}" route attempt
  for ((attempt=1; attempt<=attempts; attempt++)); do
    if curl --fail --silent --show-error --include --suppress-connect-headers --connect-timeout 3 --max-time 10 \
      --header 'Cache-Control: no-cache' \
      "$url/?deployment_check=$GITHUB_SHA" > "$work_dir/response"; then
      awk 'body {print} /^\r?$/ {body=1}' "$work_dir/response" > "$work_dir/index.html"
      route="$(awk 'tolower($1)=="x-daynomy-frontend:" {gsub(/\r/,"",$2); print $2}' "$work_dir/response")"
      if grep -Fq '<div id="root"></div>' "$work_dir/index.html" \
        && { [ -z "$marker" ] || grep -Fq "name=\"daynomy-deployment\" content=\"$marker\"" "$work_dir/index.html"; } \
        && { [ -z "$expected_html" ] || cmp -s "$expected_html" "$work_dir/index.html"; } \
        && { [ -z "$expected_port" ] || [ "$route" = "127.0.0.1:$expected_port" ]; }; then
        return 0
      fi
    fi
    [ "$attempt" -eq "$attempts" ] || sleep 1
  done
  echo "Frontend response or deployment marker verification failed: $url" >&2
  return 1
}

archive_assets() {
  local staging asset name pending
  staging="$(mktemp -d "$work_dir/assets.XXXXXX")"
  docker cp "$1:/usr/share/nginx/html/assets/." "$staging/"
  for asset in "$staging"/*; do
    [ -f "$asset" ] || continue
    name="${asset##*/}"
    [[ "$name" =~ ^[^/]+-[A-Za-z0-9_-]{8}\.[^/]+$ ]] || continue
    [[ "$name" != *.map ]] || continue
    [ ! -e "$assets_dir/$name" ] || continue
    # Existing immutable files must never be unlinked/truncated under live traffic.
    pending="$(mktemp "$assets_dir/.asset.XXXXXX")"
    install -m 644 "$asset" "$pending"
    mv -- "$pending" "$assets_dir/$name"
  done
}

install_upstream() {
  # Install beside the target, then rename so Nginx never reads a partial file.
  sudo -n install -m 644 "$1" "$upstream_file.next" || return 1
  sudo -n mv -- "$upstream_file.next" "$upstream_file" || return 1
  sudo -n nginx -t || return 1
  sudo -n systemctl reload nginx || return 1
}

rollback() {
  if [ -z "$active_id" ]; then
    summary 'Failed: no healthy previous frontend; manual recovery required'
    return 1
  fi
  install_upstream "$work_dir/previous-upstream.conf" \
    || { summary 'Rollback failed: Nginx recovery failed; both containers retained'; return 1; }
  verify_http "$DEPLOY_URL" "$previous_marker" 10 "$previous_html" "$active_port" \
    || { summary 'Rollback failed: public verification failed; both containers retained'; return 1; }
  summary 'Rolled back: previous container retained; deployment remains failed'
}

finish() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    if [ "$candidate_started" = true ]; then
      docker inspect --format 'status={{.State.Status}} image={{.Image}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
        "$FRONTEND_CONTAINER_NAME" 2>/dev/null || true
    fi
    if [ "$switch_started" = true ]; then
      # Reloaded old workers can still be serving the candidate after rollback.
      # The next deployment waits for them before reusing this port.
      rollback || true
    else
      if [ -z "$active_id" ] && [ "$candidate_started" = true ]; then
        summary 'Failed: no healthy previous frontend; manual recovery required'
      else
        summary 'Failed before routing switch; existing routing unchanged'
      fi
      if [ "$candidate_started" = true ]; then
        "${compose[@]}" rm --force --stop frontend >/dev/null 2>&1 || true
      fi
    fi
  fi
  rm -rf "$work_dir"
  exit "$status"
}
trap finish EXIT

# Validate the image and host setup before touching either container.
new_image="$(docker image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")"
revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$FRONTEND_IMAGE")"
version="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}}' "$FRONTEND_IMAGE")"
[ "$revision" = "$GITHUB_SHA" ] && [ "$version" = "$DEPLOY_VERSION" ] \
  || { echo 'Candidate image version/commit labels do not match' >&2; exit 1; }
cat "$upstream_file" > "$work_dir/previous-upstream.conf"
active_port="$(sed -nE 's/^[[:space:]]*server 127\.0\.0\.1:([0-9]+);[[:space:]]*$/\1/p' "$work_dir/previous-upstream.conf")"
case "$active_port" in
  "$blue_port") candidate_port="$green_port" ;;
  "$green_port") candidate_port="$blue_port" ;;
  *) echo 'Unknown active port; check the frontend upstream configuration' >&2; exit 1 ;;
esac
sudo -n nginx -T | tee "$work_dir/nginx.conf" >/dev/null
if ! grep -Fq 'proxy_pass http://daynomy_frontend;' "$work_dir/nginx.conf" \
  || ! grep -Fq "alias $assets_dir/" "$work_dir/nginx.conf"; then
  echo 'Host Nginx frontend routing/assets setup is required; see RELEASING.md' >&2
  exit 1
fi

active_id="$(docker ps --quiet --filter "name=^/$prefix-$active_port$")"
legacy_id="$(docker ps --quiet --filter "name=^/$prefix$")"
if [ -z "$active_id" ] && [ -n "$legacy_id" ] \
  && docker port "$legacy_id" 80/tcp | grep -Eq ":$active_port$"; then
  active_id="$legacy_id"
fi
if [ -n "$active_id" ]; then
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$active_id")"
  [ "$health" = healthy ] || [ "$health" = none ] \
    || { echo 'Active frontend is not healthy; manual inspection required' >&2; exit 1; }
  previous_image="$(docker inspect --format '{{.Image}}' "$active_id")"
  previous_version="$(docker image inspect --format '{{with index .Config.Labels "org.opencontainers.image.version"}}{{.}}{{else}}unknown{{end}}' "$previous_image")"
  previous_revision="$(docker image inspect --format '{{with index .Config.Labels "org.opencontainers.image.revision"}}{{.}}{{else}}unknown{{end}}' "$previous_image")"
  previous_marker="$(docker exec "$active_id" cat /usr/share/nginx/html/index.html \
    | sed -n 's/.*name="daynomy-deployment" content="\([^"]*\)".*/\1/p')"
  verify_http "http://127.0.0.1:$active_port" "$previous_marker"
  # Catch an interrupted earlier reload: on-disk routing must match public traffic.
  verify_http "$DEPLOY_URL" "$previous_marker" 10 '' "$active_port"
  if [ -z "$previous_marker" ]; then
    previous_html="$work_dir/previous-public.html"
    cp "$work_dir/index.html" "$previous_html"
  fi
elif curl --fail --silent --connect-timeout 3 --max-time 5 "http://127.0.0.1:$active_port/" >/dev/null; then
  echo 'The active port belongs to an unmanaged container; manual inspection required' >&2
  exit 1
fi

# A previous Nginx worker may still use the inactive port. Wait before reusing it.
for ((elapsed=0; elapsed<=drain_timeout; elapsed++)); do
  if pgrep -f '^nginx: worker process is shutting down' >/dev/null; then
    [ "$elapsed" -lt "$drain_timeout" ] \
      || { echo 'Previous Nginx requests are still draining; retry later' >&2; exit 1; }
    sleep 1
  else
    pgrep_status=$?
    [ "$pgrep_status" -eq 1 ] || exit "$pgrep_status"
    break
  fi
done

mkdir -p "$assets_dir"
# Keep hashed files across deployments so open tabs and rollbacks can load them.
# ponytail: assets accumulate; prune only after defining the supported old-version window.
if [ -n "$active_id" ]; then
  archive_assets "$active_id"
fi
# On the second deployment, retire the original single-container setup only after draining.
if [ -n "$legacy_id" ] && [ "$legacy_id" != "$active_id" ] \
  && docker port "$legacy_id" 80/tcp | grep -Eq ":$candidate_port$"; then
  docker stop --time 30 "$legacy_id" >/dev/null
  docker rm "$legacy_id" >/dev/null
fi

export FRONTEND_IMAGE FRONTEND_PORT="$candidate_port"
export FRONTEND_CONTAINER_NAME="$prefix-$candidate_port"
compose=(docker compose --project-name "$FRONTEND_CONTAINER_NAME" --file "$COMPOSE_FILE")
candidate_started=true
"${compose[@]}" up --detach --wait --wait-timeout "$wait_timeout" --pull never --no-deps frontend
candidate_id="$("${compose[@]}" ps --quiet frontend)"
[ "$(docker inspect --format '{{.Image}}' "$candidate_id")" = "$new_image" ] \
  || { echo 'Candidate container image does not match' >&2; exit 1; }
verify_http "http://127.0.0.1:$candidate_port" "$DEPLOY_ENV:$GITHUB_SHA"
# Archive the candidate too: rollback must still serve files from briefly exposed new HTML.
archive_assets "$candidate_id"
docker exec "$candidate_id" ls -1 /usr/share/nginx/html/assets > "$work_dir/candidate-assets"
while IFS= read -r asset; do
  [[ "$asset" =~ ^[^/]+-[A-Za-z0-9_-]{8}\.[^/]+$ ]] || continue
  [[ "$asset" != *.map ]] || continue
  curl --fail --silent --show-error --head --connect-timeout 3 --max-time 10 \
    --retry 3 --retry-all-errors --retry-delay 1 --header 'Cache-Control: no-cache' \
    "$DEPLOY_URL/assets/$asset?deployment_check=$GITHUB_SHA" > "$work_dir/asset-headers"
  if ! grep -Eqi '^Cache-Control: public, max-age=31536000, immutable[[:space:]]*$' "$work_dir/asset-headers" \
    || grep -Eqi '^Content-Type: text/html' "$work_dir/asset-headers"; then
    echo "Public asset/cache verification failed: $asset" >&2
    exit 1
  fi
done < "$work_dir/candidate-assets"

printf 'upstream daynomy_frontend {\n    server 127.0.0.1:%s;\n}\n' "$candidate_port" > "$work_dir/next-upstream.conf"
switch_started=true
install_upstream "$work_dir/next-upstream.conf"
verify_http "$DEPLOY_URL" "$DEPLOY_ENV:$GITHUB_SHA" 10 '' "$candidate_port"
summary "Succeeded: port $candidate_port image $new_image verified; previous container retained"
