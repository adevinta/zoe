# Copyright (c) 2020 Adevinta.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

THIS_DIR=$(readlink -f $(dirname $0))
PROJECT_DIR=$(readlink -f "${THIS_DIR}/..")

DOCKER_CMD_PREFIX="docker run -it --rm -v /tmp:/tmp -v ${PROJECT_DIR}:${PROJECT_DIR} -w ${PROJECT_DIR}"

# TODO: check if this is supported accros all environments
if [[ "${USE_CURRENT_USER}" == '1' ]]; then
  DOCKER_CMD_PREFIX="${DOCKER_CMD_PREFIX} --user=$(id -u):$(id -g) -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -v $HOME:$HOME"
fi

# build images
JAVA_DOCKER_HOME=${JAVA_DOCKER_HOME:-"/tmp/docker-java-home"}
GRADLE_DOCKER="${DOCKER_CMD_PREFIX} -v ${JAVA_DOCKER_HOME}:/root gradle:jre-slim"
JAVA_DOCKER="${DOCKER_CMD_PREFIX} -v ${JAVA_DOCKER_HOME}:/root openjdk:11-jdk"

# packaging related
JAVA_14_LINUX_DOCKER="${DOCKER_CMD_PREFIX} -v ${JAVA_DOCKER_HOME}:/root openjdk:14-jdk-buster"

ZOE_CLI_LIB="${PROJECT_DIR}/zoe-cli/build/libs"
ZOE_CLI_JAR="zoe-cli-final.jar"
