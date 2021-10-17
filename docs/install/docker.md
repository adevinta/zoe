# Using Zoe with Docker

We provide ready to use docker images for the CLI on each version release. This is a good alternative if your OS is not supported for the [platform packages install](package.md) or you don't want to install the JDK on your machine. The drawback however is a slightly higher startup latency.

Zoe CLI can be launched within docker using the following command:

```bash
docker run --rm -v $HOME/zoe-docker:/root/.zoe adevinta/zoe-cli:latest --help
```  

You can also use a release specific image:

```bash
docker run --rm -v $HOME/zoe-docker:/root/.zoe adevinta/zoe-cli:0.26.1 --help
```

## Creating a launcher

If you want to have a native CLI experience, you can wrap the docker run call in a bash script. Here is an example on linux based systems:

=== "~/bin/zoe"

    ```bash
    #!/usr/bin/env bash
    
    IMAGE="adevinta/zoe:latest"
    
    # Use the host user
    DOCKER_RUN="docker container run -i --rm -v $HOME/zoe-docker:/root/.zoe"
    
    # pass zoe specific environment variables
    [[ -n "${ZOE_CLUSTER}" ]] && DOCKER_RUN="${DOCKER_RUN} -e ZOE_CLUSTER=${ZOE_CLUSTER}"
    [[ -n "${ZOE_ENV}" ]] && DOCKER_RUN="${DOCKER_RUN} -e ZOE_ENV=${ZOE_ENV}"
    [[ -n "${ZOE_CONFIG_DIR}" ]] && DOCKER_RUN="${DOCKER_RUN} -e ZOE_CONFIG_DIR=${ZOE_CONFIG_DIR}"
    [[ -n "${ZOE_STACKTRACE}" ]] && DOCKER_RUN="${DOCKER_RUN} -e ZOE_STACKTRACE=${ZOE_STACKTRACE}"
    [[ -n "${_ZOE_COMPLETE}" ]] && DOCKER_RUN="${DOCKER_RUN} -e _ZOE_COMPLETE=${_ZOE_COMPLETE}"
    
    # pass AWS secrets
    if [[ -n "${AWS_SECRET_ACCESS_KEY}" ]]; then
        DOCKER_RUN_ME="${DOCKER_RUN_ME} -e AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}"
        DOCKER_RUN_ME="${DOCKER_RUN_ME} -e AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}"
        DOCKER_RUN_ME="${DOCKER_RUN_ME} -e AWS_SESSION_TOKEN=${AWS_SESSION_TOKEN}"
    fi
    
    exec ${DOCKER_RUN} ${IMAGE} "$@"
    ```

Once this file is created, make it executable:

```bash
chmod +x ~/bin/zoe
```

Make sure that the `~/bin` folder is in your path. If not, you can add it by appending the following line in your `~/.bashrc` (or `~/.zshrc`):
 
```text
PATH="$PATH:~/bin"
```

Restart your session. You should now be able to launch zoe by using:

```bash
zoe --help
```
