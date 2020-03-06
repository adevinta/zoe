# Consuming data

In this step, we will learn more about the `consume` command by consuming the data produced during the previous section.

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

### Controlling the time range

By default, zoe outputs 5 records from the last hour. We can control the number of output records (`-n`) and the time range (`--from`). For example, to get only 2 records starting the consumption from the last 6 hours :

```bash tab="command"
zoe -v --cluster local topics consume input -n 2 --from 'PT6h'
```

```json tab="output"
{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
```

The `--from` option takes an [ISO-8601 duration format](https://en.wikipedia.org/wiki/ISO_8601#Durations).

### Selecting a subset of the fields

We can select a subset of the output fields by using `--query` option and giving it a [jmespath expression](https://jmespath.org/) that runs against each message read. The result of this expression will be the data output by zoe :

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

We can display the result in a nicely formatted table by using the `--output table` option. Let's use the previous command as an example :

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

### Filtering data based on content

Zoe can also use Jmespath expressions that return a boolean against each read message from kafka and use the result to filter the returned data. This can be used to perform searches into kafka topics. This is one of the most interesting features of Zoe. When combined with remote runners (ex. `--runner kubernetes`) and parallel execution (`--jobs 20` to spin up 20 pods), we can perform expensive searches in large topics in a relatively short amount of time. You can learn more about runners and parallel execution in the advanced section of the documentation.

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

If we are dealing with a large topic and want to search for seven days of data, we can offload the consumption to kubernetes and spin up 25 pods to consume data in parallel using the following command :

```bash tab="command"
zoe --cluster my-production-cluster \
    --runner kubernetes \
    topics consume input \
    --from 'P7d' \
    --filter "user.name.first == 'Kasimir'" \
    --jobs 25
```

This command will not work as is on your computer at this stage because this requires additional configuration to zoe related to the target kubernetes cluster. But there is a tutorial available in this documentation to try out zoe with a kubernetes cluster using Minikube.
