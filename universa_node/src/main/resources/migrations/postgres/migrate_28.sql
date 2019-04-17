create table kept_items_tags (
    ledger_id integer,
    tag text,
    value text,
    foreign key (ledger_id) references ledger(id) on delete cascade
    );

create index ix_kept_items_tags_ledger_id on kept_items_tags(ledger_id);
create index ix_kept_items_tags_tag on kept_items_tags(tag);
create index ix_kept_items_tags_value on kept_items_tags(value);

ALTER TABLE keeping_items RENAME TO kept_items;
ALTER TABLE kept_items RENAME COLUMN id TO ledger_id;
ALTER TABLE kept_items DROP COLUMN hash;