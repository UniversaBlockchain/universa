DELETE FROM votings;
ALTER TABLE votings ADD COLUMN packed BYTEA NOT NULL;
ALTER TABLE voting_votes RENAME COLUMN packed_key to packed_address;
CREATE UNIQUE INDEX ix_voting_votes_packed_addresses ON voting_votes(voting_id,packed_address);

ALTER TABLE votings ADD COLUMN role_name TEXT NOT NULL;

CREATE TABLE voting_candidates(
    id SERIAL PRIMARY KEY,
    voting_id INTEGER REFERENCES votings(id) ON DELETE CASCADE,
    candidate_hash BYTEA
);

ALTER TABLE voting_votes ADD COLUMN voting_candidate_id
INTEGER REFERENCES voting_candidates(id) ON DELETE CASCADE;

drop index ix_votings_hashes;
create index ix_votings_hashes on votings(hash);
create unique index ix_votings_hashes_roles on votings(hash,role_name);