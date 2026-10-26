-- ════════════════════════════════════════════════════════════
--  SoundFlow v0.4.2：讓 App 知道「目前登入的信箱在不在白名單」
--  起因：倬用不在白名單的信箱登入，App 讀到空資料、寫入被拒，卻顯示「請重新登入」，看不出原因。
--  唯讀：只回傳自己的信箱與是否為成員，不會洩漏白名單內容。
--  回滾：drop function public.sf_whoami();
-- ════════════════════════════════════════════════════════════

create or replace function public.sf_whoami()
returns json
language sql
stable
security definer
set search_path = ''
as $$
  select json_build_object(
    'email', lower(coalesce(auth.jwt() ->> 'email', '')),
    'member', soundflow_private.is_member()
  );
$$;

revoke all on function public.sf_whoami() from public, anon;
grant execute on function public.sf_whoami() to authenticated;
