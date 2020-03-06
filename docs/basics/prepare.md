# Prepare the environment

In this step, we will spin up a single node kafka cluster with docker and docker compose. This cluster will be already initialized with some topics created by an initializer script.

We will then create the required zoe configuration to point to this cluster so we can start interacting with it.

## Prerequisites

For this tutorial you will need :

- Zoe (follow the install instructions [here](../install/overview.md))
- Docker and docker-compose

## Spin up the kafka cluster

Clone the [zoe repository](https://github.com/adevinta/zoe) and spin up the kafka cluster :

```bash
# clone the repo
git clone https://github.com/adevinta/zoe.git

# go the to the tutorials folder
cd zoe/tutorials/simple

# spin up the stack
docker-compose up -d
```

Now you should have :

- A zookeeper node
- A kafka node
- 2 topics created : `input-topic` and `another-topic`

Now let's go to the next step and configure zoe to point to this cluster.

## Configuring zoe

Zoe uses a central configuration file to store all the information related to the kafka clusters that we want to interact with. This file keeps all the cluster properties (`bootstrap.servers`, etc.) and topics configuration so we don't have to repeat them again and again each time we want to interact with the cluster.

We can easily initialize a default configuration file using :

```bash tab="command"
zoe -v config init
```

```text tab="logs"
2020-03-07 01:43:16 INFO zoe: creating a new config file...
```

This creates a default configuration file at `~/.zoe/config/default.yml` that can be used as a starting point. This file looks like the following (some optional fields have been omitted for readability):

```yaml
clusters:

  local:
    props:
      bootstrap.servers: "localhost:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

runners:
  default: "local"
``` 

This config file defines the following elements :

- A `clusters` section where all the kafka clusters configuration are defined. Each cluster configuration is keyed by an alias. In this case, a single kafka cluster configuration aliased `local` is defined in the above config. This alias is what we will use to refer to this cluster when using `zoe`.
- Each cluster configuration defines :
    - A `props` section that represent the sets of properties to be injected to the kafka clients when interacting with the cluster.
    - A `topics` section that contain a list of topic configuration keyed by an alias. In this case, one topic is defined with the alias `input` and whose real name in kafka is `input-topic`. This alias is what we will use when writing or reading from the topic in zoe to avoid dealing with and remembering long topic names.
- Lastly, there is a `runners` section that defines the `local` runner as the default. We will talk more about runners in [the advanced section](../advanced/runners/overview.md) but keep in mind that zoe offloads the consumption and the interaction with kafka clusters to "runners" that can either be local or remote (lambda functions or kubernetes pods).

The default configuration is already enough to interact with our locally spinned up kafka cluster. So we should be able to start interacting with it without any change.

We can use zoe to inspect the kafka cluster and list the available topics :

```bash tab="command"
zoe -c local topics list
```

```json tab="output"
["input-topic", "another-topic"]
```

We can see that there is 2 topics within our cluster. Let's modify the configuration to add these 2 topics and give them an alias so we don't have to remember their names :

```yaml
clusters:

  local:
    props:
      bootstrap.servers: "localhost:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"
    topics:
      input:
        name: "input-topic"
      another:
        name: "another-topic"

runners:
  default: "local"
``` 

Now that our configuration is ready, let's use zoe to produce some test data into the cluster.
