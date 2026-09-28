'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Bell,
  CheckCheck,
  CircleCheck,
  CircleDot,
  ExternalLink,
  GitPullRequest,
  MessageCircle,
  RefreshCw,
  ShieldAlert,
  Tag,
} from 'lucide-react';
import {
  fetchAllNotifications,
  markAllNotificationsRead,
  markThreadRead,
} from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { useAppStore } from '@/lib/gh/store';
import { timeAgoId } from '@/lib/gh/format';
import type { GHNotification } from '@/lib/gh/types';
import { useToast } from '@/hooks/use-toast';
import { EmptyState, ErrorCard, Spinner } from './bits';

// ─── Label & icon maps ───────────────────────────────────────────────────────

const REASON_LABEL: Record<string, string> = {
  assign: 'Ditugaskan',
  author: 'Penulis',
  comment: 'Komentar',
  ci_activity: 'CI',
  manual: 'Manual',
  mention: 'Penyebutan',
  push: 'Push',
  release: 'Rilis',
  review_requested: 'Minta review',
  security_alert: 'Keamanan',
  state_change: 'Perubahan status',
  subscription: 'Langganan',
  team_mention: 'Tim',
};

const SUBJECT_STYLE: Record<string, { icon: React.ComponentType<{ className?: string }>; color: string; label: string }> = {
  PullRequest: { icon: GitPullRequest, color: '#3fb950', label: 'Pull Request' },
  Issue: { icon: CircleDot, color: '#a371f7', label: 'Issue' },
  Release: { icon: Tag, color: '#f0883e', label: 'Release' },
  Discussion: { icon: MessageCircle, color: '#a371f7', label: 'Diskusi' },
  CheckSuite: { icon: CircleCheck, color: '#3fb950', label: 'CI' },
  RepositoryVulnerabilityAlert: { icon: ShieldAlert, color: '#f85149', label: 'Keamanan' },
};

function subjectStyle(type: string) {
  return SUBJECT_STYLE[type] ?? { icon: Bell, color: '#8b949e', label: type };
}

// ─── View ────────────────────────────────────────────────────────────────────

