#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

set -ex

archive_type="$1"
suffix="$2"

if [[ -z "$archive_type" ]]; then
  echo "usage: $0 <archive_type>" >&2
  exit 1
fi

# build distribution
package_dir=$(mktemp -d)

./gradlew \
  zoe-cli:"${archive_type}DistWithoutRuntime" \
  -P${archive_type}DistWithoutRuntime.outputDir="${package_dir}" \
  -P${archive_type}DistWithoutRuntime.suffix="${suffix}" >&2

package=$(find "${package_dir}" -maxdepth 1 -name 'zoe*.'${archive_type})

[[ -z "${package}" ]] && \
  { echo "package not found in: ${package_dir} (files: $(ls ${package_dir}))" >&2; exit 1; }

echo ${package}
