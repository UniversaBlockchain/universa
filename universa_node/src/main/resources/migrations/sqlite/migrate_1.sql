--- sqlite version

create table ledger(
    id integer primary key,
    hash blob,
    state integer,
    locked_by_id integer,
    created_at integer not null,
    expires_at integer
);

create unique index ix_ledger_hashes on ledger(hash);
-- create index ix_ledger_locks on ledger(locked_by_id);
create index ix_ledger_expires_at on ledger(expires_at);

