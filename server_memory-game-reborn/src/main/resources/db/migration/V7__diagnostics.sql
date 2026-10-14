-- TEXT deliberately avoids PostgreSQL OID/LOB semantics. H2 and PostgreSQL compatible.
create table pk_diagnostic (
    id varchar(36) primary key,
    user_id bigint not null,
    upload_id varchar(36) not null,
    schema_version integer not null,
    app_version varchar(80) not null,
    build_time varchar(80) not null,
    started_at bigint not null,
    ended_at bigint not null,
    match_id varchar(80),
    received_at bigint not null,
    bytes integer not null,
    fingerprint varchar(64) not null,
    content text not null,
    constraint uk_pk_diagnostic_upload unique(user_id, upload_id)
);
create index ix_pk_diagnostic_received on pk_diagnostic(received_at);
create index ix_pk_diagnostic_user_received on pk_diagnostic(user_id,received_at);
create index ix_pk_diagnostic_match_received on pk_diagnostic(match_id,received_at);
-- A single row is locked inside the same transaction as quota checks and insertion/cleanup.
create table pk_diagnostic_lock (id integer primary key);
insert into pk_diagnostic_lock(id) values(1);
