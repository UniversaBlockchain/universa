create table items (
    id serial,
    packed bytea,
    keepTill bigint,
    foreign key (id) references ledger(id)
    );
