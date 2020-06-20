# Zoe : The missing companion for Kafka

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) **due to its ability to offload consumption and execution to kubernetes pods or lambda functions** (more runners will be supported in the future).

## Status

Zoe has been open sourced very recently and is not GA yet. It is actively being improved towards stabilization. Documentation is also in progress. That said, we are already using it at Adevinta and you can already start trying it if you are not afraid of digging into the code to solve some eventual undocumented problems :) . 

## Key features

Here are some of the most interesting features of zoe :

- Consume kafka topics from a specific point in time (ex. using `--from 'PT5h` from the last 5 hours).
- Filter data based on content (ex. using `--filter "id == '12345'"` filters records with the selected id).
- Supports offloading consumption of data to multiple lambda functions, kubernetes pods, etc. for parallelism (ex. adding `--runner kubernetes` would offload all the requests to a configured kubernetes cluster).
- Monitor consumer groups' offsets.
- Upload avro schemas from a `.avsc` or `.avdl` file using different naming strategies.
- ... and more.

## Install

Go to the [install](docs/install/overview.md) page for instructions on how to install the Zoe CLI.

## Sample commands

Read the last 10 records from the `input` topic from the `local` kafka cluster (aliases for topics and clusters are set in the configuration) :

```
zoe --cluster local topics consume -n 10 
```

Read the last 10 records from the last 6 hours :

```
zoe --cluster local topics consume -n 10 --from 'PT6h'
```

Filter records belonging to `Kasimir` :

```
zoe --cluster local topics consume -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'
```

Spin up 10 consumers in parallel :

```
zoe --cluster local topics consume -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir' \
    --jobs 10
```

Offload consumption to kubernetes pods (the target kubernetes cluster is configured in zoe's configuration file):

```
zoe --runner kubernetes \
    --cluster local topics consume -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir' \
    --jobs 10
```

## Documentation

The full documentation can be found on the [website](https://adevinta.github.io/zoe).

## Maintainers

- Created by: Walid Lezzar ([Github](https://github.com/wlezzar), [Twitter](https://twitter.com/walezz), [LinkedIn](https://www.linkedin.com/in/walid-lezzar/))

### Contributors

Contributor's guide coming soon.

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
_ZOE_COMPLETE=bash zoe-cli/build/install/zoe-cli-shadow/bin/zoe > /tmp/complete.sh
source /tmp/complete.sh
```

## Development

### Testing actions
```bash
docker build -t gh-actions:ubuntu-latest dev/actions/images/ubuntu
act -P ubuntu-latest=gh-actions:ubuntu-latest -r -j release-runtimeless -e dev/actions/payloads/release.json release
```
