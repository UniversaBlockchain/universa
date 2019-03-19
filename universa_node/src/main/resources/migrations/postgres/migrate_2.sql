CREATE OR REPLACE FUNCTION sr_find_or_create(hash_id bytea)
RETURNS SETOF ledger AS $$
BEGIN
    INSERT INTO ledger(hash, state, created_at)
    VALUES($1,1,extract(epoch from timezone('GMT', now())))
    ON CONFLICT (hash) DO NOTHING;

    RETURN QUERY SELECT * FROM ledger WHERE hash = $1;
END;
$$
LANGUAGE 'plpgsql';

