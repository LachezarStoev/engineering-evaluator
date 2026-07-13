alter table criterion add column aggregation varchar(20) not null default 'SUM';
alter table criterion add column denominator_metric_key varchar(100);

insert into engineering_level(id,code,name,ordinal_value,version,status,effective_from) values
('10000000-0000-0000-0000-000000000001','JUNIOR','Junior Developer',1,1,'PUBLISHED','2026-01-01'),
('10000000-0000-0000-0000-000000000002','MID','Mid-Level Developer',2,1,'PUBLISHED','2026-01-01'),
('10000000-0000-0000-0000-000000000003','MID_II','Upper Mid-Level',3,1,'PUBLISHED','2026-01-01'),
('10000000-0000-0000-0000-000000000004','SENIOR','Senior Developer',4,1,'PUBLISHED','2026-01-01'),
('10000000-0000-0000-0000-000000000005','PRINCIPAL','Principal Developer',5,1,'PUBLISHED','2026-01-01');

insert into criterion(id,code,name,description,source_tool,metric_key,evaluation_type,period_type,operator,threshold_value,level_code,version,status,effective_from,aggregation,denominator_metric_key) values
('20000000-0000-0000-0000-000000000001','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',150,'JUNIOR',1,'PUBLISHED','2026-01-01','SUM',null),
('20000000-0000-0000-0000-000000000002','QA_RATIO','QA return ratio','QA reopens divided by completed tasks','jira','qa_reopens','AUTOMATIC','QUARTER','<',25,'JUNIOR',1,'PUBLISHED','2026-01-01','RATIO','completed_tasks'),
('20000000-0000-0000-0000-000000000003','DOCUMENTATION','Documentation contributions','Technical documentation updates','confluence','documentation_updates','AUTOMATIC','QUARTER','>=',1,'JUNIOR',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000004','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',250,'MID',1,'PUBLISHED','2026-01-01','SUM',null),
('20000000-0000-0000-0000-000000000005','QA_RATIO','QA return ratio','QA reopens divided by completed tasks','jira','qa_reopens','AUTOMATIC','QUARTER','<',15,'MID',1,'PUBLISHED','2026-01-01','RATIO','completed_tasks'),
('20000000-0000-0000-0000-000000000006','REVIEWS','Monthly meaningful reviews','Eligible GitLab review comments','gitlab','review_comments','AUTOMATIC_WITH_REVIEW','MONTH','>=',20,'MID',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000007','DOCUMENTATION','Documentation contributions','Technical documentation updates','confluence','documentation_updates','AUTOMATIC','QUARTER','>=',2,'MID',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000008','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',250,'MID_II',1,'PUBLISHED','2026-01-01','SUM',null),
('20000000-0000-0000-0000-000000000009','QA_RATIO','QA return ratio','QA reopens divided by completed tasks','jira','qa_reopens','AUTOMATIC','QUARTER','<',12,'MID_II',1,'PUBLISHED','2026-01-01','RATIO','completed_tasks'),
('20000000-0000-0000-0000-000000000010','REVIEWS','Monthly meaningful reviews','Eligible GitLab review comments','gitlab','review_comments','AUTOMATIC_WITH_REVIEW','MONTH','>=',30,'MID_II',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000011','VERSATILITY','Cross-project versatility','Cross-project evidence for human review','gitlab','cross_project_contributions','MANAGER_REVIEWED','QUARTER','>=',1,'MID_II',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000012','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',150,'SENIOR',1,'PUBLISHED','2026-01-01','SUM',null),
('20000000-0000-0000-0000-000000000013','QA_RATIO','QA return ratio','QA reopens divided by completed tasks','jira','qa_reopens','AUTOMATIC','QUARTER','<',10,'SENIOR',1,'PUBLISHED','2026-01-01','RATIO','completed_tasks'),
('20000000-0000-0000-0000-000000000014','FINAL_AUDITS','Final task audits','Final review evidence','jira','final_task_reviews','AUTOMATIC_WITH_REVIEW','QUARTER','>=',40,'SENIOR',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000015','ANALYSIS_APPROVALS','Major analysis approvals','Technical approval evidence','jira','analysis_approvals','AUTOMATIC_WITH_REVIEW','QUARTER','>=',5,'SENIOR',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000016','CORE_VELOCITY','Core system story points','Core Jira work','jira','core_story_points','AUTOMATIC','QUARTER','>=',100,'PRINCIPAL',1,'PUBLISHED','2026-01-01','SUM',null),
('20000000-0000-0000-0000-000000000017','QA_RATIO','QA return ratio','QA reopens divided by completed tasks','jira','qa_reopens','AUTOMATIC','QUARTER','<',5,'PRINCIPAL',1,'PUBLISHED','2026-01-01','RATIO','completed_tasks'),
('20000000-0000-0000-0000-000000000018','FINAL_AUDITS','Final task audits','Final review evidence','jira','final_task_reviews','AUTOMATIC_WITH_REVIEW','QUARTER','>=',40,'PRINCIPAL',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000019','ANALYSIS_APPROVALS','Major analysis approvals','Technical approval evidence','jira','analysis_approvals','AUTOMATIC_WITH_REVIEW','QUARTER','>=',10,'PRINCIPAL',1,'PUBLISHED','2026-01-01','COUNT',null),
('20000000-0000-0000-0000-000000000020','STRATEGIC_INITIATIVE','Platform/process initiative','Requires manager confirmation','jira','strategic_initiatives','MANAGER_REVIEWED','QUARTER','>=',1,'PRINCIPAL',1,'PUBLISHED','2026-01-01','COUNT',null);
