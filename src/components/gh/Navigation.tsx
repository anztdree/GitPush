'use client';

import { House, Upload, CircleUserRound, Bell } from 'lucide-react';
import { useAppStore } from '@/lib/gh/store';
import { cn } from '@/lib/utils';
import { Avatar } from './bits';

// ─── Top header ──────────────────────────────────────────────────────────────

export function Header() {
  const user = useAppStore((s) => s.user);
  const demo = useAppStore((s) => s.demo);
  const setTab = useAppStore((s) => s.setTab);

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#30363d] bg-[#0d1117]/95 px-4 backdrop-blur">
      <div className="flex items-center gap-2.5">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#238636]">
          <Upload className="h-4 w-4 text-white" aria-hidden />
        </span>
        <span className="text-base font-bold tracking-tight">GitPush</span>
        {demo && (
          <span className="rounded-full border border-[#d2992240] bg-[#bb800926] px-2 py-0.5 text-[10px] font-bold tracking-wide text-[#e3b341]">
            DEMO
          </span>
        )}
      </div>
      <button
        onClick={() => setTab('profile')}
        aria-label="Buka profil"
        className="rounded-full outline-none ring-[#3fb950] transition-transform hover:scale-105 focus-visible:ring-2"
      >
        <Avatar src={user?.avatar_url || null} name={user?.name || user?.login || '?'} className="h-8 w-8 text-xs" />
      </button>
    </header>
  );
}

// ─── Bottom navigation ───────────────────────────────────────────────────────

export function BottomNav() {
  const tab = useAppStore((s) => s.tab);
  const setTab = useAppStore((s) => s.setTab);
  const openUpload = useAppStore((s) => s.openUpload);
  const uploadOpen = useAppStore((s) => s.uploadOpen);
  const closeOverlays = useAppStore((s) => s.closeOverlays);
  const notifUnread = useAppStore((s) => s.notifUnread);

  function goTab(next: 'home' | 'notifs' | 'profile') {
    closeOverlays();
    setTab(next);
  }

  return (
    <nav
      aria-label="Navigasi utama"
      className="flex h-16 shrink-0 items-stretch justify-around border-t border-[#30363d] bg-[#161b22] pb-[env(safe-area-inset-bottom)]"
    >
      <NavItem
        icon={<House className="h-5 w-5" aria-hidden />}
        label="Beranda"
        active={tab === 'home'}
        onClick={() => goTab('home')}
      />
      <NavItem
        icon={<Bell className="h-5 w-5" aria-hidden />}
        label="Notifikasi"
        active={tab === 'notifs'}
        onClick={() => goTab('notifs')}
        badge={notifUnread > 0 ? notifUnread : undefined}
      />
      <button
        onClick={() => openUpload()}
        aria-label="Upload massal"
        aria-current={uploadOpen ? 'true' : undefined}
        className="flex min-w-16 flex-col items-center justify-center gap-0.5 px-3 pt-2"
      >
        <span
          className={cn(
            'flex h-11 w-11 -mt-1 items-center justify-center rounded-full shadow-lg transition-all',
            uploadOpen
              ? 'bg-[#2ea043] shadow-[#23863666] scale-105'
              : 'bg-[#238636] shadow-[#23863644] hover:bg-[#2ea043]'
          )}
        >
          <Upload className="h-5 w-5 text-white" aria-hidden />
        </span>
        <span className={cn('text-[10px] font-medium', uploadOpen ? 'text-[#3fb950]' : 'text-[#8b949e]')}>
          Unggah
        </span>
      </button>
      <NavItem
        icon={<CircleUserRound className="h-5 w-5" aria-hidden />}
        label="Profil"
        active={tab === 'profile'}
        onClick={() => goTab('profile')}
      />
    </nav>
  );
}

function NavItem({
  icon,
  label,
  active,
  onClick,
  badge,
}: {
  icon: React.ReactNode;
  label: string;
  active: boolean;
  onClick: () => void;
  badge?: number;
}) {
  return (
    <button
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
      className={cn(
        'relative flex min-w-16 flex-col items-center justify-center gap-1 px-3 transition-colors',
        active ? 'text-[#3fb950]' : 'text-[#8b949e] hover:text-[#e6edf3]'
      )}
    >
      <span className="relative">
        {icon}
        {badge !== undefined && badge > 0 && (
          <span
            className="absolute -right-2.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#1f6feb] px-1 text-[9px] font-bold leading-none text-white"
            aria-label={`${badge} notifikasi belum dibaca`}
          >
            {badge > 9 ? '9+' : badge}
          </span>
        )}
      </span>
      <span className="text-[10px] font-medium">{label}</span>
    </button>
  );
}
