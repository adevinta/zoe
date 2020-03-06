# Local runner

The local runner launches the consumer / producers in the same JVM process as the zoe CLI.

This should be used when consuming from a local kafka cluster. It can also be used against a remote kafka cluster if it's reachable from the local machine.

The local runner can be selected using :

```bash
zoe -c my-cluster -r local topics consume input -n 10
```  
