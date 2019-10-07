create table votings(
  id serial primary key,
  hash bytea not null,
  expires_at integer
);


create table voting_votes(
    id serial primary key,
    voting_id integer references votings(id) on delete cascade,
    packed_key bytea
);

create unique index ix_votings_hashes on votings(hash);
create index ix_votings_expires_at on votings(expires_at);
create index ix_voting_vote_voting_id on voting_votes(voting_id);


