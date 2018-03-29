create table ledger_testrecords(
    hash bytea not null
);

create unique index ix_ledger_testnet_hashes on ledger_testrecords(hash);