export function NotificationsView() {
  const setNotifUnread = useAppStore((s) => s.setNotifUnread);
  const { toast } = useToast();

  const [items, setItems] = useState<GHNotification[] | null>(null);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyAll, setBusyAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchAllNotifications();
      setItems(list);
      setNotifUnread(list.filter((n) => n.unread).length);
    } catch (e) {
      setError(e instanceof GHError ? e.message : 'Gagal memuat notifikasi.');
    } finally {
      setLoading(false);
    }
  }, [setNotifUnread]);

  useEffect(() => {
    void load();
  }, [load]);

  async function openNotif(n: GHNotification) {
    if (items) {
      const next = items.map((x) => (x.id === n.id ? { ...x, unread: false } : x));
      setItems(next);
      setNotifUnread(next.filter((x) => x.unread).length);
    }
    if (n.unread) {
      try {
        await markThreadRead(n.id);
      } catch {
        /* penandaan gagal tidak fatal */
      }
    }
    window.open(n.repository.html_url, '_blank', 'noopener');
  }

  async function markAll() {
    setBusyAll(true);
    try {
      await markAllNotificationsRead();
      setItems((cur) => cur?.map((x) => ({ ...x, unread: false })) ?? null);
      setNotifUnread(0);
      toast({ title: 'Semua notifikasi ditandai dibaca' });
    } catch (e) {
      toast({
        title: 'Gagal menandai semua',
        description: e instanceof GHError ? e.message : 'Coba lagi nanti.',
        variant: 'destructive',
      });
    } finally {
      setBusyAll(false);
    }
  }

  const unreadCount = items?.filter((n) => n.unread).length ?? 0;
  const visible = !items ? [] : filter === 'unread' ? items.filter((n) => n.unread) : items;

  return (
    <main className="flex-1 overflow-y-auto overscroll-contain">
      <div className="mx-auto w-full max-w-2xl px-4 pb-8 pt-5">
        {/* Title + actions */}
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-bold tracking-tight">Notifikasi</h1>
          {unreadCount > 0 && (
            <span className="rounded-full bg-[#1f6febd0] px-2 py-0.5 text-[10px] font-bold text-white">
              {unreadCount} baru
            </span>
          )}
          <div className="ml-auto flex items-center gap-1">
            <button
              onClick={markAll}
              disabled={busyAll || unreadCount === 0}
              aria-label="Tandai semua dibaca"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3] disabled:opacity-40"
            >
              {busyAll ? <Spinner /> : <CheckCheck className="h-4 w-4" aria-hidden />}
            </button>
            <button
              onClick={() => void load()}
              aria-label="Muat ulang notifikasi"
              className="flex h-8 w-8 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
            >
              {loading ? <Spinner /> : <RefreshCw className="h-4 w-4" aria-hidden />}
            </button>
          </div>
        </div>

        {/* Filter pills */}
        <div className="mt-3 flex gap-2" role="tablist" aria-label="Filter notifikasi">
          {(
            [
              ['all', 'Semua'],
              ['unread', unreadCount > 0 ? `Belum dibaca (${unreadCount})` : 'Belum dibaca'],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              role="tab"
              aria-selected={filter === value}
              onClick={() => setFilter(value)}
              className={
                filter === value
                  ? 'rounded-full border border-[#3fb950] bg-[#23863626] px-3 py-1 text-xs font-semibold text-[#3fb950]'
                  : 'rounded-full border border-[#30363d] px-3 py-1 text-xs font-medium text-[#8b949e] transition-colors hover:border-[#8b949e] hover:text-[#e6edf3]'
              }
            >
              {label}
            </button>
          ))}
        </div>

        {error && (
          <div className="mt-4">
            <ErrorCard message={error} onRetry={() => void load()} />
          </div>
        )}

        {loading && !items && (
          <div className="mt-4 space-y-2" aria-hidden>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="flex animate-pulse items-center gap-3 rounded-xl border border-[#21262d] bg-[#161b22] px-4 py-3">
                <div className="h-8 w-8 rounded-lg bg-[#21262d]" />
                <div className="flex-1">
                  <div className="h-3 w-4/5 rounded bg-[#21262d]" />
                  <div className="mt-2 h-3 w-2/5 rounded bg-[#21262d]" />
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && !error && visible.length === 0 && (
          <div className="mt-4">
            <EmptyState
              icon={Bell}
              title={filter === 'unread' ? 'Tidak ada yang belum dibaca' : 'Tidak ada notifikasi'}
              desc={
                filter === 'unread'
                  ? 'Semua notifikasi sudah kamu baca. Kerja bagus! 🎉'
                  : 'Notifikasi issue, pull request, dan rilis akan tampil di sini.'
              }
            />
          </div>
        )}

        {/* List */}
        {!error && visible.length > 0 && (
          <ul className="mt-4 divide-y divide-[#21262d] overflow-hidden rounded-xl border border-[#30363d] bg-[#161b22]">
            {visible.map((n) => {
              const st = subjectStyle(n.subject.type);
              const Icon = st.icon;
              return (
                <li key={n.id}>
                  <button
                    onClick={() => void openNotif(n)}
                    className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-[#1c2128]"
                  >
                    <span
                      className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                      style={{ backgroundColor: `${st.color}1f`, color: st.color }}
                      aria-hidden
                    >
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={n.unread ? 'block truncate text-sm font-semibold text-[#e6edf3]' : 'block truncate text-sm text-[#c9d1d9]'}>
                        {n.subject.title}
                      </span>
                      <span className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-[#8b949e]">
                        <span className="truncate">{n.repository.full_name}</span>
                        <span className="rounded-full border border-[#30363d] px-1.5 py-px text-[10px]">
                          {REASON_LABEL[n.reason] ?? n.reason}
                        </span>
                        <span>{timeAgoId(n.updated_at)}</span>
                      </span>
                    </span>
                    {n.unread ? (
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-[#58a6ff]" aria-label="Belum dibaca" />
                    ) : (
                      <ExternalLink className="mt-1 h-3.5 w-3.5 shrink-0 text-[#6e7681]" aria-hidden />
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </main>
  );
}
