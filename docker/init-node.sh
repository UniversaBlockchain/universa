#!/bin/bash
set -e
PRIVATE_DIR="/deploy/tmp"

if ! [ "$(ls -A $PRIVATE_DIR)" ]; then
	java -jar /code/uniclient/build/libs/uniclient.jar -g node-${NODE_INDEX}-local.universa.io
	mv ./node-${NODE_INDEX}-local.universa.io.public.unikey /deploy/config/keys/node-${NODE_INDEX}-local.universa.io.public.unikey
	mv ./node-${NODE_INDEX}-local.universa.io.private.unikey /deploy/tmp/node-${NODE_INDEX}-local.universa.io.private.unikey
fi

java -jar /code/universa_core/build/output/uninode.jar -c .