#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
test_repo="$(mktemp -d)"
trap 'rm -rf "$test_repo"' EXIT

git -C "$test_repo" init -q -b main
git -C "$test_repo" config user.name Test
git -C "$test_repo" config user.email test@example.com
mkdir "$test_repo/frontend"
printf '{"version":"1.0.0"}\n' > "$test_repo/frontend/package.json"
printf '# Frontend\n\n## v1.0.0\n' > "$test_repo/frontend/CHANGELOG.md"
git -C "$test_repo" add frontend
git -C "$test_repo" commit -qm initial

cd "$test_repo/frontend"
export GITHUB_OUTPUT="$test_repo/output"
export GITHUB_REF=refs/heads/dev
bash "$script_dir/resolve-release-version.sh"
grep -Fq "image_tag=$(git rev-parse HEAD)" "$GITHUB_OUTPUT"

export GITHUB_REF=refs/heads/main
: > "$GITHUB_OUTPUT"
bash "$script_dir/resolve-release-version.sh"
grep -Fq 'release_version=v1.0.0' "$GITHUB_OUTPUT"

git tag -a v1.0.0 -m release
: > "$GITHUB_OUTPUT"
bash "$script_dir/resolve-release-version.sh"
grep -Fq 'image_tag=v1.0.0' "$GITHUB_OUTPUT"

printf 'next change\n' > next.txt
git add next.txt
git commit -qm next
: > "$GITHUB_OUTPUT"
bash "$script_dir/resolve-release-version.sh"
grep -Fq "image_tag=$(git rev-parse HEAD)" "$GITHUB_OUTPUT"

printf '{"version":"1.0.1"}\n' > package.json
git add package.json
git commit -qm bump
if bash "$script_dir/resolve-release-version.sh" >/dev/null 2>&1; then
  echo 'Version missing from changelog should fail' >&2
  exit 1
fi

echo 'Frontend release version checks passed'
