create table contract_storage(
    id bigserial primary key,
    hash_id bytea not null,
    bin_data bytea not null
);

create unique index ix_contract_storage_hash_id on contract_storage(hash_id);



create table contract_subscription(
    id bigserial primary key,
    contract_storage_id bigint references contract_storage(id),
    expires_at bigint not null,
    origin bytea not null
);

create index ix_contract_subscription_expires_at on contract_subscription(expires_at);
