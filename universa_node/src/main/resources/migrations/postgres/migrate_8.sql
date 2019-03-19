CREATE OR REPLACE FUNCTION sr_find_unfinished()
RETURNS SETOF ledger AS $$
BEGIN
    RETURN QUERY SELECT * FROM ledger WHERE state in (1,2,3,5,9) order by id asc;
END;
$$
LANGUAGE 'plpgsql';