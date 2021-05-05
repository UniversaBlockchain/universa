create table ubot_session(
    id bigserial primary key not null,
    executable_contract_id bytea not null unique,
    save_timestamp bigint not null,
    request_id bytea,
    request_contract bytea,
    state integer,
    session_id bytea,
    storages jsonb,
    storage_updates jsonb,
    close_votes jsonb,
    close_votes_finished jsonb
);
