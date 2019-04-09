#
# Multi-stage build
#

# Just build the Universa JAR files, leaving many temporary files behind.

FROM universa/debian-jdk8:latest as build_universa
COPY /docker/node/gradle.properties /root/.gradle/gradle.properties
COPY / /code/
WORKDIR /code
RUN apt-get update --quiet=2 --yes \
	&& apt-get install --quiet=2 --yes --no-install-recommends --fix-missing gradle \
	&& gradle -Dfile.encoding=utf-8 :universa_node:fatJar -x test \
	&& gradle -Dfile.encoding=utf-8 :uniclient:fatJar -x test \
	&& mv /code/uniclient/build /deploy/build-client && mv /code/universa_node/build /deploy/build-node

# Copy only the needed files.

FROM universa/debian-jdk8:latest as universa
COPY --from=build_universa /deploy/build-client /deploy/build-client
COPY --from=build_universa /deploy/build-node /deploy/build-node
COPY /docker/node/uninode.sh /deploy/uninode
COPY /docker/node/uniclient.sh /deploy/uniclient

EXPOSE 2052
EXPOSE 2082
EXPOSE 2700

WORKDIR /deploy
ENTRYPOINT /deploy/uninode "$0" "$@"
