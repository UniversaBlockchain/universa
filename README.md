# Universa

This is the Universa network codebase (Java core), containing the node, the console client (`uniclient`) and the associated subsystems.

## How to build

    git clone git@github.com:UniversaBlockchain/universa.git ./
    git submodule init; git submodule update
    gradle build -x test

To compile it, you may need [GNU Multiple Precision Arithmetic Library](http://gmplib.org/) ("libgmp") installed. See the details specific to your operating system regarding how it can be installed.
