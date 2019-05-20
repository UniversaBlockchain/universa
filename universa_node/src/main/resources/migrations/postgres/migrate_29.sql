ALTER TABLE items RENAME COLUMN id TO id_temp;

ALTER TABLE items ADD COLUMN id integer REFERENCES ledger(id) ON DELETE SET NULL;

UPDATE items SET id = id_temp;

ALTER TABLE items DROP COLUMN id_temp;