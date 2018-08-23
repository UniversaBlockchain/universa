#FROM debian:stretch-slim

#COPY gradle.properties /root/.gradle/gradle.properties
#COPY uninode.sh /deploy/uninode
#COPY uniclient.sh /deploy/uniclient


# All-inclusive layer.

#RUN mkdir -p /deploy/config/keys && mkdir -p /deploy/tmp && mkdir -p /usr/share/man/man1 \
#	&& apt-get update --quiet=2 --yes \
#	&& apt-get install --quiet=2 --yes --no-install-recommends --fix-missing gnupg dirmngr git gradle openssh-client \
#	&& echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee /etc/apt/sources.list.d/webupd8team-java.list \
#	&& echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list \
#	&& apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886 \
#	&& apt-get update --quiet=2 --yes \
#	&& echo "oracle-java8-set-default shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections \
#	&& apt-get install --quiet=2 --yes --no-install-recommends oracle-java8-set-default binfmt-support \
#	&& rm -rf /var/cache/oracle-jdk8-installer \
#	&& mkdir -p ~/.ssh && ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts && git clone https://github.com/UniversaBlockchain/universa.git /code \
#	&& cd /code \
#	&& gradle -Dfile.encoding=utf-8 :universa_core:fatJar -x test \
#	&& gradle -Dfile.encoding=utf-8 :uniclient:fatJar -x test \
#	&& mv /code/uniclient/build /deploy/build-client && mv /code/universa_core/build /deploy/build-core \
#	&& cd / \
#	&& rm -rf /code \
#	&& apt-get remove --quiet=2 --yes --purge gnupg dirmngr git gradle openssh-client \
#	&& rm -rf /root/.gradle \
#	&& apt-get clean --quiet=2 --yes \
#	&& apt-get autoremove --quiet=2 --yes \
#	&& rm -rf /var/lib/apt/lists/*

# Alternatively, the node binary can be built with the following command:
#   gradle -Dfile.encoding=utf-8 :universa_core:buildMultiJar -x test

#
# Multi-stage build
#

# The separate layer with JDK, may be reused if cached.

# The layer with Universa-specific code.
#
# For a most clean build, the code being built is not taken from the local directory,
# but cloned from Github from scratch.

FROM universa/debian-jdk8:latest as build_universa
COPY docker/node/gradle.properties /root/.gradle/gradle.properties
COPY / /code/
WORKDIR /code
RUN apt-get update --quiet=2 --yes \
	&& apt-get install --quiet=2 --yes --no-install-recommends --fix-missing gradle \
	&& gradle -Dfile.encoding=utf-8 :universa_core:fatJar -x test \
	&& gradle -Dfile.encoding=utf-8 :uniclient:fatJar -x test \
	&& mv /code/uniclient/build /deploy/build-client && mv /code/universa_core/build /deploy/build-core

# Alternatively, the node binary can be built with the following command:
#   gradle -Dfile.encoding=utf-8 :universa_core:buildMultiJar -x test

#	&& cd / \
#	&& rm -rf /code \
#	&& apt-get remove --quiet=2 --yes --purge gradle \
#	&& rm -rf /root/.gradle \
#	&& apt-get clean --quiet=2 --yes \
#	&& apt-get autoremove --quiet=2 --yes \
#	&& rm -rf /var/lib/apt/lists/*

FROM universa/debian-jdk8:latest as universa
COPY --from=build_universa /deploy/build-client /deploy/build-client
COPY --from=build_universa /deploy/build-core /deploy/build-core
COPY docker/node/uninode.sh /deploy/uninode
COPY docker/node/uniclient.sh /deploy/uniclient

#RUN apt-get update --quiet=2 --yes \
#	&& apt-get install --quiet=2 --yes --no-install-recommends --fix-missing git gradle \
#	&& mkdir -p ~/.ssh && ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts && git clone https://github.com/UniversaBlockchain/universa.git /code \
#	&& cd /code \
#	&& gradle -Dfile.encoding=utf-8 :universa_core:fatJar -x test \
#	&& gradle -Dfile.encoding=utf-8 :uniclient:fatJar -x test \
#	&& mv /code/uniclient/build /deploy/build-client && mv /code/universa_core/build /deploy/build-core \
#	&& rm -rf /code \
#	&& apt-get remove --quiet=2 --yes --purge git gradle \
#	&& rm -rf /root/.gradle \
#	&& apt-get clean --quiet=2 --yes \
#	&& apt-get autoremove --quiet=2 --yes \
#	&& rm -rf /var/lib/apt/lists/*

EXPOSE 2052
EXPOSE 2082
EXPOSE 2700

WORKDIR /deploy
ENTRYPOINT /deploy/uninode "$0" "$@"
