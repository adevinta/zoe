# Zoe : The Kafka companion

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) **due to its ability to offload consumption and execution to kubernetes pods or lambda functions** (more runners will be supported in the future).

## Status

Zoe has been open sourced very recently and is not GA yet. It is actively being improved towards stabilization. Documentation is also in progress. That said, we are already using it at Adevinta and you can already start trying it if you are not afraid of digging into the code to solve some eventual undocumented problems :) . 

## Key features

Here are some of the most interesting features of zoe :

- Consume kafka topics from a specific point in time (ex. using `--from 'PT5h` from the last 5 hours).
- Filter data based on content (ex. using `--filter "id == '12345'"` filters records with the selected id).
- Supports offloading consumption of data to multiple lambda functions, kubernetes pods, etc. for parallelism (ex. adding `-x kubernetes` would offload all the requests to a configured kubernetes cluster).
- Monitor consumer groups' offsets.
- Upload avro schemas from a `.avsc` or `.avdl` file using different naming strategies.
- ... and more.

Go to the install section for instructions on how to install the zoe CLI.
