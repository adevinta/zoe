# Using avro and the schema registry

Zoe has good support for Avro and the schema registry. It makes it easy to consume and display avro data nicely as normal json objects or as a table (`--output table`). It is also easy to produce Avro data from json files as an input.

## Consuming Avro data

In order to consume Avro data from Kafka, it is enough to set the necessary deserializers properties in the `props` section of the cluster config :

```yaml
clusters:

  my-cluster:
    props:
      bootstrap.servers: my-cluster.example.com:9094
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      schema.registry.url: http://my-registry.example.com
```

Then, it's possible to consume Avro data using the following command :

```bash
zoe -v -c my-cluster topics consume input -n 10
```

With the above command :

- Zoe consumes data from the input topic and deserializes it using the `key.deserializer` and `value.deserializer`.
- It transforms the data into json (to be able to apply eventual filters)

If the registry has a dedicated API key, you may configure it using:

```yaml
      basic.auth.credentials.source: USER_INFO
      basic.auth.user.info: <REGISTRY_API_KEY>:<REGISTRY_API_SECRET>
```

## Producing Avro data

To produce Avro data from a json input using the `produce` command, it is necessary to set :

- The `registry` property that points to the schema registry instance. Zoe uses this property to fetch the Avro schema needed to transform the json data into Avro.
- The required serializer properties of the producer in the `props` section of the cluster config.
- In addition, you need to set the `subject` name of the topic in the topic's configuration.

A good enough configuration for producing Avro data may look like the following :

```yaml
clusters:

  my-cluster:
    props:
      bootstrap.servers: my-cluster.example.com:9094
      key.serializer: org.apache.kafka.common.serialization.StringSerializer
      value.serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      value.subject.name.strategy: io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
    registry: https://my-registry.example.com
    topics:
      my-topic:
        name: long-topic-name
        subject: long-topic-name-com.adevinta.schemas.v1.Example
```

With the configuration above, you can execute the following command :

```bash
zoe -v --cluster my-cluster \
    topics produce \
    --from-file data.json \
    --topic my-topic \
    --key-path 'id' 
```

The command above would do the following :

- It fetches the Avro schema registered under the subject name `long-topic-name-com.adevinta.schemas.v1.Example` in the schema registry instance pointed at by the `registry` property in the configuration.
- It uses the schema to transform the json data in `data.json` into Avro `GenericRecord` instances.
- Writes the generic records into `long-name-topic` (aliased by `my-topic`).

## Interacting with the registry

You can also interact with the schema registry using the `zoe schemas` command. Here is a non exhaustive list  of what you can do :

- List avro schemas registered into the registry.
- Describe a specific Avro schema.
- Deploy an Avro schema into the registry from an `.avsc` or `.avdl` file.
- Delete a specific Avro schema or subject from the registry.

## Where to go from here

- There is a hands on tutorial on using zoe with Avro at : [guides/avro](https://github.com/adevinta/zoe/tree/master/docs/guides/avro). 
- Learn more about interacting with the registry with : `zoe schemas --help`
