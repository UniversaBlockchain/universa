drop table items;

create table items (
    id integer,
    packed bytea,
    keepTill bigint,
    foreign key (id) references ledger(id) on delete set null
);
