create table ubot_transaction(
    id bigserial primary key not null,
    executable_contract_id bytea not null,
    transaction_name text not null,
    current_session bytea,
    pending jsonb,
    finished jsonb,
    unique(executable_contract_id, transaction_name)
);