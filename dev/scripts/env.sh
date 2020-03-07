# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

set -e

error() {
  >&2 echo "$1"
  exit 1
}

project_dir=$(pwd)

# secrurity check: is project directory inferred correctly ?
for dir_name in "zoe-cli" "zoe-core" "zoe-service"; do
  dir_path="${project_dir}/${dir_name}"
  test -d "${dir_path}" || error "project not found : '$dir_path'. Are you sourcing env.sh correctly ?"
done


# TODO: fix this once gradle works correctly with jdk 14
# project_version=$("${project_dir}"/gradlew -q printVersion)
project_version=$(/usr/bin/git describe --tags --abbrev=0 --always | sed 's/^v//')

packages_dir="${project_dir}/packages"
zoe_cli_libs="${project_dir}/zoe-cli/build/libs"
zoe_cli_install_dir="${project_dir}/zoe-cli/build/install/zoe-cli-shadow"
zoe_cli_jar_name="zoe-cli-${project_version}.jar"
zoe_cli_jar_path="${zoe_cli_libs}/${zoe_cli_jar_name}"
