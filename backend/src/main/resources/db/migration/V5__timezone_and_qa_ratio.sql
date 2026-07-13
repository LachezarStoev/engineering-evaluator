alter table evaluation add column period_timezone varchar(80) not null default 'Europe/Sofia';

update criterion
set metric_key = 'qa_defects',
    denominator_metric_key = 'qa_tested_completed_tasks',
    description = 'Attributed QA defects divided by completed implementation tasks with a linked QA task'
where code = 'QA_RATIO';
