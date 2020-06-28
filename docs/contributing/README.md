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
./gradlew clean zoe-cli:installShadowDist

# launch zoe cli
zoe-cli/build/install/zoe-cli-shadow/bin/zoe --help

# if you don't have any config yet
zoe-cli/build/install/zoe-cli-shadow/bin/zoe config init
```

## Development

### Testing actions
```bash
docker build -t gh-actions:ubuntu-latest dev/actions/images/ubuntu
act -P ubuntu-latest=gh-actions:ubuntu-latest -r -j release-runtimeless -e dev/actions/payloads/release.json release
```
