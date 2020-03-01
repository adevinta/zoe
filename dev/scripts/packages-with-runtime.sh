#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
set -ex

PROJECT_DIR=$(readlink -f "$(dirname $0)/../..")

target=${1}
version=${2}

if [[ -z "${target}" || -z "${version}" ]]; then
  echo "usage : $0 <[deb|rpm]> <version>"
  exit 1
fi

tmp_output_package_dir=$(mktemp -d)

jpackage \
    -i "${PROJECT_DIR}/zoe-cli/build/libs" \
    -n zoe \
    --main-class 'com.adevinta.oss.zoe.cli.MainKt' \
    --main-jar "zoe-cli-final.jar" \
    --type "${target}" \
    --dest "${tmp_output_package_dir}" \
    --app-version "${version}"

package=$(find "${tmp_output_package_dir}" -maxdepth 1 -name 'zoe*')

mkdir -p "${PROJECT_DIR}/packages"
cp "${package}" "${PROJECT_DIR}/packages"
