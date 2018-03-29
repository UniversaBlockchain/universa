Uniclient Command-Line tool (beta)
==================================
Release notes
-------------

### Coming soon

Support of new Capsule format (saves space on long contract chains).

New feature: append signature to partially signed contracts without breaking existing signatures.

Bulk operations for import, export, register and check.

Probe files, not only ids.

### 3.2.4b2 (29.03.2018)
* Added `--address` command that generates address from key. Has option `-short`.
* Added `--address-match` command that check matching address with key from file. Path to key define in parameter `-keyfile`.
* Added `--folder-match` command that associates the entered address with the key file in the specified directory. Address define in parameter `-addr`.

### 3.1.0 (05.03.2018)
* Added `--tutest` key for `--register` command with `--tu` key to use test transaction units as payment.
* Added `--anonymize` and `--role` keys to making anonymous roles in the contract.

### 3.0.1 (27.02.2018)
* Added `--tu` and `--amount` keys for `--register` command to avoid paid processing of contracts.

### 2.2.4 (29.12.2017)
* Added `--cost` as standalone key and as key for use with `--register`.

### 2.2.3 (16.11.2017)
* Added `--wait` and `--register` keys.

### 2.2.0 (07.11.2017)
* Added `--revoke` key.
* Added `--pack` and `--unpack` keys.

### 2.1.5 (05.11.2017)
* Better `--network` handling.

### 2.1.4 (03.11.2017)
* Better command line options logic.
* Bulk (transactions) operation support.

### 2.1.3 (01.11.2017)
* First published beta. Contains all the basic functionality to work with Universa network.