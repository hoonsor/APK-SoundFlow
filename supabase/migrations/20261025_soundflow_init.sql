-- ════════════════════════════════════════════════════════════
--  SoundFlow v0.2.0：成員白名單、統一音軌表、從新知雷達 nlm_job 自動同步 Podcast
--  套用到 Supabase 專案 AI_News（與 27 號新知雷達共用）。倬 2026-10-25 同意。
--
--  不改 nlm_job 的欄位、資料與 RLS；只在上面加一個 AFTER 觸發器，網站不受影響。
--  回滾：drop trigger sf_from_nlm_job on public.nlm_job;
--        drop table public.sf_track, public.sf_member; drop schema soundflow_private cascade;
-- ════════════════════════════════════════════════════════════

create schema if not exists soundflow_private;
revoke all on schema soundflow_private from public, anon, authenticated;

-- 套用前快照（還原：insert into public.nlm_job select * from soundflow_private.nlm_job_backup_20261025 on conflict do nothing）
create table if not exists soundflow_private.nlm_job_backup_20261025 as select * from public.nlm_job;

-- ── 成員白名單：只有名單內的信箱登入後能讀寫 SoundFlow 的資料 ─────
create table if not exists public.sf_member (
  email      text primary key,
  created_at timestamptz not null default now()
);
comment on table public.sf_member is 'SoundFlow：可使用 App 的信箱白名單';
alter table public.sf_member enable row level security;   -- 不開任何 policy：用戶端讀不到名單

-- 加成員（repo 是公開的，信箱不寫在這裡）：在 SQL Editor 執行
--   insert into public.sf_member (email) values ('<小寫信箱>') on conflict do nothing;

-- security definer 放在私有 schema：避免 RLS 遞迴，也不會被當成 RPC 呼叫
create or replace function soundflow_private.is_member()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.sf_member m where m.email = lower(coalesce(auth.jwt() ->> 'email', '')));
$$;
grant usage on schema soundflow_private to authenticated;
grant execute on function soundflow_private.is_member() to authenticated;

-- ── 統一音軌表：Podcast 與 YouTube 都在這裡，App 只讀這一張 ─────────
create table if not exists public.sf_track (
  id             uuid primary key default gen_random_uuid(),
  kind           text not null check (kind in ('podcast', 'youtube')),
  nlm_item_id    text unique,                 -- Podcast 對應 nlm_job.item_id
  title          text not null,               -- 顯示名稱（可改）
  original_title text not null,               -- 原標題（不變）
  channel        text,                        -- Podcast：來源（youtube／github…）；YouTube：頻道名
  source_url     text,
  thumbnail_url  text,
  duration_sec   integer,
  size_bytes     bigint,
  mime           text,
  tier           text not null default 'supabase' check (tier in ('supabase', 'drive')),
  storage_bucket text,                        -- tier = supabase 時的 bucket（Podcast 是 nlm）
  storage_path   text,
  drive_file_id  text,
  hidden         boolean not null default false,
  deleted_at     timestamptz,                 -- 垃圾桶
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
comment on table public.sf_track is 'SoundFlow：所有可播放的音軌（Podcast 由 nlm_job 觸發器同步，YouTube 由 N100 寫入）';
create index if not exists sf_track_kind_created_idx on public.sf_track (kind, created_at desc);

alter table public.sf_track enable row level security;
drop policy if exists "members read" on public.sf_track;
create policy "members read" on public.sf_track
  for select to authenticated using ((select soundflow_private.is_member()));
drop policy if exists "members update" on public.sf_track;
create policy "members update" on public.sf_track
  for update to authenticated
  using ((select soundflow_private.is_member()))
  with check ((select soundflow_private.is_member()));
-- 新增與刪除只由觸發器／N100（service role）做，用戶端不開放

create or replace function soundflow_private.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;
drop trigger if exists sf_track_touch on public.sf_track;
create trigger sf_track_touch before update on public.sf_track
  for each row execute function soundflow_private.touch_updated_at();

-- ── nlm_job 產生完成 → 同步到 sf_track ───────────────────────────
--  只更新原標題、長度、路徑；使用者改過的 title、hidden、deleted_at 不覆蓋
create or replace function soundflow_private.sync_from_nlm_job()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.sf_track as t
    (kind, nlm_item_id, title, original_title, channel, source_url, duration_sec, mime, tier, storage_bucket, storage_path, created_at)
  values
    ('podcast', new.item_id, new.title, new.title, new.source, new.url, new.audio_seconds, 'audio/mp4', 'supabase', 'nlm', new.audio_path, new.updated_at)
  on conflict (nlm_item_id) do update set
    original_title = excluded.original_title,
    title          = case when t.title = t.original_title then excluded.original_title else t.title end,
    channel        = excluded.channel,
    source_url     = excluded.source_url,
    duration_sec   = excluded.duration_sec,
    storage_path   = case when t.tier = 'supabase' then excluded.storage_path else t.storage_path end;
  return new;
end $$;

drop trigger if exists sf_from_nlm_job on public.nlm_job;
create trigger sf_from_nlm_job
  after insert or update of status, audio_path, audio_seconds, title on public.nlm_job
  for each row
  when (new.status = 'done' and new.audio_path is not null)
  execute function soundflow_private.sync_from_nlm_job();

-- ── 補既有的 Podcast ────────────────────────────────────────────
insert into public.sf_track
  (kind, nlm_item_id, title, original_title, channel, source_url, duration_sec, mime, tier, storage_bucket, storage_path, created_at)
select 'podcast', j.item_id, j.title, j.title, j.source, j.url, j.audio_seconds, 'audio/mp4', 'supabase', 'nlm', j.audio_path, j.updated_at
from public.nlm_job j
where j.status = 'done' and j.audio_path is not null
on conflict (nlm_item_id) do nothing;
