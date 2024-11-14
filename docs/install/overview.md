# Install Zoe CLI

There are several ways to install zoe depending on your needs and the operation system you are using:

- [Platform package install](package.md): Self contained platform packages (`.deb`, `.rpm`, etc.) built using the new [jpackage](https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html) utility. These packages contain all the dependencies required including the java virtual machine and do not require the host machine to have its own JDK. This is the easiest way to install zoe (if your platform is supported).
- [Homebrew](homebrew.md): Zoe can also be installed using Homebrew on MacOs and Linux environments. Installed packages do not contain the JDK though, so the host machine is required to have a JDK 21 or higher.
- [Manual tarball install](tarball.md): If none of the above options are available for you, Zoe provides tarballs ready to be used in all platforms (including Windows). The host machine is required to have a JDK 21 or higher.
- [Docker](docker.md): Zoe can also be used with docker. We provide ready to use images automatically built for each release.
