create table keeping_items (
    id serial,
    hash bytea not null,
    origin bytea not null,
    packed bytea
    );