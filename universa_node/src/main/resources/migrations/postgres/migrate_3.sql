CREATE OR REPLACE FUNCTION sr_find_or_create(hash_id bytea)
RETURNS SETOF ledger AS $$
BEGIN
    INSERT INTO ledger(hash, state, created_at, expires_at)
    VALUES(
        $1,
        1,
        extract(epoch from timezone('GMT', now())),
        extract(epoch from timezone('GMT', now() + interval '5 minutes'))
    )
    ON CONFLICT (hash) DO NOTHING;

    RETURN QUERY SELECT * FROM ledger WHERE hash = $1;
END;
$$
LANGUAGE 'plpgsql';