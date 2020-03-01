#!/usr/bin/env bash
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