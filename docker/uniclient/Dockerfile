# To build:
#   docker build --tag universa/uniclient:latest --compress -f docker/uniclient/Dockerfile .
#   docker push universa/uniclient

FROM universa/node

WORKDIR /deploy
ENTRYPOINT ["java", "-jar", "/deploy/build-client/libs/uniclient.jar"]
