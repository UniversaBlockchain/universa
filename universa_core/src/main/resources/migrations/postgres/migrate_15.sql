drop table environment_storage;

create table environments(
    id bigserial primary key,
    ncontract_type text not null,
    ncontract_hash_id bytea not null,
    kv_storage bytea not null,
    transaction_pack bytea not null
);

create unique index ix_environments_hash_id on environments(ncontract_hash_id);

alter table contract_storage add column origin bytea;
alter table contract_storage add column expires_at bigint;

alter table contract_subscription drop column origin;

create table environment_subscription(
    id bigserial primary key,
    subscription_id bigint references contract_subscription(id),
    environemtn_id bigint references environments(id)
);
