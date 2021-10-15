# Consuming data

In this step, we will learn more about the `consume` command by reading the data produced during the previous section.

## Prerequisites

You should have completed the two previous steps :

- [Prepare the environment](prepare.md)
- [Producing data](produce.md)

## Consuming data

To consume data from the `input` topic (alias of the `input-topic`), use the following command :

```bash tab="command"
zoe -v --cluster local topics consume input
```

```json tab="output"
{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
{"_id":"591d9b2f227c1a0020d26823","text":"Every year, nearly four million cats are eaten in ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"5a4d76916ef087002174c28b","text":"A cat’s nose pad is ridged with a unique pattern, ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
```

By default, zoe consumes 5 records starting from the last hour.

## Displaying records metadata

To display the records' metadata (record headers, key, offset, timestamp, partition, topic), use the `--expose-metadata`
option to make zoe inject records metadata in a special field named `__metadata__` by default.

```bash tab="command"
zoe -v --cluster local topics consume input -n 5 --expose-metadata
```

```json tab="output"
{"__metadata__": {"key":"5b199196ce456e001424256a", "offset":1, "timestamp":1596700800645, "partition":0, "topic":"input","headers":{"traceId":"3b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"__metadata__": {"key":"5b1b411d841d9700146158d9", "offset":2, "timestamp":2596700800645, "partition":0, "topic":"input","headers":{"traceId":"4b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
{"__metadata__": {"key":"591d9b2f227c1a0020d26823", "offset":3, "timestamp":3596700800645, "partition":0, "topic":"input","headers":{"traceId":"5b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"591d9b2f227c1a0020d26823","text":"Every year, nearly four million cats are eaten in ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"__metadata__": {"key":"59951d5ef2db18002031693c", "offset":1, "timestamp":4596700800645, "partition":1, "topic":"input","headers":{"traceId":"6b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"__metadata__": {"key":"5a4d76916ef087002174c28b", "offset":2, "timestamp":5596700800645, "partition":1, "topic":"input","headers":{"traceId":"7b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"5a4d76916ef087002174c28b","text":"A cat’s nose pad is ridged with a unique pattern, ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
```

### Controlling the time range

We can control the number of output records (`-n`) and the starting time of the consumption (`--from`).

For example, to start the consumption from the last 6 hours and read only 2 records:

```bash tab="command"
zoe -v --cluster local topics consume input -n 2 --from 'PT6h'
```

```json tab="output"
{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
```

