# Zoe : The missing companion for kafka

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) due to its ability to offload consumption and execution to kubernetes pods or lambda functions (more runners will be supported in the future).

## Key features

Here are some of the most interesting features of zoe :

- Consume kafka topics from a specific point in time (ex. from the last hour).
- Filter data based on content (ex. filter events with `id == '12345'`).
- Supports offloading consumption of data to multiple lambda functions, kubernetes pods, etc. for parallelism.
- Monitor consumer groups' offsets.
- Upload avro schemas from a .avsc or .avdl file using different naming strategies.
- ... and more. 

## Install
Available soon... (once migrated to a public github repository)

## Documentation
Available soon... (once migrated to a public github repository)

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

## Auto completion (optional)
```bash
_ZOE_COMPLETE=bash java -cp zoe-cli/build/libs/zoe-cli-final.jar com.adevinta.oss.zoe.cli.MainKt > /tmp/complete.sh
source /tmp/complete.sh
```
