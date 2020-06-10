# Platform package install (recommended)

You can install zoe using one of the platform packages listed below. Only few platforms are supported for now but more will come in the future. 

The platform packages are self contained. They ship with their own version of the java virtual machine (thus the higher size of the package). The host machine does not need to have it's own java runtime.

The platform packages are built with [jpackage](https://jdk.java.net/jpackage/) and JDK 14. 

## Ubuntu / Debian

1. Download the `.deb` package from the [latest release page](https://github.com/adevinta/zoe/releases/latest) and install it using `dpkg`:

    ```bash
    ZOE_VERSION='0.19.0'  # change it to the suitable version
    curl -L "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe_${ZOE_VERSION}-1_amd64.deb" -o /tmp/zoe.deb
    sudo dpkg -i /tmp/zoe.deb
    ```

2. Add the `/opt/zoe/bin` to your path by appending the following line in your `.bashrc` (or `.zshrc`) :

    ```
    PATH=$PATH:/opt/zoe/bin
    ```

3. You can now initialize zoe configuration :

    ```bash
    zoe -v config init
    ```

You are now ready to use zoe. Go to the [Getting started](../basics/overview.md) section.

## Centos

1. Install the latest `.rpm` package from the [latest release page](https://github.com/adevinta/zoe/releases/latest) :

    ```bash
    ZOE_VERSION=0.19.0  # change it to the suitable version
    sudo rpm -i "https://github.com/adevinta/zoe/releases/download/v${ZOE_VERSION}/zoe-${ZOE_VERSION}-1.x86_64.rpm"
    ```

2. Add the `/opt/zoe/bin` into your path by appending the following line in your `.bashrc` (or `.zshrc`) :

    ```
    PATH=$PATH:/opt/zoe/bin
    ```

3. You can now initialize zoe configuration :

    ```bash
    zoe -v config init
    ```

You are now ready to use zoe. Go to the [Getting started](../basics/overview.md) section.
