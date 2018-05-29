alter table contract_subscription add column environment_id bigint;
create index ix_contract_subscription_environment_id on contract_subscription(environment_id);

update contract_subscription set environment_id=subquery.environemtn_id from (
    select id, subscription_id, environemtn_id from environment_subscription
) as subquery
where contract_subscription.id=subquery.subscription_id;

drop table environment_subscription;
