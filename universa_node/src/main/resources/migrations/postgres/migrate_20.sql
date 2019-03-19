create table name_storage(
    id bigserial primary key,
    name_reduced text not null,
    name_full text not null,
    description text,
    url text,
    expires_at bigint not null,
    environment_id bigint references environments(id) on delete cascade
);

create table name_entry(
    entry_id bigserial primary key,
    name_storage_id bigint references name_storage(id) on delete cascade,
    short_addr text,
    long_addr text,
    origin bytea
);

create unique index ix_name_storage_name_reduced on name_storage(name_reduced);
create index ix_name_storage_expires_at on name_storage(expires_at);

create index ix_name_entry_name_storage_id on name_entry(name_storage_id);
create index ix_name_entry_short_addr on name_entry(short_addr);
create index ix_name_entry_long_addr on name_entry(long_addr);
create index ix_name_entry_origin on name_entry(origin);
