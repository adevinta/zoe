# Zoe with Avro

This tutorial shows zoe support for avro and the schema registry. If you are new to zoe, start with [the simple example](./simple.md).

## What you will learn

In this tutorial, you will learn the following aspects of zoe :

- listing avro schemas registerd in the schema registry
- deploying `.avdl` avro schemas
- writing json data into kafka topics using avro
- reading avro data from a topic
- using jmespath expressions to filter read data based on its content.

## Prerequisites
For this tutorial you will need :

- Zoe
- Docker and docker-compose

## Prepare the environment

- Clone the [repository](https://github.com/adevinta/zoe) : `git clone https://github.com/adevinta/zoe.git`
- Go to the directory : `tutorials/simple`
- Spin up the kafka cluster : `docker-compose up -d`.
- Point zoe to this tutorial's config : `export ZOE_CONFIG_DIR=$(pwd)/config`

Now, you're ready to use zoe to interact with the local kafka cluster. This cluster is available in the provided config above under the `local` alias (take a look at `zoe-config/default.yml`)

## Start using Zoe

In this tutorial we are still using the cats facts data that we used in the previous tutorial. But this time we are serializing into kafka in avro format.

In order to do this, we have provided a ready to use avro schema that describe the fact cats data in the `schema.avdl` file. The first step is to deploy this schema.

Usually, to deploy an `.avdl` file into the schema registry we first need to compile it into an `.avsc` file before posting it to the registry. With zoe, this compilation step is done automatically for us. In fact, zoe handles `.avdl` files seamlessly.

```
# list currently registered avro schemas
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
```
zoe -c local topics produce --topic input --from-file $(pwd)/data.json
``` 

Read the data from the topic :
```
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
