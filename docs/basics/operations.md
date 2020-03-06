# Operations

Zoe can do much more than just producing and consuming data. In this section, you will find examples of some additional capabilities of zoe.

## Interacting with consumer groups

Fetch the offsets of a consumer group :

```bash tab="command"
zoe -o table -v -c my-cluster groups offsets my-consumer-group-name
```

```text tab="output"
┌──────────┬───────────┬───────────────┬───────────┬─────┬──────────┐
│ topic    │ partition │ currentOffset │ endOffset │ lag │ metadata │
├──────────┼───────────┼───────────────┼───────────┼─────┼──────────┤
│ my-topic │ 3         │ 49993793      │ 49993793  │ 0   │          │
├──────────┼───────────┼───────────────┼───────────┼─────┼──────────┤
│ my-topic │ 4         │ 50275990      │ 50275991  │ 1   │          │
├──────────┼───────────┼───────────────┼───────────┼─────┼──────────┤
│ my-topic │ 1         │ 50155761      │ 50155762  │ 1   │          │
├──────────┼───────────┼───────────────┼───────────┼─────┼──────────┤
│ my-topic │ 2         │ 50212574      │ 50212574  │ 0   │          │
├──────────┼───────────┼───────────────┼───────────┼─────┼──────────┤
│ my-topic │ 0         │ 50027346      │ 50027347  │ 1   │          │
└──────────┴───────────┴───────────────┴───────────┴─────┴──────────┘
```

Consumer groups can also be given aliases in zoe's configuration. This is done in the `groups` section inside the cluster configuration. Refer to the [configuration reference](../configuration/reference.md) section.

Use `zoe groups --help` for additional commands and examples to interact with consumer groups.

## Interacting with the Avro Schema Registry

List avro schemas :

```bash tab="command"
zoe -o table -v -c my-cluster schemas list
```

```text tab="output"
┌───────────────────┐
│ subjects          │
├───────────────────┤
│ input-topic-value │
│ input-topic-key   │
└───────────────────┘
```

Describe a schema :

```bash tab="command"
zoe -o json -v -c my-cluster schemas describe input-topic-value
```

```json tab="output"
{
  "subject": "input-topic-value",
  "versions": [2],
  "latest": "{\"type\":\"record\",\"name\":\"my-schema\",\"namespace\":\"com.adevinta.example\",\"fields\":[...]}"
}
```

Deploy avro schema using an `.avdl` file :

```bash tab="command"
zoe -c my-cluster \
    schemas deploy \
    --avdl \
    --from-file schema.avdl \
    --name ModuleReason \
    --strategy topicRecord --topic input
```

Use `zoe schemas --help` for additional commands and help on interacting with schemas.
