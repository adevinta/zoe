#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

set -ex

THIS_DIR=$(readlink -f "$(dirname "$0")")
PROJECT_DIR=$(readlink -f "${THIS_DIR}/../..")

# shellcheck disable=SC1090
source "$THIS_DIR/.env.sh"

# if version not supplied, use the version from git
version=${1}

if [[ -z "${version}" ]]; then
  echo "usage : $0 <version>"
  exit 1
fi


# check build
if [[ ! -f  "${ZOE_CLI_LIB}/${ZOE_CLI_JAR}" ]]; then
  echo "you need to build the zoe cli jar first !"
  exit 1
fi

# create temporary packaging directoy
tmp_output_package_dir=$(mktemp -d)

# package
cp -R "${PROJECT_DIR}/zoe-cli/build/install/zoe-cli-shadow" "${tmp_output_package_dir}/zoe"
(cd "${tmp_output_package_dir}" && tar -czvf zoe.tar.gz zoe)
(cd "${tmp_output_package_dir}" && zip -r zoe.zip zoe)

# copy to target direction
mkdir -p "${PROJECT_DIR}/packages"
mv "${tmp_output_package_dir}/zoe.tar.gz" "${PROJECT_DIR}/packages/zoe-${version}.tar.gz"
mv "${tmp_output_package_dir}/zoe.zip" "${PROJECT_DIR}/packages/zoe-${version}.zip"