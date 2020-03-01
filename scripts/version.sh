#!/usr/bin/env bash
set -e

version=${GITHUB_REF:-$(git describe --tags --abbrev=0)}

if [[ $version == v* ]]; then
  version=${version:1}
fi

echo "$version"