
create table config (
    http_client_port integer not null,
    http_server_port integer not null,
    udp_server_port integer not null,
    node_number integer not null,
    node_name text not null,
    public_host text not null,
    host text,
    public_key bytea not null,
    private_key bytea
);

--create unique index ix_ledger_hashes on ledger(hash);
--create index ix_ledger_expires_at on ledger(expires_at);