The `--from` option takes a duration in [ISO-8601 format](https://en.wikipedia.org/wiki/ISO_8601#Durations).

### Selecting a subset of the fields

We can format the output rows by using the `--query` option and giving it a [jmespath expression](https://jmespath.org/)
. Zoe will run this Jmespath expression against each message and the result will be output instead of the original
message itself. A typical use case is when we want only a subset of the existing fields:

```bash tab="command"
zoe -v --cluster local \
       topics consume input \
       --query '{id: _id, text: text, user: user.name}'
```

```json tab="output"
{"id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","user":{"first":"Alex","last":"Wohlbruck"}}
{"id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","user":{"first":"Alex","last":"Wohlbruck"}}
{"id":"591d9b2f227c1a0020d26823","text":"Every year, nearly four million cats are eaten in ...","user":{"first":"Alex","last":"Wohlbruck"}}
{"id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","user":{"first":"Alex","last":"Wohlbruck"}}
{"id":"5a4d76916ef087002174c28b","text":"A cat’s nose pad is ridged with a unique pattern, ...","user":{"first":"Alex","last":"Wohlbruck"}}
```

### Pretty display

Zoe can display the consumed data in a nicely formatted table by using the `--output table` option:

```bash tab="command"
zoe -v --cluster local \
       --output table \
       topics consume input \
       --query '{id: _id, text: text, user: user.name}'
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬───────────────────┐
│ id                       │ text                                                  │ user              │
├──────────────────────────┼───────────────────────────────────────────────────────┼───────────────────┤
│ 5b199196ce456e001424256a │ Cats can distinguish different flavors in water.      │ first: "Alex"     │
│                          │                                                       │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────┼───────────────────┤
│ 5b1b411d841d9700146158d9 │ The Egyptian Mau’s name is derived from the Middle... │ first: "Alex"     │
│                          │                                                       │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────┼───────────────────┤
│ 591d9b2f227c1a0020d26823 │ Every year, nearly four million cats are eaten in ... │ first: "Alex"     │
│                          │                                                       │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────┼───────────────────┤
│ 59951d5ef2db18002031693c │ America’s cats, including housecats that adventure... │ first: "Alex"     │
│                          │                                                       │ last: "Wohlbruck" │
├──────────────────────────┼───────────────────────────────────────────────────────┼───────────────────┤
│ 5a4d76916ef087002174c28b │ A cat’s nose pad is ridged with a unique pattern, ... │ first: "Alex"     │
│                          │                                                       │ last: "Wohlbruck" │
└──────────────────────────┴───────────────────────────────────────────────────────┴───────────────────┘
```

We can also use the `--output json` to output a valid json instead of a json per row :

```bash tab="command"
zoe -v --cluster local \
       --output json \
       topics consume input \
       --query '{id: _id, text: text, user: user.name}' \
       -n 2
```

```json tab="output"
[
    {
        "id": "5b199196ce456e001424256a",
        "text": "Cats can distinguish different flavors in water.",
        "user": {
            "first": "Alex",
            "last": "Wohlbruck"
        }
    },
    {
        "id": "5b1b411d841d9700146158d9",
        "text": "The Egyptian Mau’s name is derived from the Middle...",
        "user": {
            "first": "Alex",
            "last": "Wohlbruck"
        }
    }
]
```

These display options are not only availabe for the `consume`. They are available for all the zoe commands. In fact, Zoe
can consistently display all its output as a table.

### Filtering data based on content

Zoe can also use [Jmespath expressions](https://jmespath.org/) that return a boolean to filter the output messages. Zoe
runs this expression against each message and depending on the boolean result, zoe will discard the message or not.

This feature can be used to perform searches into Kafka topics. It is one of the most interesting features of Zoe. When
combined with remote runners (ex. `--runner kubernetes`) and parallel execution (`--jobs 20` to spin up 20 pods), we can
perform expensive searches in large topics in a relatively short amount of time. You can learn more about runners and
parallel execution in the advanced section of the documentation.

Filters are enabled with the `--filter` option. For example, to read only Kasimir's cat facts :

```bash tab="command"
zoe -v --cluster local \
    topics consume input \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'" \
    --query '{user: user.name, text: text}'
```

```json tab="output"
{"user":{"first":"Kasimir","last":"Schulz"},"text":"Cats are the most popular pet in the United States..."}
{"user":{"first":"Kasimir","last":"Schulz"},"text":"In tigers and tabbies, the middle of the tongue is..."}
{"user":{"first":"Kasimir","last":"Schulz"},"text":"A cat can jump up to six times its length."}
{"user":{"first":"Kasimir","last":"Schulz"},"text":"There are cats who have survived falls from over 3..."}
{"user":{"first":"Kasimir","last":"Schulz"},"text":"The technical term for \"hairball\" is \"bezoar.\""}
```

If we are dealing with a large topic and want to search for seven days of data, we can offload the consumption to
kubernetes and spin up 25 pods to consume data in parallel using the following command :

```bash tab="command"
zoe --cluster my-production-cluster \
    --runner kubernetes \
    topics consume input \
    --from 'P7d' \
    --filter "user.name.first == 'Kasimir'" \
    --jobs 25
```

This command will not work as is on your computer at this stage because this requires additional work to configure
access to a kubernetes cluster with zoe. But there is a tutorial available in this documentation to try out zoe with a
kubernetes cluster using Minikube.

### Filtering data based on metadata

Zoe by default exposes only records content. You can use the `--expose-metadata` flag to expose the record's metadata as
well in a special field maned `__metadata__` by default. This field can be accessed as any other field in the `--filter`
expressions to filter records based on metadata content, as well as `--query` expressions.

The example below shows how to filter records on a given offset and partition:

```bash tab="command"
zoe -v --cluster local topics consume input --expose-metadata --filter "__metadata__.offset == \`1\` && __metadata__.partition == \`1\`"
```

```json tab="output"
{"__metadata__": {"key":"59951d5ef2db18002031693c", "offset":1, "timestamp":4596700800645, "partition":1, "topic":"input","headers":{"traceId":"6b3ae7fa-2a8b-494b-a81c-1c759a479867"}},"content":{"_id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
```

The name of the injected metadata field can be modified using `--metadataf-field-alias` option.

Record headers can also be used for filtering. The following command show an example of filtering records based on
header values:

```bash tab="command"
zoe --cluster local topics consume input \
    --expose-metadata --metadata-field-alias 'meta' \
    --filter "meta.headers.traceId == '123123'"
```
