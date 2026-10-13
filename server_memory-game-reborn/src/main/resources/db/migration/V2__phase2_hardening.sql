-- Phase-2 hardening (P1/P2 review): settlement idempotency key, single
-- active season, and the hot query indexes. Every statement is
-- idempotent-safe (IF NOT EXISTS / guarded backfill) so baselined
-- deployments converge with fresh ones.

-- MatchRecord.voided: admin voids / shutdown aborts are excluded from
-- client history and the practice reward cap.
alter table pk_match_record add column if not exists voided boolean not null default false;

-- Settlement idempotency: one record per match, ever.
create unique index if not exists uk_pk_match_record_match_id on pk_match_record (match_id);

-- History queries (player_a = ? or player_b = ? order by settled_at desc).
create index if not exists idx_pk_match_record_player_a on pk_match_record (player_a, settled_at desc);
create index if not exists idx_pk_match_record_player_b on pk_match_record (player_b, settled_at desc);

-- Practice daily-cap count (player_a, mode, result_a, settled_at).
create index if not exists idx_pk_match_record_practice on pk_match_record (player_a, mode, result_a, settled_at);

-- Season single-active flag: many NULLs, at most one 1.
alter table pk_season add column if not exists active_flag integer;

-- Backfill: the newest non-finalized season (if any) becomes THE active
-- one; stale non-finalized rows are closed out.
update pk_season set finalized = true
 where finalized = false
   and id <> (select max(id) from pk_season where finalized = false);
update pk_season set active_flag = 1 where finalized = false;

create unique index if not exists uk_pk_season_active on pk_season (active_flag);

-- Admin audit browsing is newest-first.
create index if not exists idx_pk_admin_audit_ts on pk_admin_audit (ts desc);
