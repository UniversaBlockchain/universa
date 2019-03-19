create table follower_callbacks(
    id bytea primary key,
    state int not null,
    environment_id bigint not null,
    subscription_id bigint not null,
    expires_at bigint not null,
    stored_until bigint not null
);

create index ix_follower_callbacks_id on follower_callbacks(id);
create index ix_follower_callbacks_expires_at on follower_callbacks(expires_at);