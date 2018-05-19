FROM ubuntu:18.04

RUN apt-get update && apt-get install -my wget gnupg git
RUN echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee /etc/apt/sources.list.d/webupd8team-java.list
RUN echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886

RUN apt-get update

RUN echo "oracle-java8-set-default shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections
RUN apt-get install -y oracle-java8-set-default binfmt-support

RUN apt-get install -y java-package

RUN mkdir -p ~/.gradle
COPY gradle.properties ~/.gradle/gradle.properties

RUN apt-get install -y gradle


RUN mkdir -p ~/.ssh
RUN ssh-keyscan -t rsa github.com >> ~/.ssh/known_hosts
RUN git clone https://github.com/UniversaBlockchain/universa.git ./code
WORKDIR /code

RUN git submodule init
RUN git submodule update

RUN gradle :universa_core:buildMultiJar -x test

RUN gradle :universa_core:fatJar

RUN gradle :uniclient:fatJar

EXPOSE 2052
EXPOSE 2082
EXPOSE 2700

COPY deploy /deploy
WORKDIR /deploy/tmp

RUN java -jar /code/uniclient/build/libs/uniclient.jar -g node-1-local.universa.io

RUN mv /deploy/tmp/node-1-local.universa.io.public.unikey /deploy/config/keys/node-1-local.universa.io.public.unikey

WORKDIR /deploy

ENTRYPOINT  java -jar /code/universa_core/build/output/uninode.jar -c .