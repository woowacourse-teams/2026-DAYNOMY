#!/usr/bin/env bash
set -euo pipefail

: "${COMPOSE_FILE:?COMPOSE_FILE is required}"
: "${FRONTEND_IMAGE:?FRONTEND_IMAGE is required}"
: "${DEPLOY_ENV:?DEPLOY_ENV is required}"
: "${DEPLOY_URL:?DEPLOY_URL is required}"
: "${DEPLOY_VERSION:?DEPLOY_VERSION is required}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"

export FRONTEND_IMAGE
compose=(docker compose --file "$COMPOSE_FILE")
wait_timeout="${DEPLOY_WAIT_TIMEOUT:-120}"
work_dir="$(mktemp -d)"
previous_image=''
previous_version=''
previous_revision=''
previous_marker=''
rollback_image=''
activation_started=false

summary() {
  local result="$1"
  echo "$result"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '\n### Frontend deployment: %s\n\n- Environment: %s\n- Requested version: %s\n- Requested commit: %s\n- Previous image: %s\n- Previous version: %s\n- Previous commit: %s\n' \
      "$result" "$DEPLOY_ENV" "$DEPLOY_VERSION" "$GITHUB_SHA" "${previous_image:-not recorded}" \
      "${previous_version:-unknown}" "${previous_revision:-unknown}" \
      >> "$GITHUB_STEP_SUMMARY" || true
  fi
}

container_state() {
  local container_id
  container_id="$("${compose[@]}" ps --all --quiet frontend)" || return 0
  if [ -n "$container_id" ]; then
    docker inspect --format 'status={{.State.Status}} image={{.Image}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
      "$container_id" || true
  fi
}

activate() {
  "${compose[@]}" up --detach --wait --wait-timeout "$wait_timeout" \
    --pull never --no-deps frontend
}

verify() {
  local expected_image="$1" expected_marker="$2" container_id actual_image
  container_id="$("${compose[@]}" ps --quiet frontend)" || return 1
  [ -n "$container_id" ] || { echo 'Frontend container is not running' >&2; return 1; }
  actual_image="$(docker inspect --format '{{.Image}}' "$container_id")" || return 1
  [ "$actual_image" = "$expected_image" ] || { echo 'Running image does not match the expected image' >&2; return 1; }

  curl --fail --silent --show-error --retry 5 --retry-all-errors --retry-delay 1 \
    --max-time 15 --header 'Cache-Control: no-cache' \
    "$DEPLOY_URL/" --output "$work_dir/index.html" || return 1
  grep -Fq '<div id="root"></div>' "$work_dir/index.html" || { echo 'React HTML was not returned' >&2; return 1; }
  if [ -n "$expected_marker" ]; then
    grep -Fq "name=\"daynomy-deployment\" content=\"$expected_marker\"" "$work_dir/index.html" \
      || { echo 'Public deployment marker does not match the expected commit' >&2; return 1; }
  fi
}

rollback() {
  if [ -z "$previous_image" ]; then
    summary 'Failed: no healthy previous image; manual recovery required'
    return 1
  fi
  echo "Restoring previous image $previous_image"
  export FRONTEND_IMAGE="$rollback_image"
  activate || { summary 'Rollback failed: container recovery failed'; return 1; }
  verify "$previous_image" "$previous_marker" || { summary 'Rollback failed: recovery verification failed'; return 1; }
  summary 'Rolled back: deployment remains failed'
}

finish() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    container_state
    if [ "$activation_started" = true ]; then
      rollback || true
    else
      summary 'Failed before container replacement; previous container unchanged'
    fi
  elif [ -n "$rollback_image" ]; then
    docker image rm "$rollback_image" >/dev/null 2>&1 || true
  fi
  rm -rf "$work_dir"
  exit "$status"
}
trap finish EXIT

# The workflow pulls first. Inspecting the running container preserves its actual
# image even when a newly pulled tag now points at a different image.
new_image="$(docker image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")"
revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$FRONTEND_IMAGE")"
version="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' "$FRONTEND_IMAGE")"
if [ "$revision" != "$GITHUB_SHA" ] || [ "$version" != "$DEPLOY_VERSION" ]; then
  echo 'Candidate image version/commit labels do not match the requested deployment' >&2
  exit 1
fi

container_id="$("${compose[@]}" ps --all --quiet frontend)"
if [ -n "$container_id" ]; then
  state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
  if [ "$state" = running ] && { [ "$health" = healthy ] || [ "$health" = none ]; }; then
    previous_image="$(docker inspect --format '{{.Image}}' "$container_id")"
    previous_version="$(docker image inspect --format '{{with index .Config.Labels "org.opencontainers.image.version"}}{{.}}{{else}}unknown{{end}}' "$previous_image")"
    previous_revision="$(docker image inspect --format '{{with index .Config.Labels "org.opencontainers.image.revision"}}{{.}}{{else}}unknown{{end}}' "$previous_image")"
    rollback_image="daynomy-frontend-rollback:${previous_image#sha256:}"
    docker tag "$previous_image" "$rollback_image"
    previous_marker="$(docker exec "$container_id" cat /usr/share/nginx/html/index.html \
      | sed -n 's/.*name="daynomy-deployment" content="\([^"]*\)".*/\1/p')"
  fi
fi

activation_started=true
activate
verify "$new_image" "$DEPLOY_ENV:$GITHUB_SHA"
activation_started=false
summary "Succeeded: image $new_image verified"
