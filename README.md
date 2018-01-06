# Universa

This is the Universa network codebase (Java core), containing the node, the console client (`uniclient`) and the associated subsystems.

## Build Dependencies

To build, you need to have the following installed:

- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (not tested with JDK 9).
- [Gradle](https://gradle.org/install).

### Build Dependencies: Debian/Ubuntu Linux

(Tested with Ubuntu 16.04 LTS).

First, install `java-package` (the tool that will help you to pack the Oracle JDK tarball into the .deb package). Also, install Gradle (the Java build tool using in Universa).

    sudo apt-get install java-package gradle

Visit the Oracle JDK 8 site and download the tarball (`.tar.gz`) package for Linux x64. You will get a file like `jdk-8u152-linux-x64.tar.gz` (the title may vary, depending on the version).

Using the java-package framework, convert the tarball into the .deb package (replace the `jdk-8u152-linux-x64.tar.gz` with your specific name, if needed).

    make-jpkg jdk-8u152-linux-x64.tar.gz

It will create a .deb package named accordingly. Install it (replace the `oracle-java8-jdk_8u152_amd64.deb` with your specific name, if needed).

    sudo dpkg -i oracle-java8-jdk_8u152_amd64.deb

Now your Linux environment is ready to use Java and Gradle to build Universa.

## How to build Universa

Create a directory where the project will be placed, and run the following commands:

    git clone git@github.com:UniversaBlockchain/universa.git ./
    git submodule init; git submodule update
    gradle build -x test

To successfully compile it, you may need [GNU Multiple Precision Arithmetic Library](http://gmplib.org/) (“`libgmp`”) installed. See the details specific to your operating system regarding how it can be installed.
