#!/usr/bin/env bash
# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

set -ex

source dev/scripts/env.sh

# check build
if [[ ! -d "${zoe_cli_install_dir}" ]]; then
  echo "you need to build the zoe cli jar first !"
  exit 1
fi

# create temporary packaging directoy
tmp_output_package_dir=$(mktemp -d)

# package
cp -R "${zoe_cli_install_dir}" "${tmp_output_package_dir}/zoe"
(cd "${tmp_output_package_dir}" && tar -czvf zoe.tar.gz zoe)
(cd "${tmp_output_package_dir}" && zip -r zoe.zip zoe)

# copy to target direction
mkdir -p "${packages_dir}"
mv "${tmp_output_package_dir}/zoe.tar.gz" "${packages_dir}/zoe-${project_version}.tar.gz"
mv "${tmp_output_package_dir}/zoe.zip" "${packages_dir}/zoe-${project_version}.zip"
