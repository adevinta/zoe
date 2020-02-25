# Simple example

This tutorial will walk you through how to use zoe with a basic kafka cluster and some json data.

## Prerequisites
For this tutorial you will need :
- Zoe
- Docker and docker-compose

## Tutorial

### Prepare the environment
Go to this directory to start the tutorial : `cd examples/simple`

Spin up the kafka cluster : `docker-compose up -d`.

Point to the zoe config relevant for this example : `export ZOE_CONFIG_DIR=$(pwd)/config`

From now on, you're ready to use zoe to interact with the local kafka cluster. This cluster is available under the `local` alias (cf. `zoe-config/default.yml`)

### Start using Zoe

To list and describe topics :
```shell script
# interact with topics
zoe -c local topics list
zoe -c local topics describe input-events-topic
```

To avoid repeating the `-c local` option on each command you can set `local` as the default cluster for zoe : `export ZOE_CLUSTER=local` 

Produce some events from the `data.json` to have some data in the topic :
```shell script
zoe topics produce --topic input --from-file $(pwd)/data.json
``` 

Read the data from the topic :
```shell script
# read the 10 last events
zoe -q -o table topics consume input -n 10 --from 'PT1h'

# display events in a table
zoe -q -o table topics consume input -n 10 --from 'PT1h' \
       --query '{id: _id, type: type, user: user, upvotes: upvotes}'

# filter out Kasimir's data
zoe -q -o table topics consume input -n 10 --from 'PT1h' \
       --query '{id: _id, type: type, user: user, upvotes: upvotes}' \
       --filter "user.name.first == 'Kasimir'"
```
