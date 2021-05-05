create table ubot_storage(
    id bigserial primary key not null,
    executable_contract_id bytea not null,
    storage_name text not null,
    storage_data bytea,
    save_timestamp bigint not null,
    expires_at bigint not null,
    unique(executable_contract_id, storage_name)
);

alter table ubot_session drop column storages;
