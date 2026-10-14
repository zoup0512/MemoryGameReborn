-- Preserve full admin reasons (up to 500 characters) and applied config values.
-- Standard syntax supported by both H2 and PostgreSQL.
alter table pk_admin_audit alter column params_json set data type varchar(8192);
