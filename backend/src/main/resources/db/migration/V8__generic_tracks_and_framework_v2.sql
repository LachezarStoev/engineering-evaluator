create table engineering_track (
  id uuid primary key,
  code varchar(60) not null,
  name varchar(120) not null,
  description varchar(2000),
  icon_key varchar(60) not null default 'code',
  ordinal_value integer not null,
  version integer not null,
  status varchar(20) not null,
  effective_from date not null,
  created_at timestamp with time zone not null,
  unique(code, version)
);

alter table employee add column track_code varchar(60) not null default 'GENERAL';
update employee set current_level_code = 'JUNIOR_II' where current_level_code = 'JUNIOR';
update employee set target_level_code = 'JUNIOR_II' where target_level_code = 'JUNIOR';
update employee set current_level_code = 'MID_I' where current_level_code = 'MID';
update employee set target_level_code = 'MID_I' where target_level_code = 'MID';
update employee set current_level_code = 'SENIOR_I' where current_level_code = 'SENIOR';
update employee set target_level_code = 'SENIOR_I' where target_level_code = 'SENIOR';
alter table criterion add column criterion_scope varchar(20) not null default 'COMMON';
alter table criterion add column track_code varchar(60);
alter table criterion add column team_key varchar(120);
alter table criterion add column proration_policy varchar(30) not null default 'PROGRESS_ONLY';
alter table criterion add column mandatory_criterion boolean not null default true;
alter table criterion add column rubric_text varchar(4000);
alter table criterion add column visualization_key varchar(40) not null default 'PROGRESS';
alter table criterion add column threshold_max_value numeric(19,4);
alter table criterion_result add column threshold_max_value numeric(19,4);
alter table criterion_result add column period_target_max_value numeric(19,4);

create table competency_expectation (
  id uuid primary key,
  competency_key varchar(80) not null,
  level_code varchar(60) not null,
  track_code varchar(60),
  expectation varchar(500) not null,
  rubric_text varchar(4000),
  version integer not null,
  unique(competency_key, level_code, track_code, version)
);

insert into engineering_track(id,code,name,description,icon_key,ordinal_value,version,status,effective_from,created_at) values
('30000000-0000-0000-0000-000000000001','GENERAL','General Engineering','Common engineering expectations. Use when a specialized track has not been assigned.','code',1,1,'PUBLISHED','2026-07-13',current_timestamp),
('30000000-0000-0000-0000-000000000002','BACKEND','Backend Engineering','Services, APIs, data, reliability, operations and distributed-system ownership.','server',2,1,'PUBLISHED','2026-07-13',current_timestamp),
('30000000-0000-0000-0000-000000000003','FRONTEND','Frontend Engineering','Product UI, accessibility, web performance, design systems and frontend architecture.','layout',3,1,'PUBLISHED','2026-07-13',current_timestamp),
('30000000-0000-0000-0000-000000000004','FULLSTACK','Full-stack Engineering','End-to-end product delivery across frontend and backend systems.','layers',4,1,'DRAFT','2026-07-13',current_timestamp);

