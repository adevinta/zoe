![Build](https://github.com/adevinta/zoe/workflows/Build%20test/badge.svg)

# Zoe: The Kafka CLI for humans

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out
this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) **due to its
ability to offload consumption and execution to kubernetes pods or lambda functions** (more runners will be supported in
the future).

## Try zoe from your browser!

You can try zoe from your browser using our [new Katacoda tutorials](https://www.katacoda.com/wlezzar/courses/zoe).

## Key features

Here are some of the most interesting features of zoe :

- Consume kafka topics from a specific point in time (ex. using `--from 'PT5h` from the last 5 hours).
- Filter data based on content (ex. using `--filter "id == '12345'"` filters records with the selected id).
- Filter data based on record metadata and record headers (ex.
  using `--filter-meta "offset == '95' && partition == '0'"` finds record on given partition with the given offset).
- Supports offloading consumption of data to multiple lambda functions, kubernetes pods, etc. for parallelism (ex.
  adding `--runner kubernetes` would offload all the requests to a configured kubernetes cluster).
- Monitor consumer groups' offsets.
- Upload avro schemas from a `.avsc` or `.avdl` file using different naming strategies.
- ... and more.

## Install

For Linux & MacOS, the simplest way is to use brew to install zoe:

```bash
brew install adevinta/homebrew-zoe/zoe
```

Go to the [install](docs/install/overview.md) page for instructions on other platforms.

## Quickstart

```bash
# Initialize zoe configuration
zoe config init

# The generated config points to a local kafka cluster (localhost:29092). You can edit it using the following command
zoe config edit

# You can inspect the list of clusters you have in your config
zoe -o table config clusters list

# You can now use zoe to interact with the clusters
zoe -o table topics list
```

## Sample commands

Read the last 10 records from the `input` topic from the `local` kafka cluster (aliases for topics and clusters are set
in the configuration) :

```
zoe --cluster local topics consume input -n 10 
```

Read the last 10 records from the last 6 hours :

```
zoe --cluster local topics consume input -n 10 --from 'PT6h'
```

Filter records belonging to `Kasimir` :

```
zoe --cluster local topics consume input -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'
```

Spin up 10 consumers in parallel :

```
zoe --cluster local topics consume input -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir' \
    --jobs 10
```

Offload consumption to kubernetes pods (the target kubernetes cluster is configured in zoe's configuration file):

```
zoe --runner kubernetes \
    --cluster local topics consume input -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir' \
    --jobs 10
```

## Documentation

The full documentation can be found on the [website](https://adevinta.github.io/zoe).

## Need help?

If you are encountering a bug or have any question, please open a Github issue in the repository.

## Maintainers

- Created by: Walid Lezzar ([Github](https://github.com/wlezzar), [Twitter](https://twitter.com/walezz)
  , [LinkedIn](https://www.linkedin.com/in/walid-lezzar/))

### Contributors

The [contributor's guide](docs/contributing/README.md) currently only shows how to build the project from source. Some
docs about the architecture of Zoe and how to contribute to the project will be added soon. 
