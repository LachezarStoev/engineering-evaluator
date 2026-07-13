create table employee (
  id uuid primary key, canonical_email varchar(320) not null unique, display_name varchar(200) not null,
  team varchar(120), manager_email varchar(320), current_level_code varchar(60), target_level_code varchar(60),
  employment_start date, probation_end date, active boolean not null default true, created_at timestamp with time zone not null
);
create table employee_alias (employee_id uuid not null references employee(id) on delete cascade, email varchar(320) not null unique, primary key(employee_id,email));
create table external_identity (
  id uuid primary key, employee_id uuid not null references employee(id) on delete cascade, tool_key varchar(80) not null,
  external_user_id varchar(200) not null, username varchar(200), matched_email varchar(320), verified boolean not null,
  unique(tool_key,external_user_id)
);
create table engineering_level (
  id uuid primary key, code varchar(60) not null, name varchar(120) not null, ordinal_value integer not null,
  version integer not null, status varchar(20) not null, effective_from date not null, unique(code,version)
);
create table criterion (
  id uuid primary key, code varchar(80) not null, name varchar(200) not null, description varchar(2000), source_tool varchar(80) not null,
  metric_key varchar(100) not null, evaluation_type varchar(40) not null, period_type varchar(30) not null,
  operator varchar(20) not null, threshold_value numeric(19,4), level_code varchar(60) not null,
  version integer not null, status varchar(20) not null, effective_from date not null, unique(code,level_code,version)
);
create table evidence (
  id uuid primary key, employee_id uuid not null references employee(id), tool_key varchar(80) not null, metric_key varchar(100) not null,
  external_id varchar(300) not null, occurred_at timestamp with time zone not null, numeric_value numeric(19,4), title varchar(500), url varchar(2000),
  attributes_json text not null, included boolean not null default true, exclusion_reason varchar(500), unique(tool_key,external_id,metric_key)
);
create table evaluation (
  id uuid primary key, employee_id uuid not null references employee(id), period varchar(20) not null, level_code varchar(60) not null,
  rule_version integer not null, status varchar(30) not null, finalized boolean not null, created_at timestamp with time zone not null,
  finalized_at timestamp with time zone, unique(employee_id,period,level_code,rule_version)
);
create table criterion_result (
  id uuid primary key, evaluation_id uuid not null references evaluation(id) on delete cascade, criterion_id uuid not null references criterion(id),
  measured_value numeric(19,4), threshold_value numeric(19,4), result_status varchar(30) not null, formula varchar(500) not null,
  coverage varchar(30) not null, manager_decision varchar(30), manager_note varchar(2000)
);
create table connector_config (
  id uuid primary key, tool_key varchar(80) not null unique, display_name varchar(120) not null, base_url varchar(1000), enabled boolean not null,
  allowed_scopes text, health_status varchar(30) not null, last_sync_at timestamp with time zone
);
create table dispute (
  id uuid primary key, evaluation_id uuid not null references evaluation(id), criterion_result_id uuid references criterion_result(id),
  author_email varchar(320) not null, message varchar(3000) not null, status varchar(30) not null, created_at timestamp with time zone not null
);
create table audit_event (
  id uuid primary key, actor_email varchar(320) not null, action varchar(120) not null, entity_type varchar(80) not null,
  entity_id varchar(120) not null, details_json text not null, created_at timestamp with time zone not null
);
