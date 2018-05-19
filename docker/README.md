# Universa Docker

Allow to execute local environment of Universa Blockchain Smart Contracts.

## Requirements
Install Docker and Docker Compose
### Docker
https://docs.docker.com/install/linux/docker-ce/ubuntu/
### Docker Compose
https://docs.docker.com/compose/install/

## Description
docker-compose.yaml file contains definitions of two containers:

 - db: Postgres database.  Image will download the Postgres image 
 - universa-node-1: Universa node with private key. Image will be created from Dockerfile


# Run
    docker-compose up db
Wait ≈ 10 seconds

    docker-compose up universa-node-1
