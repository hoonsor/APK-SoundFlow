-- ════════════════════════════════════════════════════════════
--  SoundFlow v0.4.0：YouTube 分享 → N100 轉檔
--  倬 2026-10-26 同意（「請先開始」）。套用到 AI_News。
--
--  yt_job：App 送出、N100 撿起來轉檔；sf_worker_heartbeat：N100 每 5 分鐘回報
--  Storage bucket sf：**私人**（YouTube 音檔只給白名單成員播放，不公開）
--  回滾：drop table public.yt_job, public.sf_worker_heartbeat;
--        alter table public.sf_track drop column video_id;
--        delete from storage.buckets where id = 'sf';（要先清空檔案）
-- ════════════════════════════════════════════════════════════

-- sf_track 加 video_id：同一支影片不重複轉檔
alter table public.sf_track add column if not exists video_id text;
create unique index if not exists sf_track_video_id_key on public.sf_track (video_id) where video_id is not null;

-- ── 轉檔工作 ──────────────────────────────────────────────────
create table if not exists public.yt_job (
  id           uuid primary key default gen_random_uuid(),
  url          text not null,
  video_id     text not null,
  status       text not null default 'queued'
               check (status in ('queued', 'processing', 'done', 'failed', 'cancelled')),
  error        text,
  attempts     integer not null default 0,
  title        text,                         -- N100 取得原標題後填入，App 顯示用
  track_id     uuid references public.sf_track (id) on delete set null,
  requested_at timestamptz not null default now(),
  started_at   timestamptz,
  updated_at   timestamptz not null default now()
);
comment on table public.yt_job is 'SoundFlow：YouTube 轉檔工作（App 新增、N100 處理）';
create index if not exists yt_job_status_idx on public.yt_job (status, requested_at);
-- 同一支影片同時只能有一個排隊或轉檔中的工作
create unique index if not exists yt_job_active_video_key on public.yt_job (video_id) where status in ('queued', 'processing');

alter table public.yt_job enable row level security;
drop policy if exists "members read" on public.yt_job;
create policy "members read" on public.yt_job
  for select to authenticated using ((select soundflow_private.is_member()));
drop policy if exists "members enqueue" on public.yt_job;
create policy "members enqueue" on public.yt_job
  for insert to authenticated
  with check ((select soundflow_private.is_member()) and status = 'queued');
-- 成員只能「重試」（改回 queued）或「取消」；轉檔結果只有 N100（service role）能寫
drop policy if exists "members retry or cancel" on public.yt_job;
create policy "members retry or cancel" on public.yt_job
  for update to authenticated
  using ((select soundflow_private.is_member()))
  with check ((select soundflow_private.is_member()) and status in ('queued', 'cancelled'));

drop trigger if exists yt_job_touch on public.yt_job;
create trigger yt_job_touch before update on public.yt_job
  for each row execute function soundflow_private.touch_updated_at();

-- ── N100 心跳 ─────────────────────────────────────────────────
create table if not exists public.sf_worker_heartbeat (
  worker         text primary key,
  seen_at        timestamptz not null default now(),
  version        text,
  yt_dlp_version text,
  info           jsonb
);
comment on table public.sf_worker_heartbeat is 'SoundFlow：N100 轉檔服務的最後上線時間';
alter table public.sf_worker_heartbeat enable row level security;
drop policy if exists "members read" on public.sf_worker_heartbeat;
create policy "members read" on public.sf_worker_heartbeat
  for select to authenticated using ((select soundflow_private.is_member()));

-- ── 私人 Storage bucket：YouTube 音檔 ─────────────────────────────
--  免費方案單檔上限 50 MB；N100 依長度調整位元率（見 worker/），超過 3 小時拒絕
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('sf', 'sf', false, 52428800, array['audio/ogg', 'audio/opus', 'audio/webm', 'audio/mp4', 'audio/mpeg'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "soundflow members read sf" on storage.objects;
create policy "soundflow members read sf" on storage.objects
  for select to authenticated
  using (bucket_id = 'sf' and (select soundflow_private.is_member()));
