#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

if [[ $(git rev-parse --is-shallow-repository) == true ]]; then
    echo 'Versioning requires full Git history: run git fetch --unshallow --tags.' >&2
    exit 1
fi

# Increases along descendant history; do not rewrite published release history.
version_code=$(git rev-list --count HEAD)
description=$(git describe --tags --long --always --abbrev=8 --match 'v[0-9]*.[0-9]*.[0-9]*' HEAD)
if [[ $description =~ ^v([0-9]+\.[0-9]+\.[0-9]+)-([0-9]+)-g([0-9a-f]+)$ ]]; then
    version_name=${BASH_REMATCH[1]}
    distance=${BASH_REMATCH[2]}
    revision=${BASH_REMATCH[3]}
    if (( distance > 0 )); then
        version_name+=".r${distance}.g${revision}"
    fi
elif [[ $description =~ ^[0-9a-f]+$ ]]; then
    # Before the first version tag, count from the start of the repository.
    version_name="0.0.0.r${version_code}.g${description}"
else
    echo "Expected a vMAJOR.MINOR.PATCH tag; got: $description" >&2
    exit 1
fi

printf 'versionName=%s\nversionCode=%s\n' "$version_name" "$version_code"
