# Zoe with Avro

This tutorial shows zoe support for avro and the schema registry. If you are new to zoe, start with [the basic scenario](../simple/guide.md).

## What you will learn

In this tutorial, you will learn the following aspects of zoe:

- Configure zoe to use against avro topics.
- Listing avro schemas registered in the schema registry
- Deploying `.avdl` avro schemas
- Writing json data into kafka topics using avro
- Reading avro data from a topic

## Prerequisites

For this guide you will need:

- Zoe (install instructions are [here](../../install/overview.md))
- Docker and docker-compose (that we will use to spin up Kafka)

## Prepare the environment

- Clone the [repository](https://github.com/adevinta/zoe): `git clone https://github.com/adevinta/zoe.git`
- Go to the directory: `docs/guides/avro`
- Spin up the kafka cluster: `docker-compose up -d`.
- Check all the containers are up:

```bash tab="command"
docker-compose ps      
```

```text tab="output"
  Name               Command            State                        Ports                      
------------------------------------------------------------------------------------------------
broker      /etc/confluent/docker/run   Up      0.0.0.0:29092->29092/tcp, 0.0.0.0:9092->9092/tcp
zookeeper   /etc/confluent/docker/run   Up      0.0.0.0:2181->2181/tcp, 2888/tcp, 3888/tcp      
```

## Configure zoe

Now, we need to configure zoe to make it aware of the cluster.

Create a new file called `default.yml` inside zoe's config directory (`~/.zoe/config` by default) and fill it with the following content:

```yaml tab="~/.zoe/config/default.yml"
clusters:
  default:
    props:
      bootstrap.servers: "localhost:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "io.confluent.kafka.serializers.KafkaAvroDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "io.confluent.kafka.serializers.KafkaAvroSerializer"

    registry: "http://localhost:8081"

    topics:
      input:
        name: "input-events-topic"
        subject: "input-events-topic-value"
    
runners:
  default: "local"
```

The [basic scenario](../simple/guide.md) guide explains most of the configuration above. The relevant parts for this guide are the following:

- In the `default` cluster's configuration, notice the `registry` key. This defines the address of the [schema registry](https://docs.confluent.io/current/schema-registry/schema_registry_tutorial.html) where avro schemas are stored. When we define this key, zoe automatically adds the `schema.registry.url` property to the consumers and producers and there is no need to supply it manually.
- Notice also that we are using the `KafkaAvroDeserializer` and `KafkaAvroSerializer` in the cluster's client properties.
- In the topics section, besides the name of the topic, we define the subject name of the schema associated with the topic. This is an optional parameter that is only used when producing data into the topic so that zoe serializes the data using the right Avro schema. You can also override it with the `--subject` option in the `topics produce` command. 

If you don't want to write this configuration by hand, you can copy the one already present in the repository by using the following command:

```bash tab="command"
zoe config init --from git --url https://github.com/adevinta/zoe --dir docs/guides/avro/config
``` 

Ensure zoe is now aware of the cluster by executing the following command:

```bash tab="command"
zoe --silent -o table config clusters list
```

```text tab="output"
┌─────────┬─────────────────┬───────────────────────┬────────────────────────────────────────────┬────────┐
│ cluster │ brokers         │ registry              │ topics                                     │ groups │
├─────────┼─────────────────┼───────────────────────┼────────────────────────────────────────────┼────────┤
│ default │ localhost:29092 │ http://localhost:8081 │ alias: "input"  name: "input-events-topic" │        │
└─────────┴─────────────────┴───────────────────────┴────────────────────────────────────────────┴────────┘
```

Now, you're ready to use zoe to interact with the local kafka cluster. This cluster is available in the provided config above under the `local` alias (take a look at `zoe-config/default.yml`)

## Creating the Cat Facts Avro schema with zoe

In this guide we will be using again the cat facts data that we used in the [basic scenario guide](../simple/guide.md). This time though we will serialize this data in Avro. So the first step is to create the Avro schema representing this data!

The Avro schema representing the Cat Facts dataset is provided [in the repository](schema.avdl). It is written in `.avdl` which is a format that describes avro schemas in an expressive and intuitive way. You can know more about `avdl` [here](https://avro.apache.org/docs/current/idl.html)

Usually, to deploy an `.avdl` file into the schema registry we first need to compile it down into an `.avsc` file before posting it to the registry. With zoe, this compilation step is done automatically for us. In fact, zoe handles `.avdl` files as seamlessly as `.avsc` files.

```bash tab="command"
# deploy the cat facts schema from the 
zoe schemas deploy \
  --avdl \
  --from-file schema.avdl \
  --name CatFact \
  --strategy topic \
  --topic input \
  --suffix value
```

```text tab="output"
2020-06-28 09:38:29 INFO zoe: loading config from url : file:/tmp/zoe-avro/default.yml
{"type":"actual","id":1,"subject":"input-events-topic-value"}
```

Ensure our schema is successfully registered by listing the topics from the schema registry:

```bash tab="command"
zoe --silent -o table schemas list
```

```bash tab="output"
┌──────────────────────────┐
│ subjects                 │
├──────────────────────────┤
│ input-events-topic-value │
└──────────────────────────┘
```

Describe the schema using:

```bash tab="command"
zoe --silent -o table schemas describe input-events-topic-value
```

```text tab="output"
┌──────────────────────────┬──────────┬─────────────────────────────────────────────────────────────────────────────────────────────┐
│ subject                  │ versions │ latest                                                                                      │
├──────────────────────────┼──────────┼─────────────────────────────────────────────────────────────────────────────────────────────┤
│ input-events-topic-value │ 1        │ {"type":"record","name":"CatFact","namespace":"com.adevinta.oss.zoe.guides","fields":[...]} │
└──────────────────────────┴──────────┴─────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Producing data

Now that we have the Cat Facts Avro schema registered, let's produce some cat facts from the `data.json` file in avro:

```bash tab="command"
zoe -o table topics produce --topic input --from-file data.json --subject input-events-topic-value
``` 

```text tab="output"
2020-06-28 09:48:11 INFO zoe: loading config from url : file:/tmp/zoe-avro/default.yml
2020-06-28 09:48:11 INFO zoe: producing '211' records to topic 'input-events-topic'
2020-06-28 09:48:12 INFO AppInfoParser: Kafka version: 5.5.0-ccs
2020-06-28 09:48:12 INFO AppInfoParser: Kafka commitId: 785a156634af5f7e
2020-06-28 09:48:12 INFO AppInfoParser: Kafka startTimeMs: 1593330492339
2020-06-28 09:48:12 INFO Metadata: [Producer clientId=producer-1] Cluster ID: pD8UlIdIQkyn40_NW_-f4w
2020-06-28 09:48:12 INFO KafkaProducer: [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
┌─────────────────────────────────────────────────────────────────────────────────┬─────────┐
│ produced                                                                        │ skipped │
├─────────────────────────────────────────────────────────────────────────────────┼─────────┤
│ offset: 43  partition: 0  topic: "input-events-topic"  timestamp: 1593330492564 │         │
│ offset: 31  partition: 2  topic: "input-events-topic"  timestamp: 1593330492591 │         │
│ offset: 47  partition: 1  topic: "input-events-topic"  timestamp: 1593330492591 │         │
│ offset: 48  partition: 1  topic: "input-events-topic"  timestamp: 1593330492591 │         │
│ offset: 50  partition: 4  topic: "input-events-topic"  timestamp: 1593330492591 │         │
│ offset: 86  partition: 3  topic: "input-events-topic"  timestamp: 1593330492619 │         │
| ...                                                                             |         |
│ offset: 89  partition: 1  topic: "input-events-topic"  timestamp: 1593330492619 │         │
│ offset: 96  partition: 0  topic: "input-events-topic"  timestamp: 1593330492619 │         │
└─────────────────────────────────────────────────────────────────────────────────┴─────────┘
```

We could have omitted the `--subject` option as we have already told zoe about that topic's subject name in the configuration (see above).

## Reading data

Reading data from an Avro topic with zoe is really no different from reading a Json topic. Zoe seemlessly transforms avro data into json and zoe can apply the jmespath filters with `--filter` as with json data. In fact the following command works exactly the same in both this guide and the [Basic scenario](../simple/guide.md) one:

```bash tab="command" 
# filter out Kasimir's data
zoe --silent -o table topics consume input \
       -n 10 \
       --from 'PT1h' \
       --query '{id: _id, type: type, user: user, upvotes: upvotes}' \
       --filter "user.name.first == 'Kasimir'"
```

```text tab="output"
┌──────────────────────────┬──────┬───────────────────────────────────────────┬─────────┐
│ id                       │ type │ user                                      │ upvotes │
├──────────────────────────┼──────┼───────────────────────────────────────────┼─────────┤
│ 58e008d00aac31001185ed0f │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │
│                          │      │ name: {"first":"Kasimir","last":"Schulz"} │         │
├──────────────────────────┼──────┼───────────────────────────────────────────┼─────────┤
│ 58e00b3a0aac31001185ed20 │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │
│                          │      │ name: {"first":"Kasimir","last":"Schulz"} │         │
├──────────────────────────┼──────┼───────────────────────────────────────────┼─────────┤
│ 58e00a090aac31001185ed16 │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │
│                          │      │ name: {"first":"Kasimir","last":"Schulz"} │         │
├──────────────────────────┼──────┼───────────────────────────────────────────┼─────────┤
│ 58e008d00aac31001185ed0f │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │
│                          │      │ name: {"first":"Kasimir","last":"Schulz"} │         │
├──────────────────────────┼──────┼───────────────────────────────────────────┼─────────┤
│ 58e00bdb0aac31001185edfd │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │
│                          │      │ name: {"first":"Kasimir","last":"Schulz"} │         │
└──────────────────────────┴──────┴───────────────────────────────────────────┴─────────┘
```
