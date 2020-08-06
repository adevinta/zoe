# Configuration reference

```yaml
# Clusters definition
clusters:

  # Cluster alias (used with the --cluster option to refer to this cluster config)
  subito:
    
    # Kafka clients properties that will be injected to all Kafka clients by Zoe at runtime
    # For more information on available properties : 
    #   - consumer : https://kafka.apache.org/documentation/#consumerconfigs
    #   - producer : https://kafka.apache.org/documentation/#producerconfigs
    # This section can contain secrets.
    props:
      bootstrap.servers: my-kafka.example.com:9092
      key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value.deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      sasl.jaas.config: secret:JAAS_CONFIG
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-256
    
    # Schema registry address of the cluster. Will be used by zoe when interacting with the schema registry (schemas list, describe and deploy command)
    # When this property is set, zoe automatically injects the property 'schema.registry.url' into the Kafka clients props.
    registry: https://my-registry.example.com
    
    # A list of consumer groups keyed by their alias. Useful when using the 'zoe groups' command to interact with consumer groups.
    # When you need to refer to a consumer group, you can use the alias instead of the full name of the consumer group.
    groups:
      my-group: long-consumer-group-name
    
    # Topics config section. Basically a dictionary with the topic's alias as the key and the topic's configuration as the value.
    # The topic's configuration basically includes :
    #   - name: the full name of the topic
    #   - subject (optional) : the subject name of the avro schema of the topic.
    topics:

      topic-alias:
        name: full-name-of-the-topic-in-the-cluster
        subject: subject-name-of-the-topic
        # Override any kafka client property that is specific to a topic
        propsOverride:
          key.deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value.deserializer: org.apache.kafka.common.serialization.StringDeserializer

# Runners configuration
runners:

  # Default runner to use when the '-r' option is not specified
  default: lambda

  # Runners configuration
  config:
    
    # The kubernetes runner configuration
    kubernetes:
      # Kubernetes context to use
      context: mu-kube-context
      # Namespace to use
      namespace: env-staging
      # Whether to delete the pods after completion
      deletePodAfterCompletion: true
      # Number of CPUs
      cpu: "1"
      # Memory limit
      memory: "512M"
      # Timeout of the commands
      timeoutMs: 300000

    # The lambda runner configuration
    lambda:

      # This section sets the deployment context of the lambda function. Mainly used with the 'zoe lambda deploy' command.
      deploy:
        subnets: ["subnet-xxxxxx"]
        securityGroups: ["sg-yyyyy"]
        memory: 512
        timeout: 500

      # Credentials provider for the lambda client. More credentials type can be provided.
      # Cf. The runners section in The advanced usage guide.
      credentials:
        type: "profile"
        name: "my-profile"

      # AWS Region for the lambda client
      awsRegion: eu-west-1

# Registered expressions section
# Cf. The registered expression guide in the Advanced usage section.
expressions:
  short_id: "ends_with(id, '{{ value }}')"

# Secrets provider
# Cf. The secrets provider guide in the Advanced usage section. 
secrets:
  provider: "strongbox"
  region: "eu-west-1"
  group: my.group

```