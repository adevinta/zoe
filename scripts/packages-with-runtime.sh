#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


set -ex

THIS_DIR=$(readlink -f $(dirname "$0"))
PROJECT_DIR=$(readlink -f "${THIS_DIR}/..")

# shellcheck disable=SC1090
source "$THIS_DIR/.env.sh"

target=${1}

if [[ -z "${target}" ]]; then
  echo "usage : $0 [deb|rpm]"
  exit 1
fi

tmp_output_package_dir="/tmp/package"

rm -f ${tmp_output_package_dir}/zoe*.${target}

if [[ ! -f  "${ZOE_CLI_LIB}/${ZOE_CLI_JAR}" ]]; then
  echo "you need to build the zoe cli jar first !"
  exit 1
fi

version=$("${PROJECT_DIR}"/scripts/version.sh)

jpackage \
    -i "${ZOE_CLI_LIB}" \
    -n zoe \
    --main-class 'com.adevinta.oss.zoe.cli.MainKt' \
    --main-jar "${ZOE_CLI_JAR}" \
    --type "${target}" \
    --dest "${tmp_output_package_dir}" \
    --app-version "${version}"

package=$(ls -t ${tmp_output_package_dir}/zoe*.${target} | head -n 1)

if [[ -z "${package}" ]]; then
  echo "Zoe cli package not found !"
  exit 1
fi

mkdir -p "${PROJECT_DIR}/packages"
rm -f "${PROJECT_DIR}/packages"/zoe*.${target}
cp "${package}" "${PROJECT_DIR}/packages"