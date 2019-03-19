CREATE OR REPLACE FUNCTION sr_find_or_create(hash_id bytea)
  RETURNS SETOF ledger AS $$
BEGIN
RETURN QUERY
WITH new_row AS (
  INSERT INTO ledger(hash, state, created_at, expires_at, locked_by_id)
    SELECT
      $1,
      1,
      extract(epoch from timezone('GMT', now())),
      extract(epoch from timezone('GMT', now() + interval '5 minute')),
      NULL
    WHERE NOT EXISTS (SELECT * FROM ledger WHERE hash = $1)
  RETURNING *
)
SELECT * FROM new_row
UNION
SELECT * FROM ledger WHERE hash = $1;
END;

$$
LANGUAGE 'plpgsql';
