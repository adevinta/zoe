#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
set -ex

installer_type="$1"

if [[ -z "$installer_type" ]]; then
  echo "usage: $0 <installer_type>" >&2
  exit 1
fi

package_dir=$(mktemp -d --suffix "_zoe")
./gradlew zoe-cli:jpackage -Pjpackage.output="${package_dir}" -Pjpackage.installerType="${installer_type}" >&2

package=$(find "${package_dir}" -maxdepth 1 -name 'zoe*.'${installer_type})

[[ -z "${package}" ]] && \
  { echo "package not found in: ${package_dir}" >&2; exit 1; }

echo "$package"
