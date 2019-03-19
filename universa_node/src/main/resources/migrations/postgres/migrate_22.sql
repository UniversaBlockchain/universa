create table keeping_items (
    id integer,
    hash bytea not null,
    origin bytea not null,
    packed bytea,
    foreign key (id) references ledger(id) on delete set null
    );