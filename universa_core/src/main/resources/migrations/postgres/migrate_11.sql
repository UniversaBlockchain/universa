create table ledger_testnet(
) INHERITS(ledger);

create unique index ix_ledger_testnet_hashes on ledger_testnet(hash);
create index ix_ledger_testnet_expires_at on ledger_testnet(expires_at);

CREATE OR REPLACE FUNCTION sr_move_to_testnet(hash_id bytea) RETURNS SETOF ledger AS $$
DECLARE
    rec RECORD;
    newRecId INT;
BEGIN
    SELECT * INTO rec FROM ledger_testnet WHERE hash = $1;
    IF NOT FOUND THEN
        SELECT * INTO rec FROM ledger WHERE hash = $1;
        INSERT INTO ledger_testnet(hash, state, created_at, expires_at, locked_by_id) VALUES (rec.hash,rec.state,rec.created_at,rec.expires_at,rec.locked_by_id) RETURNING id into newRecId;


        DELETE FROM items WHERE id = rec.id;
        DELETE FROM ledger WHERE id = rec.id;
        UPDATE ledger SET locked_by_id = newRecId WHERE locked_by_id = rec.id;
    END IF;

    RETURN QUERY SELECT * FROM ledger WHERE hash = $1;
END;
$$
LANGUAGE 'plpgsql';