#!/bin/bash
set -e
PRIVATE_DIR="/deploy/tmp"

if ! [ "$(ls -A $PRIVATE_DIR)" ]; then
	java -jar /deploy/build-client/libs/uniclient.jar -g node-${NODE_INDEX}-local.utoken.io
	mv ./node-${NODE_INDEX}-local.utoken.io.public.unikey /deploy/config/keys/node-${NODE_INDEX}-local.utoken.io.public.unikey
	mv ./node-${NODE_INDEX}-local.utoken.io.private.unikey /deploy/tmp/node-${NODE_INDEX}-local.utoken.io.private.unikey
fi
