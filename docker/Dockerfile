FROM debian:stretch

RUN apt-get update && apt-get install -my wget gnupg git gradle && mkdir -p ~/.gradle && mkdir /deploy && mkdir /deploy/config && mkdir /deploy/tmp && echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee /etc/apt/sources.list.d/webupd8team-java.list && echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886 && rm -rf /var/lib/apt/lists/*

RUN apt-get update && echo "oracle-java8-set-default shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections && apt-get install -y oracle-java8-set-default binfmt-support && rm -rf /var/lib/apt/lists/*

COPY gradle.properties ~/.gradle/gradle.properties

RUN mkdir -p ~/.ssh && ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts && git clone https://github.com/UniversaBlockchain/universa.git /code
WORKDIR /code

RUN git submodule init && git submodule update && gradle :universa_core:buildMultiJar -x test && gradle :universa_core:fatJar && gradle :uniclient:fatJar

WORKDIR /deploy

RUN mv /code/uniclient/build /deploy/build-client && mv /code/universa_core/build /deploy/build-core && rm -rf /code && apt-get remove -y --purge wget gnupg git gradle

COPY init-node.sh /deploy/init-node.sh

RUN chmod +x /deploy/init-node.sh

EXPOSE 2052
EXPOSE 2082
EXPOSE 2700

ENTRYPOINT /deploy/init-node.sh