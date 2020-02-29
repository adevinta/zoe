#!/usr/bin/env bash
set -ex

THIS_DIR=$(readlink -f "$(dirname "$0")")
PROJECT_DIR=$(readlink -f "${THIS_DIR}/..")

# shellcheck disable=SC1090
source "$THIS_DIR/.env.sh"

# build
./gradlew zoe-cli:installShadowDist

# create temporary packaging directoy
tmp_output_package_dir="/tmp/package"
rm -Rf "${tmp_output_package_dir}"
mkdir -p "${tmp_output_package_dir}"

# delete already existing artifacts if they exist
mkdir -p "${PROJECT_DIR}/packages"
rm -Rf "${PROJECT_DIR}/packages/zoe*.tar.gz"
rm -Rf "${PROJECT_DIR}/packages/zoe*.zip"

# package
cp -R "${PROJECT_DIR}/zoe-cli/build/install/zoe-cli-shadow" "${tmp_output_package_dir}/zoe-cli"
(cd "${tmp_output_package_dir}" && tar -czvf zoe-cli.tar.gz zoe-cli)
(cd "${tmp_output_package_dir}" && zip -r zoe-cli.zip zoe-cli)
mv "${tmp_output_package_dir}/zoe-cli.tar.gz" "${PROJECT_DIR}/packages"
mv "${tmp_output_package_dir}/zoe-cli.zip" "${PROJECT_DIR}/packages"
