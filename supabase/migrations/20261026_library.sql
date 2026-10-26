-- ════════════════════════════════════════════════════════════
--  SoundFlow v0.5.0：顏色標籤、播放清單（雲端同步）、垃圾桶與永久刪除
--  倬 2026-10-26 同意（選「雲端同步」，新增 4 張資料表）。套用到 AI_News。
--
--  刪除流程：App 設 sf_track.deleted_at（進垃圾桶）→ 7 天後或使用者按「立即永久刪除」（purge_requested_at）
--  → N100 刪除實體檔（YouTube 一律刪；Podcast 只有 delete_source = true 才刪新知雷達的 nlm 音檔）
--  → 設 purged_at。sf_track 這一列保留（hidden），避免 nlm_job 觸發器把 Podcast 又加回來。
--  回滾：drop table public.sf_playlist_item, public.sf_playlist, public.sf_track_label, public.sf_label;
--        alter table public.sf_track drop column delete_source, drop column purge_requested_at, drop column purged_at;
-- ════════════════════════════════════════════════════════════

alter table public.sf_track
  add column if not exists delete_source      boolean not null default false,
  add column if not exists purge_requested_at timestamptz,
  add column if not exists purged_at          timestamptz;
comment on column public.sf_track.delete_source is 'Podcast 永久刪除時是否連新知雷達的 nlm 音檔一起刪（YouTube 一律刪）';
create index if not exists sf_track_trash_idx on public.sf_track (deleted_at) where deleted_at is not null and purged_at is null;

-- ── 顏色標籤 ─────────────────────────────────────────────────
create table if not exists public.sf_label (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(name) between 1 and 30),
  color      text not null,                 -- App 色票代號（teal、amber…），顏色值只在 App 的 SfColors
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);
comment on table public.sf_label is 'SoundFlow：顏色標籤';

create table if not exists public.sf_track_label (
  track_id   uuid not null references public.sf_track (id) on delete cascade,
  label_id   uuid not null references public.sf_label (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (track_id, label_id)
);
create index if not exists sf_track_label_label_idx on public.sf_track_label (label_id);

-- ── 播放清單 ─────────────────────────────────────────────────
create table if not exists public.sf_playlist (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(name) between 1 and 40),
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
comment on table public.sf_playlist is 'SoundFlow：自訂播放清單';

create table if not exists public.sf_playlist_item (
  playlist_id uuid not null references public.sf_playlist (id) on delete cascade,
  track_id    uuid not null references public.sf_track (id) on delete cascade,
  sort_order  integer not null default 0,
  added_at    timestamptz not null default now(),
  primary key (playlist_id, track_id)
);
create index if not exists sf_playlist_item_track_idx on public.sf_playlist_item (track_id);

drop trigger if exists sf_playlist_touch on public.sf_playlist;
create trigger sf_playlist_touch before update on public.sf_playlist
  for each row execute function soundflow_private.touch_updated_at();

-- ── RLS：白名單成員可完整讀寫（單人使用，成員都是倬自己的信箱）─────────
do $$
declare t text;
begin
  foreach t in array array['sf_label', 'sf_track_label', 'sf_playlist', 'sf_playlist_item'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists "members all" on public.%I', t);
    execute format('create policy "members all" on public.%I for all to authenticated
                    using ((select soundflow_private.is_member())) with check ((select soundflow_private.is_member()))', t);
  end loop;
end $$;
