-- purge old testnet contracts that have short hashid
DELETE FROM ledger
WHERE octet_length(hash) = 64;

