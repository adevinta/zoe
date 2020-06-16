# Install Zoe CLI

There are several ways to install zoe depending on your needs and the operation system you are using:

- [Platform package install](package.md): Self contained platform packages (`.deb`, `.rpm`, etc.). These packages come with their own version of the java virtual machine and do not require the host machine to have its own JDK. This is the easiest and the recommended way to install zoe (if your platform is supported).
- [Homebrew](homebrew.md): Zoe can also be installed using Homebrew in MacOs and Linux environments. Installed packages do not contain the JDK though, so you will need to install it by yourself.
- [Manual tarball install](tarball.md): Supports all platforms. These are simple tarball packages that just contain the Zoe java application without the JDK. The host machine is required to have JDK 11 or higher.
- [Docker](docker.md): Zoe can also be used with docker. We provide built-in images on each release.
