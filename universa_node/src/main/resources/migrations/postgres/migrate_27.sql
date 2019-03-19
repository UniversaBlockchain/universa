alter table keeping_items add column parent bytea;
create index ix_keeping_items_parent on keeping_items(parent);