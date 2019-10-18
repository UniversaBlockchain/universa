DELETE FROM votings;
alter table votings ADD COLUMN packed bytea not null;
alter table voting_votes rename column packed_key to packed_address;
create unique index ix_voting_votes_packed_addresses on voting_votes(voting_id,packed_address);
