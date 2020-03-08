# Zoe runners

When using commands like `zoe consume`, zoe has the ability to run the Kafka consumers remotely in kubernetes pods and seamlessly get the responses back to the CLI. It can do the same with all the other commands that require interaction with Kafka. This behavior is possible thanks to Zoe's concept of runners.

Zoe currently supports 3 runners :

- [Local runner](local.md): Launches the consumers / producers on the same JVM process as the zoe CLI.
- [Kubernetes runner](kubernetes.md): Launches the consumers / producers as pods on a remote kubernetes cluster.
- [Lambda runner](lambda.md): Launches the consumers / producers on a pre-deployed lambda functions. Zoe can deploy this lambda itself.
 
A specific runner can be selected using the `--runner` or `-r` option :

```bash tab="command"
zoe -c my-cluster --runner kubernetes topics consume input -n 5
```

```json tab="output"
{"_id":"5b199196ce456e001424256a","text":"Cats can distinguish different flavors in water.","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":6,"userUpvoted":null}
{"_id":"5b1b411d841d9700146158d9","text":"The Egyptian Mau’s name is derived from the Middle...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":5,"userUpvoted":null}
{"_id":"591d9b2f227c1a0020d26823","text":"Every year, nearly four million cats are eaten in ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"59951d5ef2db18002031693c","text":"America’s cats, including housecats that adventure...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
{"_id":"5a4d76916ef087002174c28b","text":"A cat’s nose pad is ridged with a unique pattern, ...","type":"cat","user":{"_id":"5a9ac18c7478810ea6c06381","name":{"first":"Alex","last":"Wohlbruck"}},"upvotes":4,"userUpvoted":null}
```

The command above offloads the consumption of the topic to a kubernetes pod. The result will seamlessly be output by the CLI as if the runner was local.

Runners configuration is set in zoe's configuration file in the `runners` section:

```yaml
runners:
  default: lambda
  config:
    kubernetes:
      context: mu-kube-context
      namespace: env-staging
    lambda:
      deploy:
        subnets: ["subnet-xxxxxxx"]
        securityGroups: ["sg-yyyyyyy"]
        memory: 512
        timeout: 500
      credentials:
        type: "profile"
        name: "spt-tranquility-pre"
      awsRegion: eu-west-1
```
