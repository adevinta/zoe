# Configuration loading chain

Zoe has a complex and powerful configuration override chain that makes it highly configurable.

Zoe configuration properties are considered in the following order (in a decreasing order of priority):

1. `ZOE_CONFIG_OVERRIDE_{env}` environment variable.
2. `ZOE_CONFIG_OVERRIDE` environment variable.
3. `~/.zoe/config/{env}.yml` or `.json` file
4. `~/.zoe/config/common.yml` or `.json` file

The 2 first environment variables must contain the configuration (or a subset) in json format.

The final zoe configuration will be the result of merging all the above configuration values in the increasing level priority.

The special `common.yml` configuration file is supposed to contain configuration values that are common to all the environments. Registered expressions or secrets providers are a good use case. 

## Environment variables substitutions

It is possible to use environment variables inside the zoe yaml or json configuration files and zoe will lookup those variables at runtime from the environment and does an automatic substitution.

The following example shows the use of an environment variable inside a yaml configuraiton file:

```yaml
clusters:

  my-cluster:
    props:
      bootstrap.servers: ${BOOTSTRAP_SERVER}
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
    registry: ${SCHEMA_REGISTRY:-localhost:8081}

runners:
    default: local
```

With the above configuration, zoe replaces the `${BOOTSTRAP_SERVER}` and `${SCHEMA_REGISTRY}` expressions with the value of the environment variables `BOOTSTRAP_SERVER` and `SCHEMA_REGISTRY` respectively. If the `SCHEMA_REGISTRY` is not found, the specified default value is used (`localhost:8081` in this case). If no default value is specified and no environment variable with the corresponding name exists, the expression is simply not replaced.

The following command should make the previous statement clearer:

```bash tab="command"
BOOTSTRAP_SERVER=localhost:9092 zoe -o table config clusters list
```

```json tab="output"
┌────────────┬────────────────┬────────────────┬────────┬────────┐
│ cluster    │ brokers        │ registry       │ topics │ groups │
├────────────┼────────────────┼────────────────┼────────┼────────┤
│ my-cluster │ localhost:9092 │ localhost:8081 │        │        │
└────────────┴────────────────┴────────────────┴────────┴────────┘
```