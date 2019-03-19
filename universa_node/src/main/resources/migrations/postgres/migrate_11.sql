create table payments_summary(
    amount int,
    date int
);

create unique index ix_payment_dates on payments_summary(date);
