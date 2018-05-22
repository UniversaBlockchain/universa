
# Universa Docker
Allow to execute local environment of Universa Blockchain Smart Contracts.
## Requirements
Install Docker and Docker Compose
### Docker
https://docs.docker.com/install/linux/docker-ce/ubuntu/
### Docker Compose
https://docs.docker.com/compose/install/
## Description
docker-compose.yaml file contains definitions of five containers:
 - db: Postgres database.  Image will download the Postgres image 
 - universa-node-1: Universa node. Image will be created from Dockerfile
 - universa-node-2: Universa node. 
 - universa-node-3: Universa node.
 - universa-node-4: Universa node. 

In **deploy** folder are located node's configurations, private and public keys.
# Run
    docker-compose up -d

# Containers
| Container | External Ports | Internal IP | Internal Ports |
| ------ | ------ | ------ | ------ |
| db | 5432 | 10.6.0.10 | 5432 |
| node-1-local | 2052, 2082,2700 | 10.6.0.11| 2052, 2082,2700|
| node-2-local | 2053, 2083,2701 | 10.6.0.12| 2052, 2082,2700|
| node-3-local | 2054, 2084,2702 |10.6.0.13 |2052, 2082,2700 |
| node-4-local | 2055, 2085,2703 |10.6.0.14 | 2052, 2082,2700|

# Logs

Firstly, check the name of container to view logs:

    docker ps

For example, if the name of first node's container is **code_node-1-local_1** (depends of parent folder name, in this case is **code**) log command is:

    docker logs code_node-2-local_1

This will show the stdout of container.