insert into engineering_level(id,code,name,ordinal_value,version,status,effective_from) values
('31000000-0000-0000-0000-000000000001','JUNIOR_I','Junior I',1,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000002','JUNIOR_II','Junior II',2,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000003','MID_I','Mid I',3,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000004','MID_II','Mid II',4,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000005','SENIOR_I','Senior I',5,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000006','SENIOR_II','Senior II',6,2,'PUBLISHED','2026-07-13'),
('31000000-0000-0000-0000-000000000007','PRINCIPAL','Principal Engineer',7,2,'PUBLISHED','2026-07-13');

-- Framework v2 common quantitative and evidence-assisted criteria. Track-specific rules can be
-- layered on top without copying the common matrix.
insert into criterion(id,code,name,description,source_tool,metric_key,evaluation_type,period_type,operator,threshold_value,threshold_max_value,level_code,version,status,effective_from,aggregation,denominator_metric_key,minimum_coverage,custom_period_allowed,criterion_scope,track_code,team_key,proration_policy,mandatory_criterion,rubric_text,visualization_key) values
('32000000-0000-0000-0000-000000000001','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',100,null,'JUNIOR_I',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000002','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',30,null,'JUNIOR_I',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000003','DOCUMENTATION','Onboarding or technical documentation','Confluence contributions','confluence','documentation_updates','AUTOMATIC_WITH_REVIEW','QUARTER','>=',1,null,'JUNIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000004','REVIEW_FEEDBACK','Addresses review feedback','Human confirmation supported by review evidence','human','review_feedback_followup','MANAGER_REVIEWED','QUARTER','>=',1,null,'JUNIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Confirms that material review feedback is understood and addressed consistently.','RUBRIC'),
('32000000-0000-0000-0000-000000000005','ONBOARDING','Completes onboarding plan','Human confirmation of the agreed onboarding plan','human','onboarding_completion','MANAGER_REVIEWED','CUSTOM','>=',1,null,'JUNIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Assess completion of the team onboarding plan and understanding of core workflows.','RUBRIC'),

('32000000-0000-0000-0000-000000000006','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',150,null,'JUNIOR_II',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000007','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',25,null,'JUNIOR_II',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000008','DOCUMENTATION','Technical documentation','Confluence contributions','confluence','documentation_updates','AUTOMATIC_WITH_REVIEW','QUARTER','>=',1,null,'JUNIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000009','REVIEWS','Meaningful review comments','Eligible GitLab review comments','gitlab','review_comments','AUTOMATIC_WITH_REVIEW','MONTH','>=',10,null,'JUNIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,'Review quality is more important than raw count; questionable evidence remains reviewable.','PROGRESS'),
('32000000-0000-0000-0000-000000000010','OWNERSHIP','Small functional-area ownership','Manager rubric supported by Jira, GitLab and documentation evidence','human','ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'JUNIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Owns a small functional area such as configuration, utilities or documentation.','RUBRIC'),

('32000000-0000-0000-0000-000000000011','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',250,null,'MID_I',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000012','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',15,null,'MID_I',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000013','DOCUMENTATION','Technical documentation','Confluence contributions','confluence','documentation_updates','AUTOMATIC_WITH_REVIEW','QUARTER','>=',2,null,'MID_I',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000014','REVIEWS','Meaningful review comments','Eligible GitLab review comments','gitlab','review_comments','AUTOMATIC_WITH_REVIEW','MONTH','>=',20,null,'MID_I',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000015','KNOWLEDGE_SHARING','Internal demo or presentation','Evidence of a demo, workshop or presentation','human','knowledge_sharing','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',false,'Confirms useful knowledge sharing with an identifiable audience and subject.','RUBRIC'),
('32000000-0000-0000-0000-000000000016','OWNERSHIP','One project, module or service','Manager rubric supported by delivery and operational evidence','human','ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Owns delivery and operational consequences for one project, module or service.','RUBRIC'),

('32000000-0000-0000-0000-000000000017','VELOCITY','Quarterly velocity','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','>=',250,null,'MID_II',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000018','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',12,null,'MID_II',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000019','REVIEWS','Meaningful review comments','Eligible GitLab review comments','gitlab','review_comments','AUTOMATIC_WITH_REVIEW','MONTH','>=',30,null,'MID_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000020','DOCUMENTATION','Technical documentation','Confluence contributions','confluence','documentation_updates','AUTOMATIC_WITH_REVIEW','QUARTER','>=',2,null,'MID_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,null,'PROGRESS'),
('32000000-0000-0000-0000-000000000021','VERSATILITY','Cross-domain contributions','Valuable GitLab contributions outside the primary project','gitlab','cross_project_contributions','AUTOMATIC_WITH_REVIEW','QUARTER','>=',1,null,'MID_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,'Contribution value and unfamiliarity of the domain require human confirmation.','PROGRESS'),
('32000000-0000-0000-0000-000000000022','REVIEW_OWNERSHIP','Review follow-up ownership','Manager rubric supported by review and follow-up evidence','human','review_followup','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_II',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Follows material issues identified during reviews through resolution.','RUBRIC'),
('32000000-0000-0000-0000-000000000023','OWNERSHIP','Multiple projects or services','Manager rubric supported by delivery and operational evidence','human','ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_II',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Demonstrates sustained ownership across multiple projects or services.','RUBRIC'),

('32000000-0000-0000-0000-000000000024','VELOCITY','Quarterly velocity range','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','BETWEEN',180,220,'SENIOR_I',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,'The range is supporting context; complexity and enablement can justify manager override.','RANGE'),
('32000000-0000-0000-0000-000000000025','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',10,null,'SENIOR_I',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000026','FINAL_AUDITS','Completed task audits','Final task-review evidence','jira','final_task_reviews','AUTOMATIC_WITH_REVIEW','QUARTER','>=',30,null,'SENIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Audit quality and technical responsibility must be confirmed.','PROGRESS'),
('32000000-0000-0000-0000-000000000027','ANALYSIS_APPROVALS','Major analysis approvals','Technical approval evidence','jira','analysis_approvals','AUTOMATIC_WITH_REVIEW','QUARTER','>=',5,null,'SENIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Only substantive technical analyses count.','PROGRESS'),
('32000000-0000-0000-0000-000000000028','MENTORING','Active mentoring','Human rubric supported by reviews, workshops and outcomes','human','mentoring','MANAGER_REVIEWED','QUARTER','>=',1,null,'SENIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Actively enables Mid and Junior developers and can describe concrete outcomes.','RUBRIC'),
('32000000-0000-0000-0000-000000000029','OWNERSHIP','Major system or business domain','Manager rubric supported by cross-system evidence','human','ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'SENIOR_I',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Trusted ownership of a major system or business domain.','RUBRIC'),

('32000000-0000-0000-0000-000000000030','VELOCITY','Quarterly velocity range','Completed Jira story points','jira','story_points','AUTOMATIC','QUARTER','BETWEEN',150,180,'SENIOR_II',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,'The range is supporting context; cross-team leadership can justify manager override.','RANGE'),
('32000000-0000-0000-0000-000000000031','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',8,null,'SENIOR_II',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000032','FINAL_AUDITS','Completed task audits','Final task-review evidence','jira','final_task_reviews','AUTOMATIC_WITH_REVIEW','QUARTER','>=',40,null,'SENIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Audit quality and technical responsibility must be confirmed.','PROGRESS'),
('32000000-0000-0000-0000-000000000033','ANALYSIS_APPROVALS','Major analysis approvals','Technical approval evidence','jira','analysis_approvals','AUTOMATIC_WITH_REVIEW','QUARTER','>=',8,null,'SENIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Only substantive technical analyses count.','PROGRESS'),
('32000000-0000-0000-0000-000000000034','ENGINEERING_IMPROVEMENT','Engineering improvement initiative','Evidence and manager confirmation of outcome and adoption','human','engineering_improvements','MANAGER_REVIEWED','QUARTER','>=',1,null,'SENIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Led an improvement with measurable team or platform impact.','RUBRIC'),
('32000000-0000-0000-0000-000000000035','OWNERSHIP','Multiple critical domains','Manager rubric supported by cross-system evidence','human','ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'SENIOR_II',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Sustained ownership and influence across multiple critical domains.','RUBRIC'),

('32000000-0000-0000-0000-000000000036','CORE_VELOCITY','Critical platform velocity range','Completed Jira points for explicitly classified critical platform work','jira','core_story_points','AUTOMATIC_WITH_REVIEW','QUARTER','BETWEEN',100,150,'PRINCIPAL',2,'PUBLISHED','2026-07-13','SUM',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',false,'Critical/core classification must be evidenced and reviewable.','RANGE'),
('32000000-0000-0000-0000-000000000037','QA_RATIO','QA return ratio','Attributed QA defects divided by QA-tested completed tasks','jira','qa_defects','AUTOMATIC_WITH_REVIEW','QUARTER','<',5,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','RATIO','qa_tested_completed_tasks','COMPLETE',false,'COMMON',null,null,'ALLOWED',true,null,'RATIO'),
('32000000-0000-0000-0000-000000000038','ANALYSIS_APPROVALS','Major analysis approvals','Technical approval evidence','jira','analysis_approvals','AUTOMATIC_WITH_REVIEW','QUARTER','>=',10,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Only substantive technical analyses count.','PROGRESS'),
('32000000-0000-0000-0000-000000000039','FINAL_AUDITS','Critical task approvals','Critical final task-review evidence','jira','final_task_reviews','AUTOMATIC_WITH_REVIEW','QUARTER','>=',40,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','COUNT',null,'COMPLETE',false,'COMMON',null,null,'PROGRESS_ONLY',true,'Only critical final approvals with technical accountability count.','PROGRESS'),
('32000000-0000-0000-0000-000000000040','STRATEGIC_IMPROVEMENT','Major platform/process improvement','Evidence and manager confirmation of outcome and adoption','human','strategic_initiatives','MANAGER_REVIEWED','QUARTER','>=',1,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'A major improvement with organization-level impact and adoption.','RUBRIC'),
('32000000-0000-0000-0000-000000000041','ENGINEERING_STANDARDS','Organization-wide standards adoption','Human assessment with linked adoption evidence','human','engineering_standards','MANAGER_REVIEWED','QUARTER','>=',1,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Defines standards and demonstrates meaningful organization-wide adoption.','RUBRIC'),
('32000000-0000-0000-0000-000000000042','ARCHITECTURE','Strategic architecture leadership','Human assessment with architecture decisions and outcomes','human','architecture','MANAGER_REVIEWED','QUARTER','>=',1,null,'PRINCIPAL',2,'PUBLISHED','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'COMMON',null,null,'ALLOWED',true,'Leads long-term architecture decisions with organization-level consequences.','RUBRIC');

insert into competency_expectation(id,competency_key,level_code,track_code,expectation,rubric_text,version) values
('33000000-0000-0000-0000-000000000001','TECHNICAL_SKILLS','JUNIOR_I',null,'Learns fundamentals',null,2),
('33000000-0000-0000-0000-000000000002','TECHNICAL_SKILLS','JUNIOR_II',null,'Consistent implementation',null,2),
('33000000-0000-0000-0000-000000000003','TECHNICAL_SKILLS','MID_I',null,'Independent delivery',null,2),
('33000000-0000-0000-0000-000000000004','TECHNICAL_SKILLS','MID_II',null,'Advanced implementation',null,2),
('33000000-0000-0000-0000-000000000005','TECHNICAL_SKILLS','SENIOR_I',null,'Expert implementation',null,2),
('33000000-0000-0000-0000-000000000006','TECHNICAL_SKILLS','SENIOR_II',null,'Technical leader',null,2),
('33000000-0000-0000-0000-000000000007','TECHNICAL_SKILLS','PRINCIPAL',null,'Organization expert',null,2),
('33000000-0000-0000-0000-000000000008','INDEPENDENCE','JUNIOR_I',null,'High guidance',null,2),
('33000000-0000-0000-0000-000000000009','INDEPENDENCE','JUNIOR_II',null,'Limited guidance',null,2),
('33000000-0000-0000-0000-000000000010','INDEPENDENCE','MID_I',null,'Independent',null,2),
('33000000-0000-0000-0000-000000000011','INDEPENDENCE','MID_II',null,'Self-directed',null,2),
('33000000-0000-0000-0000-000000000012','INDEPENDENCE','SENIOR_I',null,'Fully autonomous',null,2),
('33000000-0000-0000-0000-000000000013','INDEPENDENCE','SENIOR_II',null,'Organizational ownership',null,2),
('33000000-0000-0000-0000-000000000014','INDEPENDENCE','PRINCIPAL',null,'Strategic autonomy',null,2),
('33000000-0000-0000-0000-000000000015','CODE_REVIEWS','JUNIOR_I',null,'Responds to feedback',null,2),
('33000000-0000-0000-0000-000000000016','CODE_REVIEWS','JUNIOR_II',null,'Active participant',null,2),
('33000000-0000-0000-0000-000000000017','CODE_REVIEWS','MID_I',null,'Regular reviewer',null,2),
('33000000-0000-0000-0000-000000000018','CODE_REVIEWS','MID_II',null,'Advanced reviewer',null,2),
('33000000-0000-0000-0000-000000000019','CODE_REVIEWS','SENIOR_I',null,'Final reviewer',null,2),
('33000000-0000-0000-0000-000000000020','CODE_REVIEWS','SENIOR_II',null,'Audit authority',null,2),
('33000000-0000-0000-0000-000000000021','CODE_REVIEWS','PRINCIPAL',null,'Defines review standards',null,2),
('33000000-0000-0000-0000-000000000022','OWNERSHIP','JUNIOR_I',null,'Individual tasks',null,2),
('33000000-0000-0000-0000-000000000023','OWNERSHIP','JUNIOR_II',null,'Small functional area',null,2),
('33000000-0000-0000-0000-000000000024','OWNERSHIP','MID_I',null,'One service or module',null,2),
('33000000-0000-0000-0000-000000000025','OWNERSHIP','MID_II',null,'Multiple services',null,2),
('33000000-0000-0000-0000-000000000026','OWNERSHIP','SENIOR_I',null,'Major domain',null,2),
('33000000-0000-0000-0000-000000000027','OWNERSHIP','SENIOR_II',null,'Critical domains',null,2),
('33000000-0000-0000-0000-000000000028','OWNERSHIP','PRINCIPAL',null,'Platform ownership',null,2),
('33000000-0000-0000-0000-000000000029','MENTORING','JUNIOR_I',null,'Learns from others',null,2),
('33000000-0000-0000-0000-000000000030','MENTORING','JUNIOR_II',null,'Supports peers',null,2),
('33000000-0000-0000-0000-000000000031','MENTORING','MID_I',null,'Mentors Juniors',null,2),
('33000000-0000-0000-0000-000000000032','MENTORING','MID_II',null,'Regular mentor',null,2),
('33000000-0000-0000-0000-000000000033','MENTORING','SENIOR_I',null,'Team mentor',null,2),
('33000000-0000-0000-0000-000000000034','MENTORING','SENIOR_II',null,'Senior mentor',null,2),
('33000000-0000-0000-0000-000000000035','MENTORING','PRINCIPAL',null,'Organizational mentor',null,2),
('33000000-0000-0000-0000-000000000036','ARCHITECTURE','JUNIOR_I',null,'Learns concepts',null,2),
('33000000-0000-0000-0000-000000000037','ARCHITECTURE','JUNIOR_II',null,'Understands designs',null,2),
('33000000-0000-0000-0000-000000000038','ARCHITECTURE','MID_I',null,'Contributes',null,2),
('33000000-0000-0000-0000-000000000039','ARCHITECTURE','MID_II',null,'Reviews solutions',null,2),
('33000000-0000-0000-0000-000000000040','ARCHITECTURE','SENIOR_I',null,'Designs systems',null,2),
('33000000-0000-0000-0000-000000000041','ARCHITECTURE','SENIOR_II',null,'Leads architecture',null,2),
('33000000-0000-0000-0000-000000000042','ARCHITECTURE','PRINCIPAL',null,'Defines architecture',null,2),
('33000000-0000-0000-0000-000000000043','BUSINESS_UNDERSTANDING','JUNIOR_I',null,'Basic',null,2),
('33000000-0000-0000-0000-000000000044','BUSINESS_UNDERSTANDING','JUNIOR_II',null,'Working knowledge',null,2),
('33000000-0000-0000-0000-000000000045','BUSINESS_UNDERSTANDING','MID_I',null,'Good understanding',null,2),
('33000000-0000-0000-0000-000000000046','BUSINESS_UNDERSTANDING','MID_II',null,'Strong understanding',null,2),
('33000000-0000-0000-0000-000000000047','BUSINESS_UNDERSTANDING','SENIOR_I',null,'Excellent',null,2),
('33000000-0000-0000-0000-000000000048','BUSINESS_UNDERSTANDING','SENIOR_II',null,'Cross-domain expertise',null,2),
('33000000-0000-0000-0000-000000000049','BUSINESS_UNDERSTANDING','PRINCIPAL',null,'Strategic understanding',null,2),
('33000000-0000-0000-0000-000000000050','CROSS_TEAM_IMPACT','JUNIOR_I',null,'None',null,2),
('33000000-0000-0000-0000-000000000051','CROSS_TEAM_IMPACT','JUNIOR_II',null,'Limited',null,2),
('33000000-0000-0000-0000-000000000052','CROSS_TEAM_IMPACT','MID_I',null,'Occasional',null,2),
('33000000-0000-0000-0000-000000000053','CROSS_TEAM_IMPACT','MID_II',null,'Regular',null,2),
('33000000-0000-0000-0000-000000000054','CROSS_TEAM_IMPACT','SENIOR_I',null,'High',null,2),
('33000000-0000-0000-0000-000000000055','CROSS_TEAM_IMPACT','SENIOR_II',null,'Organization-wide',null,2),
('33000000-0000-0000-0000-000000000056','CROSS_TEAM_IMPACT','PRINCIPAL',null,'Company-wide',null,2);

-- Candidate track criteria are deliberately drafts until their exact company definitions and
-- data sources are approved. They demonstrate that new tracks require configuration, not code.
insert into criterion(id,code,name,description,source_tool,metric_key,evaluation_type,period_type,operator,threshold_value,threshold_max_value,level_code,version,status,effective_from,aggregation,denominator_metric_key,minimum_coverage,custom_period_allowed,criterion_scope,track_code,team_key,proration_policy,mandatory_criterion,rubric_text,visualization_key) values
('34000000-0000-0000-0000-000000000001','BE_OPERATIONAL_OWNERSHIP','Backend operational ownership','Service reliability, observability, incident response and runbook evidence','human','backend_operational_ownership','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_II',2,'DRAFT','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'TRACK','BACKEND',null,'ALLOWED',false,'Assess concrete operational outcomes, not repository activity alone.','RUBRIC'),
('34000000-0000-0000-0000-000000000002','FE_PRODUCT_QUALITY','Frontend product quality','Accessibility, browser stability, automated UI coverage and performance evidence','human','frontend_product_quality','MANAGER_REVIEWED','QUARTER','>=',1,null,'MID_II',2,'DRAFT','2026-07-13','COUNT',null,'HUMAN_REVIEW',true,'TRACK','FRONTEND',null,'ALLOWED',false,'Assess user-facing outcomes with links to tests, monitoring and released work.','RUBRIC');
