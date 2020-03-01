# Zoe : The missing companion for Kafka

Zoe is a command line tool to interact with kafka in an easy and intuitive way. Wanna see this in action ? check out this demo...

[![demo](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No.svg)](https://asciinema.org/a/vSDNcUUaMMBkWxCSDD8u3s0No?speed=2.5&rows=35)

Zoe really shines when it comes to interacting with cloud hosted kafka clusters (kubernetes, AWS, etc.) due to its ability to offload consumption and execution to kubernetes pods or lambda functions (more runners will be supported in the future).

## Status

Zoe is not GA yet. It has been open sourced very recently and is actively being improved toward stabilization. Documentation is also still to be done. That said, we are already using it at Adevinta and you can already start trying it if you are not afraid of digging into the code to solve some eventual undocumented problems :) . 

## Key features

Here are some of the most interesting features of zoe :

- Consume kafka topics from a specific point in time (ex. from the last hour).
- Filter data based on content (ex. filter events with `id == '12345'`).
- Supports offloading consumption of data to multiple lambda functions, kubernetes pods, etc. for parallelism.
- Monitor consumer groups' offsets.
- Upload avro schemas from a .avsc or .avdl file using different naming strategies.
- ... and more. 

## Install

### Tarball install (requires a JDK on the host machine)

Java 11 or higher is required in order to install the runtimeless packages. If you don't have java installed already, you can use [sdkman](https://sdkman.io/) to easily install it. If you don't want to install java you can try one of the platform packages provided below if your platform is supported.

Once java is installed, proceed with the following steps :

1. Download the runtime-less zip or tar package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and uncompress it into your home directory (or wherever you wish)
```
ZOE_VERSION=0.2.2  # change it to the suitable version
curl -L "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe-${ZOE_VERSION}.tar.gz" | tar -zx -C $HOME
```
2. Add the `$HOME/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:$HOME/zoe/bin
``` 
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

### Platform package install (Experimental - does not require an already installed JDK)

The following packages are self contained. They ship with their own version of java virtual machine (thus the higher size of the package). The host machine does not need to have any java runtime installed already.

The packages are built with [jpackage](https://jdk.java.net/jpackage/). Only few plateforms are supported for now but more will be supported in the future. 

#### Ubuntu / Debian

1. Download the runtime-full `.deb` package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and install it using `dpkg` :
```
ZOE_VERSION=0.2.2  # change it to the suitable version
curl -L "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe_${ZOE_VERSION}-1_amd64.deb" -o /tmp/zoe.deb
sudo dpkg -i /tmp/zoe.deb
```
2. Add the `/opt/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:/opt/zoe/bin
```
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

#### Centos

1. Download the runtime-full `.deb` package of the zoe CLI from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and install it using `dpkg` :
```
ZOE_VERSION=0.2.2  # change it to the suitable version
sudo rpm -i "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe-${ZOE_VERSION}-1.x86_64.rpm"
```
2. Add the `/opt/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :
```
PATH=$PATH:/opt/zoe/bin
```
3. Init zoe configuration :
```bash
zoe config init
```
4. You are now ready to use zoe. Go to the `./tutorials` folder to start learning zoe.

## Documentation

Available soon...

## Build from source

### Requirements
To build and deploy :
- java 11 or later (install with the awesome [sdkman](https://sdkman.io/)) 

### Build zoe cli

```bash
# switch to java 11 or later
# if you are using sdkman
sdk use java 11

# build zoe CLI
./gradlew clean zoe-cli:installShadowDist

# launch zoe cli
zoe-cli/build/install/zoe-cli-shadow/bin/zoe --help

# if you don't have any config yet
zoe-cli/build/install/zoe-cli-shadow/bin/zoe config init
```

## Auto completion (optional)
```bash
_ZOE_COMPLETE=bash java -cp zoe-cli/build/libs/zoe-cli-final.jar com.adevinta.oss.zoe.cli.MainKt > /tmp/complete.sh
source /tmp/complete.sh
```

## Development

### Testing actions
```bash
docker build -t gh-actions:ubuntu-latest dev/actions/images/ubuntu
act -P ubuntu-latest=gh-actions:ubuntu-latest -r -j release-runtimeless -e dev/actions/payloads/release.json release
```
