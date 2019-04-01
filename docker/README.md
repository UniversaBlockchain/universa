Universa Docker containers
--------------------------

This Docker configuration allows you to configure and execute a local environment of Universa Blockchain nodes, for various development/debug purposes.

See more details in our Knowledge Base article on Universa docker images: [kb.universablockchain.com/universa_docker_images/92](https://kb.universablockchain.com/universa_docker_images/92).


## Prerequirements

Install Docker and Docker Compose, as appropriate for your environment and operating system. See more details on [Docker site](https://docker.com).


### Building

Build `universa/debian-jdk8`:

~~~
docker build --tag universa/debian-jdk8:latest docker/debian-jdk8
~~~

Build `universa/universa/node`:

~~~
docker build --tag universa/node:latest --compress -f docker/node/Dockerfile .
~~~

or

~~~
docker build --tag universa/node:latest --compress .
~~~

---

For other documentation on Universa please consult the Universa Knowledge Base at [kb.universablockchain.com](https://kb.universablockchain.com). For a visual guide on the documentation topics, visit the Universa Development Map at [lnd.im/UniversaDevelopmentMap](https://lnd.im/UniversaDevelopmentMap).
