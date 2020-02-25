# Zoe with avro

This tutorial shows zoe avro support with the schema registry

## Prerequisites
For this tutorial you will need :
- Zoe
- Docker and docker-compose

## Tutorial

Go to this directory to start the tutorial : `cd examples/avro`

Spin up the kafka cluster : `docker-compose up -d`.

Point to the zoe config relevant for this example : `export ZOE_CONFIG_DIR=$(pwd)/config`

From now on, you're ready to use zoe to interact with this spinned up kafka cluster. This cluster is available under the `local` alias (cf. `zoe-config/default.yml`)

The first step is to deploy the avro schema related to the cat facts data. This schema is defined in the `schema.avdl` file.

Usually, to deploy such a schema into the schema registry we first need to compile this `.avdl` into an `.avsc` file before posting it to the registry. With zoe, this compilation step is done automatically for us. In fact, zoe handles `.avdl` seamlessly.
```shell script
# interact with schemas
zoe -q -c local schemas list

# deploy the cat facts schema from the 
zoe -q -c local schemas deploy \
  --avdl \
  --from-file schema.avdl \
  --name CatFact \
  --strategy topicRecord \
  --topic input \
  --dry-run
```

Produce some events from the `data.json` using avro :
```shell script
zoe -c local topics produce --topic input --from-file $(pwd)/data.json
``` 

Read the data from the topic :
```shell script
# read the 10 last events
zoe -q -o table -c local topics consume input -n 10 --from 'PT1h'

# display events in a table
zoe -q -o table -c local topics consume input -n 10 --from 'PT1h' \
       --query '{id: _id, type: type, user: user, upvotes: upvotes}'

# filter out Kasimir's data
zoe -q -o table -c local topics consume input -n 10 --from 'PT1h' \
       --query '{id: _id, type: type, user: user, upvotes: upvotes}' \
       --filter "user.name.first == 'Kasimir'"
```
