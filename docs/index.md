# Zoe - The Kafka Companion

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) **due to its ability to offload consumption and execution to kubernetes pods or lambda functions** (more runners will be supported in the future).

## Key features

Here are some of the most interesting features of zoe :

- Supports [offloading consumption of data](advanced/runners/overview.md) to lambda functions, kubernetes pods, etc. for parallelism (ex. adding `-r kubernetes` would offload all the requests to multiple pods in a configured kubernetes cluster).
- Consume kafka topics from a specific point in time (ex. using `--from 'PT5h` from the last 5 hours).
- Filter data based on content (ex. using `--filter "id == '12345'"` filters records with the selected id).
- Monitor consumer groups' offsets.
- Upload avro schemas from a `.avsc` or `.avdl` file using different naming strategies.
- ... and more.

Go to the [install section](install/overview.md) for instructions on how to install the zoe CLI.

## Sample commands

Read 10 records from the `input` topic from the `local` kafka cluster (aliases for topics and clusters are set in the configuration) :

```bash tab="command"
zoe --cluster local topics consume -n 10 
```

```json tab="output"
{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
{"_id":"591d9b2f227c1a0020d26823","text":"Every year, nearly four million cats are eaten in ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"5a4d76916ef087002174c28b","text":"A cat’s nose pad is ridged with a unique pattern, ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
```

Read the 10 records starting from the last 6 hours and format the output data as a table :

```bash tab="command"
zoe --cluster local \
    --output table
    topics consume \
    -n 10 \
    --from 'PT6h'
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
│ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 599f89639a11040c4a163440 │ Here is a video of some cats in zero gravity. yout... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 5       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 591d9b2f227c1a0020d26823 │ Every year, nearly four million cats are eaten in ... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 5b3bd7d24cf4e10014bfd199 │ The myth that a cat has nine lives comes from thei... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 3       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 5b453e380fd3a600147f32f3 │ Exposure to UV light with hairless or partially-ha... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 3       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 590b9d90229d260020af0b06 │ Evidence suggests domesticated cats have been arou... │ cat  │ _id: "5a9ac18c7478810ea6c06381"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Alex","last":"Wohlbruck"} │         │             │
└──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
```

Filter only records that belong to `Kasimir` :

```bash tab="command"
zoe --cluster local \
    --output table \
    topics consume \
    -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'"
```

```text tab="output"
┌──────────────────────────┬───────────────────────────────────────────────────────┬──────┬───────────────────────────────────────────┬─────────┬─────────────┐
│ _id                      │ text                                                  │ type │ user                                      │ upvotes │ userUpvoted │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e009390aac31001185ed10 │ Most cats are lactose intolerant, and milk can cau... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e007cc0aac31001185ecf5 │ Cats are the most popular pet in the United States... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e008c50aac31001185ed0e │ The world's richest cat is worth $13 million after... │ cat  │ _id: "58e007480aac31001185ecef"           │ 2       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
├──────────────────────────┼───────────────────────────────────────────────────────┼──────┼───────────────────────────────────────────┼─────────┼─────────────┤
│ 58e009790aac31001185ed14 │ The technical term for "hairball" is "bezoar."        │ cat  │ _id: "58e007480aac31001185ecef"           │ 4       │ null        │
│                          │                                                       │      │ name: {"first":"Kasimir","last":"Schulz"} │         │             │
└──────────────────────────┴───────────────────────────────────────────────────────┴──────┴───────────────────────────────────────────┴─────────┴─────────────┘
```

Do the same search as before but against my production kafka cluster and by offloading the consumption to kubernetes and spinning up 10 pods in parallel :

```bash tab="command"
zoe --cluster my-production-kafka \
    --output table \
    --runner kubernetes \
    topics consume \
    -n 10 \
    --from 'PT6h' \
    --filter "user.name.first == 'Kasimir'" \
    --jobs 10
```

Display the offsets of my consumer group `my-group` :

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

## Status

Zoe has been open sourced very recently and is not GA yet. It is actively being improved towards stabilization. Documentation is also in progress. That said, we are already using it at Adevinta and you can already start trying it if you are not afraid of digging into the code to solve some eventual undocumented problems :) . 

## Maintainers

- Created by: Walid Lezzar ([Github](https://github.com/wlezzar), [Twitter](https://twitter.com/walezz), [LinkedIn](https://www.linkedin.com/in/walid-lezzar/))

### Contributors

Contributor's guide coming soon.