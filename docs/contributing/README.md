# Contributor's guide

TODO

## Build from source

### Requirements
To build and deploy :
- java 11 or later (install with the awesome [sdkman](https://sdkman.io/)) 

### Build zoe cli

```bash
# switch to java 11 or later
# if you are using sdkman
sdk use java 11

# build zoe CLI
./gradlew clean zoe-cli:installDist

# launch zoe cli
zoe-cli/build/install/zoe/bin/zoe --help

# if you don't have any config yet
zoe-cli/build/install/zoe/bin/zoe config init
```

## Development

### Local testing

```bash
# spin up the local environment
docker-compose -f docs/guides/simple/docker-compose.yml up -d

# point zoe to the testing environment
export ZOE_CONFIG_DIR=docs/guides/simple/config

# use the locally built zoe CLI
zoe-cli/build/install/zoe/bin/zoe topics list

# produce some data
zoe-cli/build/install/zoe/bin/zoe topics produce --topic simple --from-file docs/guides/simple/data.json
```

### Testing actions
```bash
docker build -t gh-actions:ubuntu-latest dev/actions/images/ubuntu
act -P ubuntu-latest=gh-actions:ubuntu-latest -r -j release-runtimeless -e dev/actions/payloads/release.json release
```
