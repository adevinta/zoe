#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
set -ex

target=${1}

if [[ -z "${target}" ]]; then
  echo "usage : $0 <[deb|rpm]>"
  exit 1
fi

source dev/scripts/env.sh

tmp_output_package_dir=$(mktemp -d)

jpackage \
    -i "${zoe_cli_libs}" \
    -n zoe \
    --main-class 'com.adevinta.oss.zoe.cli.MainKt' \
    --main-jar "${zoe_cli_jar_name}" \
    --type "${target}" \
    --dest "${tmp_output_package_dir}" \
    --app-version "${project_version}"

package=$(find "${tmp_output_package_dir}" -maxdepth 1 -name 'zoe*')

mkdir -p "${packages_dir}"
cp "${package}" "${packages_dir}"
