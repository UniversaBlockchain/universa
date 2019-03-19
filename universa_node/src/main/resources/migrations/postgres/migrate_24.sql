create table follower_subscription(
    id bigserial primary key,
    origin bytea not null,
    environment_id bigint not null,
    expires_at bigint not null,
    muted_at bigint not null,
    spent_for_callbacks double precision not null,
    started_callbacks int not null
);

create index ix_follower_subscription_origin on follower_subscription(origin);
create index ix_follower_subscription_expires_at on follower_subscription(expires_at);