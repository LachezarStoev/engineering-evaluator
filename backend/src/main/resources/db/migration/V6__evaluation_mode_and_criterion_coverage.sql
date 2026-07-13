alter table evaluation add column evaluation_mode varchar(30) not null default 'SNAPSHOT';
alter table criterion add column minimum_coverage varchar(30) not null default 'COMPLETE';
alter table criterion add column custom_period_allowed boolean not null default false;
alter table criterion_result add column cadence varchar(30);
alter table criterion_result add column criterion_name varchar(200);
