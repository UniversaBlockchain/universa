#!/bin/bash
set -e
PRIVATE_DIR="/deploy/tmp"

if ! [ "$(ls -A $PRIVATE_DIR)" ]; then
	java -jar /deploy/build-client/libs/uniclient.jar -g node-${NODE_INDEX}-local.universa.io
	mv ./node-${NODE_INDEX}-local.universa.io.public.unikey /deploy/config/keys/node-${NODE_INDEX}-local.universa.io.public.unikey
	mv ./node-${NODE_INDEX}-local.universa.io.private.unikey /deploy/tmp/node-${NODE_INDEX}-local.universa.io.private.unikey
fi

java -jar /deploy/build-core/output/uninode.jar -c /deploy