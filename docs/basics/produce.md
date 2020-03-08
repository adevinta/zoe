# Producing data

In this step, we are going to use zoe to write some json data into Kafka.

## Prerequisites

- You should have completed the previous step : [Prepare the environment](prepare.md)

## Producing data

There are multiple ways to write json data to a Kafka cluster using zoe. 

### Write data from stdin

The simplest way is to inject the data from stdin :

```bash tab="command"
echo '[{"msg": "hello world"}]' | zoe -v --cluster local topics produce --topic input --from-stdin
```

```json tab="output"
{
    "produced": [
        {
            "offset": 2,
            "partition": 0,
            "topic": "input-topic",
            "timestamp": 1583483283555
        }
    ],
    "skipped": []
}
```

```text tab="logs"
2020-03-06 09:28:02 INFO zoe: loading config from url : file: ~/.zoe/config/default.yml
2020-03-06 09:28:03 INFO zoe: producing '1' records to topic 'input-topic'
2020-03-06 09:28:03 INFO AppInfoParser: Kafka version: 2.3.1
2020-03-06 09:28:03 INFO AppInfoParser: Kafka commitId: 18a913733fb71c01
2020-03-06 09:28:03 INFO AppInfoParser: Kafka startTimeMs: 1583483283410
2020-03-06 09:28:03 INFO Metadata: [Producer clientId=producer-1] Cluster ID: y50BSUG2RzipAYoKITJuQg
2020-03-06 09:28:03 INFO KafkaProducer: [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
```

Zoe expects a valid json array as input from stdin. It iterates over the elements and writes them one by one to the target topic: the topic aliased `input` in this case. Remember that the alias `input` was given to the `input-topic` in zoe's configuration in the [previous section](../prepare/#configuring-zoe).
  
Now, we can check the data we have just written by using the `consume` command (we will learn more about the `consume` command in the next section) :

```bash tab="input"
zoe -v --cluster local topics consume input -n 1
```

```json tab="output"
{"msg": "hello world"}
```

We can have more information about the message key, offset and timestamp by adding the `--verbose` flag 

```bash tab="input"
zoe -v --cluster local topics consume input -n 1 --verbose
```

```json tab="output"
{
    "key": "2d57d220-7340-4c0c-ab66-fd51c0157cae",
    "offset": 2,
    "timestamp": 1583483283555,
    "partition": 0,
    "topic": "input-topic",
    "formatted": {
        "msg": "hello world"
    }
}
```

Notice that the message key is a generated UUID. By default, zoe generates UUID keys when writing messages into kafka. We can make it instead pick the key from a field in the message with the `--key-path` option:

```bash tab="command"
echo '[{"id": "1", "msg": "hello world"}]' | zoe -v --cluster local topics produce --topic input --from-stdin --key-path 'id'
```

```json tab="output"
{
    "produced": [
        {
            "offset": 3,
            "partition": 0,
            "topic": "input-topic",
            "timestamp": 1583488726267
        }
    ],
    "skipped": []
}
```

The key path option argument is a [Jmespath](https://jmespath.org/) expression that is executed against each input message. The result of the expression will be used as the record's key. It is also possible to do the same to set the messages timestamp with the `--ts-path`. Use `zoe topics produce --help` for more information.

### Write data from a json file

The other way to write data into Kafka with zoe is to use a json file.

The following example uses a sample dataset downloaded from the [cats facts API](https://cat-fact.herokuapp.com/#/cat/facts). You can inspect the sample in the repository at [tutorials/simple/data.json](https://github.com/adevinta/zoe/blob/master/tutorials/simple/data.json).

Let's write this json file into the `input` topic.

```bash tab="command"
zoe -v topics produce --topic input --from-file tutorials/simple/data.json
```

```text tab="logs"
2020-03-06 11:07:18 INFO zoe: loading config from url : file:~/.zoe/config/default.yml
2020-03-06 11:07:18 INFO zoe: producing '212' records to topic 'input-topic'
2020-03-06 11:07:18 INFO AppInfoParser: Kafka version: 2.3.1
2020-03-06 11:07:18 INFO AppInfoParser: Kafka commitId: 18a913733fb71c01
2020-03-06 11:07:18 INFO AppInfoParser: Kafka startTimeMs: 1583489238910
2020-03-06 11:07:19 INFO Metadata: [Producer clientId=producer-1] Cluster ID: y50BSUG2RzipAYoKITJuQg
2020-03-06 11:07:19 INFO KafkaProducer: [Producer clientId=producer-1] Closing the Kafka producer with timeoutMillis = 9223372036854775807 ms.
```

```json tab="output"
{
    "produced": [
        {
            "offset": 4,
            "partition": 0,
            "topic": "input-topic",
            "timestamp": 1583489239047
        },
        {
            "offset": 5,
            "partition": 0,
            "topic": "input-topic",
            "timestamp": 1583489239054
        },
        ...
    ],
    "skipped": []
}
```

You can know more about the `produce` command with `zoe topics produce --help`. 

## Writing data in streaming mode

In all the examples above, zoe was expecting a valid json array as input. This is because zoe parses the input data (from stdin or from a file) entirely before sending it to Kafka. This is the default behavior of the `produce` command.

This behavior is not suitable when streaming data to the `zoe produce` command from a continuous data source (ex. a tcp connection or an http command) because:

- The data is usually not formatted as a Json Array. It's often rather a json per line.
- We do not want zoe to buffer all the data internally before sending it.

For these cases, Zoe supports a streaming mode by setting a `--streaming` flag. In this mode :

- Zoe expects one valid json object per line (separated by line breaks)
- Zoe will keep listening to the input stream continuously. It needs to be stopped using a stop signal (`Ctrl-C`).

Streaming mode can be enabled like the following :

```bash tab="command"
echo '{"msg": "hello world"}' | zoe -v --cluster local topics produce --topic input --from-stdin --streaming
```
