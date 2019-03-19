alter table contract_subscription add column hash_id bytea;
alter table contract_subscription add column subscription_on_chain boolean;

create table contract_binary(
    hash_id bytea primary key,
    bin_data bytea not null
);

insert into contract_binary select hash_id, bin_data from contract_storage on conflict do nothing;

alter table contract_storage add column environment_id bigint;
alter table contract_storage drop column bin_data;

drop index ix_contract_storage_hash_id;
create index ix_contract_storage_hash_id on contract_storage(hash_id);

update contract_subscription set hash_id=subquery.hash_id, subscription_on_chain=false from (
    select id, hash_id from contract_storage
) as subquery
where contract_subscription.contract_storage_id=subquery.id;

insert into contract_storage (hash_id, origin, expires_at, environment_id) select contract_storage.hash_id,
contract_storage.origin, contract_subscription.expires_at, contract_subscription.environment_id
from contract_storage join contract_subscription on contract_storage.id=contract_subscription.contract_storage_id;

alter table contract_subscription drop column contract_storage_id;

delete from contract_storage where environment_id is null;

insert into contract_subscription (hash_id, subscription_on_chain, expires_at, environment_id) select
origin as hash_id, true as subscription_on_chain, expires_at, environment_id from follower_subscription;

create table follower_environments(
    environment_id bigint primary key,
    expires_at bigint not null,
    muted_at bigint not null,
    spent_for_callbacks double precision not null,
    started_callbacks int not null
);

insert into follower_environments select environment_id, max(expires_at) as expires_at, max(muted_at) as muted_at,
sum(spent_for_callbacks) as spent_for_callbacks, sum(started_callbacks) as started_callbacks from follower_subscription group by environment_id;

drop table follower_subscription;

alter table follower_callbacks drop column subscription_id;

create index ix_contract_subscription_hash_id on contract_subscription(hash_id);
create index ix_contract_binary_hash_id on contract_binary(hash_id);
create index ix_contract_storage_environment_id on contract_storage(environment_id);
create index ix_follower_environments_environment_id on follower_environments(environment_id);