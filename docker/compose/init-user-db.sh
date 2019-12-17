#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER node1user;
	ALTER USER node1user with encrypted password 'uniPass';
	CREATE DATABASE node1db;
	GRANT ALL PRIVILEGES ON DATABASE node1db TO node1user;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER node2user;
	ALTER USER node2user with encrypted password 'uniPass';
	CREATE DATABASE node2db;
	GRANT ALL PRIVILEGES ON DATABASE node2db TO node2user;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER node3user;
	ALTER USER node3user with encrypted password 'uniPass';
	CREATE DATABASE node3db;
	GRANT ALL PRIVILEGES ON DATABASE node3db TO node3user;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER node4user;
	ALTER USER node4user with encrypted password 'uniPass';
	CREATE DATABASE node4db;
	GRANT ALL PRIVILEGES ON DATABASE node4db TO node4user;
EOSQL

touch /var/tmp/db_init_completed.lock