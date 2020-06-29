# Managing configuration

Zoe has a powerful configuration engine that can be overridden and customized in a number of ways. Zoe also makes it easy to share configuration among the team using a github repository.

In this guide, we will discover the different ways to configure zoe.

## Initializing the configuration

Zoe stores its configuration in `~/.zoe/config` by default. The directory location can be overridden with the environment variable `ZOE_CONFIG_DIR` or by using the option `zoe --config-dir /path/to/config`.

Add a file named `default.yml` inside `~/.zoe/config` like the following:

```yaml tab="~/.zoe/config/default.yml"
clusters:

  cluster1:
    props:
      bootstrap.servers: "cluster1:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

  cluster2:
    props:
      bootstrap.servers: "cluster2:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

runners:
  default: "local"
```

The configuration above defines two Kafka clusters aliased `cluster1` and `cluster2`. To refer to a specific cluster when using zoe, you can use the `--cluster` option or the short version `-c`:

```bash tab="command"
# To target cluster1
zoe -c cluster1 topics list

# To target cluster2
zoe -c cluster2 topics list
```

## Managing multiple environments

If you are dealing with multiple environments, you can create multiple yaml configuration files in `~/.zoe/config` as the following:

```text tab="~/.zoe/config"
~/.zoe/config
├── default.yml
└── prod.yml
```

```yaml tab="~/.zoe/config/default.yml"
clusters:

  cluster1:
    props:
      bootstrap.servers: "cluster1:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

  cluster2:
    props:
      bootstrap.servers: "cluster2:29092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

runners:
  default: "local"
```

```yaml tab="~/.zoe/config/prod.yml"
clusters:

  cluster1:
    props:
      bootstrap.servers: "cluster1-prod.example.com:9092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

  cluster2:
    props:
      bootstrap.servers: "cluster2-prod.example.com:9092"
      key.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      value.deserializer: "org.apache.kafka.common.serialization.StringDeserializer"
      key.serializer: "org.apache.kafka.common.serialization.StringSerializer"
      value.serializer: "org.apache.kafka.common.serialization.ByteArraySerializer"

runners:
  default: "kubernetes"
```

The example above defines two environments: `default` and `prod`. Environments can be specified using the `--environment` or `-e` option and giving it the environment name which is the file name without the extension:

```bash tab="command"
# To target cluster1 in the prod environment
zoe -e prod -c cluster1 topics list

# To target cluster2 in the default environment
zoe -e default -c cluster2 topics list
```

When no environment is specified with `-e`, the environment named `default` is used. Thus, the last command could have been written as the following:

```bash tab="command"
# To target cluster2 in the default environment
zoe -c cluster2 topics list
```

## The special common.yml file

You can store configuration values that are shared across all the environments in a file called `common.yml` inside zoe's configuration directory. The configuration in this file will always be loaded no matter what environment has been specified.

```yaml tab="~/.zoe/config/common.yml"
secrets:
  provider: "strongbox"
  region: "eu-west-1"

runners:
  default: lambda 
```

The example above sets the [secrets' provider](https://adevinta.github.io/zoe/advanced/secrets/overview/) configuration and the default runner in the shared `common.yml` file. This way, these parameters are loaded for all the environments. 

The environment specific configuration values takes precedence over the `common.yml` configuration values. To know more about zoe's configuration loading chain, checkout [the dedicated section in the documentation](../../configuration/chain.md).

## Sharing configuration in git

The above configuration tree can be shared across the whole team in a git repository. This allows the configuration to be written once, and used by all the team members.

If your configuration contains credentials (ex. `sasl.jaas.config` property), you can use a [secret provider](https://adevinta.github.io/zoe/advanced/secrets/overview/) to describe the secret in the configuration without exposing its value.

Zoe can easily fetch a remote configuration directory from git using the following command:

```bash tab="command"
zoe config init --from git --url https://github.com/adevinta/zoe --dir docs/guides/simple/config
```

## Overriding configuration values using environment variables

You can override part of zoe's configuration using an environment variable called `ZOE_CONFIG_OVERRIDE`. This variable takes a json that will be **shallow** merged into the final configuration.

The example below overrides the `bootstrap.servers` property of `cluster1`:

```bash
export ZOE_CONFIG_OVERRIDE={"clusters": {"cluster1": {"props": {"bootstrap.servers": "custom.endpoint:9092"}}}}
zoe -c cluster1 topics list
```

Zoe provides other environment variables to override environment specific configuration values.  To know more, checkout [the dedicated section about zoe's configuration loading chain](../../configuration/chain.md).
