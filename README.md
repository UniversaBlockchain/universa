# Universa

This is the Universa network codebase (Java core), containing the node, the console client (`uniclient`) and the associated subsystems.

## Build Dependencies

To build, you need to have the following installed:

- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (not tested with JDK 9).
- [Gradle](https://gradle.org/install).

### Build Dependencies: Debian/Ubuntu Linux

(Tested with Ubuntu 16.04 LTS).

First, install `java-package` (the tool that will help you to pack the Oracle JDK tarball into the .deb package). Also, install Gradle (the Java build tool using in Universa).

    sudo apt-get install -y java-package gradle

Visit the [Oracle JDK 8 site](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and download the tarball (`.tar.gz`) package for Linux x64 (assuming you run it on one). You will get a file like `jdk-8u152-linux-x64.tar.gz` (the title may vary, depending on the version).

Using the `java-package` framework, convert the tarball into the .deb package (replace the `jdk-8u152-linux-x64.tar.gz` with your specific name, if needed).

    make-jpkg jdk-8u152-linux-x64.tar.gz

It will create a .deb package named accordingly. Install it (replace the `oracle-java8-jdk_8u152_amd64.deb` with your specific name, if needed).

    sudo dpkg -i oracle-java8-jdk_8u152_amd64.deb

You may also need to configure your Gradle environment to refer to the just-installed JDK:

    mkdir -p ~/.gradle
    cat <<EOT > ~/.gradle/gradle.properties
    org.gradle.java.home=/usr/lib/jvm/oracle-java8-jdk-amd64
    EOT

Now your Linux environment is ready to use Java and Gradle to build Universa.

## How to build Universa

Create a directory where the project will be placed, and, inside it, run the following commands:

    git clone git@github.com:UniversaBlockchain/universa.git ./
    git submodule init; git submodule update
    gradle fatJar

To successfully compile it, you may need [GNU Multiple Precision Arithmetic Library](http://gmplib.org/) (“`libgmp`”) installed. See the details specific to your operating system regarding how it can be installed.

Now you can launch Universa components.

Launching the node:

    java -jar universa_core/build/libs/universa_core-all-1.0-SNAPSHOT.jar

Launching uniclient (CLI interface):

    java -jar uniclient/build/libs/uniclient-all-1.0-SNAPSHOT.jar
