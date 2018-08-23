FROM debian:stretch-slim

RUN mkdir -p /deploy/config/keys && mkdir -p /deploy/tmp && mkdir -p /usr/share/man/man1 \
	&& apt-get update --quiet=2 --yes \
	&& apt-get install --quiet=2 --yes --no-install-recommends --fix-missing gnupg dirmngr \
	&& echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee /etc/apt/sources.list.d/webupd8team-java.list \
	&& echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu xenial main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list \
	&& apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886 \
	&& apt-get update --quiet=2 --yes \
	&& echo "oracle-java8-set-default shared/accepted-oracle-license-v1-1 select true" | debconf-set-selections \
	&& apt-get install --quiet=2 --yes --no-install-recommends oracle-java8-set-default binfmt-support \
	&& rm -rf /var/cache/oracle-jdk8-installer \
	&& apt-get remove --quiet=2 --yes --purge gnupg dirmngr \
	&& apt-get clean --quiet=2 --yes \
	&& apt-get autoremove --quiet=2 --yes \
	&& rm -rf /var/lib/apt/lists/*

WORKDIR /
ENTRYPOINT /usr/bin/java "$0" "$@"
