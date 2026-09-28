'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, Lock, Star, GitFork, RefreshCw, FolderGit2 } from 'lucide-react';
import { fetchRepos } from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { useAppStore } from '@/lib/gh/store';
import { langColor, timeAgoId } from '@/lib/gh/format';
import type { GHRepo } from '@/lib/gh/types';
import { EmptyState, ErrorCard, LangDot, Spinner } from './bits';

export function HomeView() {
  const user = useAppStore((s) => s.user);
  const openRepo = useAppStore((s) => s.openRepo);
  const refreshTick = useAppStore((s) => s.refreshTick);

  const [repos, setRepos] = useState<GHRepo[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRepos(await fetchRepos());
    } catch (e) {
      setError(e instanceof GHError ? e.message : 'Gagal memuat repositori.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load, refreshTick]);

  const filtered = useMemo(() => {
    if (!repos) return [];
    const q = query.trim().toLowerCase();
    if (!q) return repos;
    return repos.filter(
      (r) =>
        r.full_name.toLowerCase().includes(q) ||
        (r.description ?? '').toLowerCase().includes(q)
    );
  }, [repos, query]);

  return (
    <main className="flex-1 overflow-y-auto overscroll-contain">
      <div className="mx-auto w-full max-w-2xl px-4 pb-8 pt-5">
        {/* Greeting */}
        <h1 className="text-xl font-bold tracking-tight">
          Hai, {user?.name?.split(' ')[0] || user?.login} <span aria-hidden>👋</span>
        </h1>
        <p className="mt-0.5 text-sm text-[#8b949e]">Pilih repositori untuk menjelajah atau upload file.</p>

        {/* Search */}
        <div className="relative mt-4">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#6e7681]" aria-hidden />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Cari repositori…"
            aria-label="Cari repositori"
            className="w-full rounded-lg border border-[#30363d] bg-[#0d1117] py-2.5 pl-9 pr-3 text-sm outline-none transition-colors placeholder:text-[#6e7681] focus:border-[#3fb950] focus:ring-1 focus:ring-[#3fb950]"
          />
        </div>

        {/* List */}
        <div className="mt-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-[#8b949e]">
            Repositori {repos ? `(${filtered.length})` : ''}
          </h2>
          <button
            onClick={() => void load()}
            aria-label="Muat ulang daftar"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
          >
            {loading ? <Spinner /> : <RefreshCw className="h-4 w-4" aria-hidden />}
          </button>
        </div>

        {error && (
          <div className="mt-3">
            <ErrorCard message={error} onRetry={() => void load()} />
          </div>
        )}

        {loading && !repos && (
          <div className="mt-3 space-y-3" aria-hidden>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse rounded-xl border border-[#21262d] bg-[#161b22] p-4">
                <div className="h-4 w-2/5 rounded bg-[#21262d]" />
                <div className="mt-3 h-3 w-4/5 rounded bg-[#21262d]" />
                <div className="mt-3 h-3 w-1/3 rounded bg-[#21262d]" />
              </div>
            ))}
          </div>
        )}

        {!loading && !error && repos && filtered.length === 0 && (
          <div className="mt-3">
            <EmptyState
              icon={FolderGit2}
              title={query ? 'Tidak ada hasil' : 'Belum ada repositori'}
              desc={query ? `Tidak ditemukan repositori untuk "${query}".` : 'Repositori akun Anda akan tampil di sini.'}
            />
          </div>
        )}

        <ul className="mt-3 space-y-3">
          {filtered.map((repo) => (
            <li key={repo.id}>
              <button
                onClick={() =>
                  openRepo({
                    owner: repo.owner.login,
                    repo: repo.name,
                    path: '',
                    defaultBranch: repo.default_branch,
                  })
                }
                className="w-full rounded-xl border border-[#30363d] bg-[#161b22] p-4 text-left transition-all hover:border-[#8b949e] hover:bg-[#1c2128] active:scale-[0.995]"
              >
                <div className="flex items-center gap-2">
                  <span className="truncate font-semibold text-[#e6edf3]">
                    <span className="text-[#8b949e]">{repo.owner.login}/</span>
                    {repo.name}
                  </span>
                  <span className="ml-auto flex shrink-0 items-center gap-1 rounded-full border border-[#30363d] px-2 py-0.5 text-[10px] font-medium text-[#8b949e]">
                    {repo.private ? <Lock className="h-3 w-3" aria-hidden /> : null}
                    {repo.private ? 'Privat' : 'Publik'}
                  </span>
                </div>
                {repo.description && (
                  <p className="mt-1.5 line-clamp-2 text-sm leading-relaxed text-[#8b949e]">{repo.description}</p>
                )}
                <div className="mt-2.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-[#8b949e]">
                  <LangDot color={langColor(repo.language)} lang={repo.language} />
                  <span className="inline-flex items-center gap-1">
                    <Star className="h-3.5 w-3.5" aria-hidden /> {repo.stargazers_count}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <GitFork className="h-3.5 w-3.5" aria-hidden /> {repo.forks_count}
                  </span>
                  <span className="ml-auto">Diperbarui {timeAgoId(repo.pushed_at || repo.updated_at)}</span>
                </div>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
