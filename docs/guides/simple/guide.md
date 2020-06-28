# A basic scenario

This guide walks you through the most basic functionalities of zoe. It uses a funny dataset downloaded from the [Cat Facts API](http://www.catfact.info/) to explore reading and writing data from / into kafka using zoe.

In this guide, we will spin up a simple one node kafka cluster locally and then interact with it using zoe.
 
If you want to follow along the instructions in this guide, you can find all the relevant files / datasets [in the repository](https://github.com/adevinta/zoe/tree/master/docs/guides/simple).

## What you will learn

In this guide, you will learn the following aspects of zoe:

- Creating topics
- Listing topics
- Describing topics
- Writing json data into kafka
- Reading json data from kafka
- Using jmespath expressions to filter read data based on its content.

## Prerequisites

For this guide you will need:

- Zoe (install instructions are [here](../../install/overview.md))
- Docker and docker-compose (that we will use to spin up Kafka)

## Prepare the environment

- Clone the [repository](https://github.com/adevinta/zoe): `git clone https://github.com/adevinta/zoe.git`
- Go to the directory: `docs/guides/simple`
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
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

    topics:
      input:
        name: input-topic

runners:
  default: "local"
```

The configuration above defines:

- A `clusters` section containing a single cluster aliased `default`. Clusters can be given any alias. Later on, when using zoe, we refer to a specific cluster by its alias using the `--cluster <cluster alias>` option. Otherwise, Zoe uses the cluster alias `default` if no cluster is specified using `--cluster`.
- The `default` cluster configuration defines:
    - A `props` section containing the kafka properties that need to be supplied to the kafka clients when interacting with the cluster.
    - A `topics` section containing a single topic aliased `input` that points to the real topic name `input-topic`. When reading to / from topics with zoe, we refer to a specific topic by its alias or real name. If we use an alias, zoe replaces it with the real name defined in the configuration.
- The `runners` section defines the `local` runner as the default one. This makes zoe by default runs the consumers / producers from the caller's machine. Zoe can use other runners like the kubernetes runner which runs the consumers / producers as kubernetes pods in a remote cluster. For more information about runners, checkout the [dedicated section about runners](https://adevinta.github.io/zoe/advanced/runners/overview/).

For more information on how to configure zoe, checkout the [configuration section in the documentation](https://adevinta.github.io/zoe/configuration/overview/).

If you don't want to write this configuration by hand, you can copy the one already present in the repository by using the following command:

```bash tab="command"
zoe config init --from git --url https://github.com/adevinta/zoe --dir docs/guides/simple/config
``` 

You can check that zoe is now aware of the cluster by executing the following command:

```bash tab="command"
zoe --silent -o table config clusters list
```

```text tab="output"
┌─────────┬─────────────────┬──────────┬─────────────────────────────────────┬────────┐
│ cluster │ brokers         │ registry │ topics                              │ groups │
├─────────┼─────────────────┼──────────┼─────────────────────────────────────┼────────┤
│ local   │ localhost:29092 │ null     │ alias: "input"  name: "input-topic" │        │
└─────────┴─────────────────┴──────────┴─────────────────────────────────────┴────────┘
```

Now, you're ready to use zoe to interact with the local kafka cluster.

## Creating topics

The first step is to create our input topic by executing the following command:

```bash tab="command"
zoe topics create input --partitions 5 --replication-factor 1
```

```text tab="output"
2020-06-27 17:53:23 INFO zoe: loading config from url : file:~/.zoe/config/default.yml
2020-06-27 17:53:23 INFO zoe: creating topic: input-topic
2020-06-27 17:53:24 INFO AppInfoParser: Kafka version: 5.5.0-ccs
2020-06-27 17:53:24 INFO AppInfoParser: Kafka commitId: 785a156634af5f7e
2020-06-27 17:53:24 INFO AppInfoParser: Kafka startTimeMs: 1593273204138
{"done":true}
```

Notice that we have used the topic alias `input` and zoe replaced it with the real name of the topic `input-topic` defined in the configuration file. In zoe, you can use both aliases or real topic names when interacting with topics.

Let's check the topic has been created (we are using the `--silent` option to avoid outputting logs):

```bash tab="command"
zoe --silent -o table topics list
```

```text tab="output"
┌───────────────┐
│ value         │
├───────────────┤
│ "input-topic" │
└───────────────┘
```

We can also describe the topic:

```bash tab="command"
zoe --silent -o table topics describe input
```

```text tab="output"
┌─────────────┬──────────┬────────────┐
│ topic       │ internal │ partitions │
├─────────────┼──────────┼────────────┤
│             │          │ 0          │
│             │          │ 1          │
│ input-topic │ false    │ 2          │
│             │          │ 3          │
│             │          │ 4          │
└─────────────┴──────────┴────────────┘
```

## Reading and writing data into the topic

Let's write some data into the topic.

We will use the dataset [provided in the repository](https://github.com/adevinta/zoe/blob/master/docs/guides/simple/data.json) that contain some data from cats facts API.

The following command writes the json data contained in this file into the `input` topic (assuming you are in the `docs/guides/simple` directory):

```bash tab="command"
zoe -o table topics produce --topic input --from-file data.json
```

```text tab="output"
2020-06-27 18:03:17 INFO zoe: loading config from url : file:~/.zoe/config/default.yml
2020-06-27 18:03:17 INFO zoe: producing '212' records to topic 'input-topic'
2020-06-27 18:03:17 INFO AppInfoParser: Kafka version: 5.5.0-ccs
2020-06-27 18:03:17 INFO AppInfoParser: Kafka commitId: 785a156634af5f7e
2020-06-27 18:03:17 INFO AppInfoParser: Kafka startTimeMs: 1593273797705
2020-06-27 18:03:17 INFO Metadata: [Producer clientId=producer-1] Cluster ID: s7yIyv-2RSOWeNSaNSgAhQ
2020-06-27 18:03:17 INFO KafkaProducer: [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
┌──────────────────────────────────────────────────────────────────────────┬─────────┐
│ produced                                                                 │ skipped │
├──────────────────────────────────────────────────────────────────────────┼─────────┤
│ offset: 30  partition: 3  topic: "input-topic"  timestamp: 1593273797924 │         │
│ offset: 53  partition: 1  topic: "input-topic"  timestamp: 1593273797937 │         │
│ offset: 47  partition: 4  topic: "input-topic"  timestamp: 1593273797937 │         │
│ offset: 31  partition: 3  topic: "input-topic"  timestamp: 1593273797937 │         │
│ offset: 32  partition: 3  topic: "input-topic"  timestamp: 1593273797937 │         │
│ offset: 94  partition: 1  topic: "input-topic"  timestamp: 1593273797951 │         │
| ...                                                                      | ...     |
│ offset: 95  partition: 1  topic: "input-topic"  timestamp: 1593273797951 │         │
│ offset: 96  partition: 1  topic: "input-topic"  timestamp: 1593273797951 │         │
│ offset: 85  partition: 2  topic: "input-topic"  timestamp: 1593273797951 │         │
└──────────────────────────────────────────────────────────────────────────┴─────────┘
```

To read the data we have just inserted:

```bash tab="command"
# read 5 messages from the last hour
zoe --silent -o table topics consume input -n 5 --from 'PT1h'
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
│ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 5b1b411d841d9700146158d9 │ The Egyptian Mau’s name is derived from the Middle... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 5       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 59a60b8e6acf530020f3586e │ Cat owners are 17% more likely to have a graduate ... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 5       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 5b1b4055841d9700146158d3 │ Scottish sailer Alexander Selkirk once survived fo... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 5a4d76916ef087002174c28b │ A cat’s nose pad is ridged with a unique pattern, ... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e00a120aac31001185ed17 │ A cat's brain is 90% similar to a human's — more s... │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
└──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
```

You can know more about the different options that can be used with: `zoe topics consume --help`. 

## Filtering events

One of the most powerful feature of zoe is its ability to filter out events using criterias based on events content.

In the following example, we are filtering out cat facts written by the user whose first name is `Kasimir`:

```bash tab="command"
# filter out Kasimir's data
zoe -o table topics consume input \
       -n 5 \
       --from 'PT1h' \
       --filter "user.name.first == 'Kasimir'"
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
│ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e00a120aac31001185ed17 │ A cat's brain is 90% similar to a human's — more s... │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e007cc0aac31001185ecf5 │ Cats are the most popular pet in the United States... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e008510aac31001185ecfe │ In tigers and tabbies, the middle of the tongue is... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e00bdb0aac31001185edfd │ Cats can change their meow to manipulate a human. ... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e00ba00aac31001185edfa │ When cats leave their poop uncovered, it is a sign... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
└──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
```

The `--filter` option takes a [jmespath](https://jmespath.org/) expression and runs it against each event read from the topic. If this expression returns `true` for a given event, this event is returned. Otherwise, it is discarded.
 
This feature is especially powerful when using [remote runners](https://adevinta.github.io/zoe/advanced/runners/overview/) as Zoe would run the consumers in the cloud (kubernetes or AWS lambdas) and only returns the relevant data to the caller's machine.

## Clean up the environment

```bash
docker-compose down -v
```