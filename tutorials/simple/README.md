# A simple example

This tutorial walks you through the most basic functionalities of zoe. It makes use of a dataset downloaded from the [Cat Facts API](http://www.catfact.info/) to explore reading and writing data from / into kafka using zoe.

In this tutorial, we will spin up a simple one node kafka cluster and then interact with it using zoe.

If you want to run this tutorial yourself, you can find all the relevant files / datasets [here](https://github.com/adevinta/zoe/tree/master/tutorials/simple).

## What you will learn

In this tutorial, you will learn the following aspects of zoe :

- listing topics
- describing topics
- writing json data into kafka topics
- reading json data from a topic
- using jmespath expressions to filter read data based on its content.

## Prerequisites

For this tutorial you will need :

- Zoe (follow instructions [here](../../docs/install/overview.md))
- Docker and docker-compose

## Prepare the environment

- Clone the [repository](https://github.com/adevinta/zoe) : `git clone https://github.com/adevinta/zoe.git`
- Go to the directory : `tutorials/simple`
- Spin up the kafka cluster : `docker-compose up -d`.
- Point zoe to this tutorial's config : `export ZOE_CONFIG_DIR=$(pwd)/config`

Now, you're ready to use zoe to interact with the local kafka cluster. This cluster is available in the provided config above under the `local` alias. Do not hesitate to take a look at the configuration file at `config/default.yml` to have a first sight on how zoe is configured.

## Start using Zoe

To list and describe topics :

```
# interact with the topics
zoe -c local topics list
zoe -c local topics describe input-events-topic
```

To avoid repeating the `-c local` option on each command you can set the `local` cluster as the default one for the current session : `export ZOE_CLUSTER=local`. Now you can write :

```
# interact with the topics
zoe topics list
``` 

Let's write some data in the topic. We have some cats facts in the `data.json` file that we can use. The following command writes this json data into the `input-events-topic` aliased as the `input` in zoe's configuration (`config/default.yml`) :

```
zoe topics produce --topic input --from-file $(pwd)/data.json
``` 

Read the data that we have just inserted :

```
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
