create table environment_storage(
    id bigserial primary key,
    hash_id bytea not null,
    bin_data bytea not null,
    ncontract_hash_id bytea not null
);

create unique index ix_environment_storage_hash_id on environment_storage(hash_id);
create index ix_environment_storage_ncontract_hash_id on environment_storage(ncontract_hash_id);
