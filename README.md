# Universa

This is the Universa network codebase (Java core), containing the node, the console client (`uniclient`) and the associated subsystems.

## How to build

To build, you need to have the following installed:

- [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (not tested with JDK 9).
- [Gradle](https://gradle.org/install).

Create a directory where the project will be placed, and run the following commands:

    git clone git@github.com:UniversaBlockchain/universa.git ./
    git submodule init; git submodule update
    gradle build -x test

To successfully compile it, you may need [GNU Multiple Precision Arithmetic Library](http://gmplib.org/) ("libgmp") installed. See the details specific to your operating system regarding how it can be installed.
