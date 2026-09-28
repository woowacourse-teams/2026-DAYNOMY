#!/usr/bin/env bash
set -euo pipefail

commit="$(git rev-parse HEAD)"
release_version=''

if [[ "${GITHUB_REF:-}" == refs/heads/main ]]; then
  package_version="$(node -p "require('./package.json').version")"
  if [[ ! "$package_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    echo "Invalid frontend package version: $package_version" >&2
    exit 1
  fi

  candidate="v$package_version"
  if git show-ref --verify --quiet "refs/tags/$candidate"; then
    tag_commit="$(git rev-parse "refs/tags/$candidate^{commit}")"
    if [[ "$tag_commit" == "$commit" ]]; then
      release_version="$candidate"
    fi
  else
    grep -Fqx "## $candidate" CHANGELOG.md || {
      echo "Record $candidate in frontend/CHANGELOG.md before release" >&2
      exit 1
    }
    release_version="$candidate"
  fi
fi

image_tag="${release_version:-$commit}"
{
  echo "image_tag=$image_tag"
  echo "release_version=$release_version"
} >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
