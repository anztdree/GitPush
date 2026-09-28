'use client';

import { useState } from 'react';

import {
  Github,
  MapPin,
  Building2,
  LogOut,
  Trash2,
  ExternalLink,
  History,
  KeyRound,
  Info,
  Smartphone,
  CircleCheck,
  Download,
  MoreVertical,
  Share,
  Package,
} from 'lucide-react';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { useAppStore } from '@/lib/gh/store';
import { isNativeApp } from '@/lib/gh/api';
import { maskToken, shortSha, timeAgoId } from '@/lib/gh/format';
import { Avatar } from './bits';

export function ProfileView() {
  const user = useAppStore((s) => s.user);
  const token = useAppStore((s) => s.token);
  const demo = useAppStore((s) => s.demo);
  const history = useAppStore((s) => s.history);
  const signOut = useAppStore((s) => s.signOut);
  const clearHistory = useAppStore((s) => s.clearHistory);
  const installEvent = useAppStore((s) => s.installEvent);
  const installed = useAppStore((s) => s.installed);
  const [installBusy, setInstallBusy] = useState(false);

  async function handleInstall() {
    if (!installEvent) return;
    setInstallBusy(true);
    try {
      await installEvent.prompt();
      await installEvent.userChoice;
    } catch {
      /* user menutup prompt — abaikan */
    } finally {
      setInstallBusy(false);
    }
  }

  if (!user) return null;

  const stats = [
    { label: 'Repositori', value: user.public_repos },
    { label: 'Pengikut', value: user.followers },
    { label: 'Mengikuti', value: user.following },
  ];

  return (
    <main className="flex-1 overflow-y-auto overscroll-contain">
      <div className="mx-auto w-full max-w-2xl space-y-4 px-4 pb-8 pt-5">
        {/* profile card */}
        <section className="flex items-center gap-4 rounded-xl border border-[#30363d] bg-[#161b22] p-5">
          <Avatar src={user.avatar_url || null} name={user.name || user.login} className="h-16 w-16 text-lg" />
          <div className="min-w-0">
            <h1 className="truncate text-lg font-bold">{user.name || user.login}</h1>
            <p className="truncate text-sm text-[#8b949e]">@{user.login}</p>
            {demo && (
              <span className="mt-1 inline-block rounded-full border border-[#d2992240] bg-[#bb800926] px-2 py-0.5 text-[10px] font-bold text-[#e3b341]">
                MODE DEMO
              </span>
            )}
          </div>
        </section>

        {user.bio && (
          <p className="rounded-xl border border-[#30363d] bg-[#161b22] p-4 text-sm leading-relaxed text-[#8b949e]">
            {user.bio}
          </p>
        )}

        {/* meta rows */}
        <section className="divide-y divide-[#21262d] rounded-xl border border-[#30363d] bg-[#161b22]">
          {user.company && (
            <MetaRow icon={<Building2 className="h-4 w-4" aria-hidden />} text={user.company} />
          )}
          {user.location && <MetaRow icon={<MapPin className="h-4 w-4" aria-hidden />} text={user.location} />}
          <a
            href={user.html_url}
            target="_blank"
            rel="noreferrer"
            className="flex min-h-12 items-center gap-3 px-4 py-3 text-sm transition-colors hover:bg-[#1c2128]"
          >
            <Github className="h-4 w-4 text-[#8b949e]" aria-hidden />
            <span>Lihat profil di GitHub</span>
            <ExternalLink className="ml-auto h-3.5 w-3.5 text-[#6e7681]" aria-hidden />
          </a>
        </section>

        {/* stats */}
        <section className="grid grid-cols-3 gap-3">
          {stats.map((s) => (
            <div key={s.label} className="rounded-xl border border-[#30363d] bg-[#161b22] p-4 text-center">
              <p className="text-xl font-bold">{s.value}</p>
              <p className="mt-0.5 text-[11px] text-[#8b949e]">{s.label}</p>
            </div>
          ))}
        </section>

        {/* install sebagai aplikasi */}
        <section className="rounded-xl border border-[#30363d] bg-[#161b22] p-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <Smartphone className="h-4 w-4 text-[#3fb950]" aria-hidden />
            Pasang sebagai Aplikasi
          </h2>
          {installed ? (
            <p className="mt-2.5 flex items-center gap-2 text-xs text-[#3fb950]">
              <CircleCheck className="h-4 w-4 shrink-0" aria-hidden />
              GitPush sudah terpasang — Anda sedang menjalankannya sebagai aplikasi.
            </p>
          ) : installEvent ? (
            <>
              <p className="mt-2 text-[11px] leading-relaxed text-[#6e7681]">
                Pasang GitPush ke layar utama — tampil penuh tanpa address bar, dengan ikon sendiri,
                seperti aplikasi Android pada umumnya.
              </p>
              <button
                onClick={handleInstall}
                disabled={installBusy}
                className="mt-3 flex min-h-10 w-full items-center justify-center gap-2 rounded-lg bg-[#238636] text-sm font-semibold text-white transition-colors hover:bg-[#2ea043] disabled:opacity-60"
              >
                <Download className="h-4 w-4" aria-hidden />
                {installBusy ? 'Menyiapkan…' : 'Install aplikasi GitPush'}
              </button>
            </>
          ) : (
            <ol className="mt-2 space-y-2 text-[11px] leading-relaxed text-[#6e7681]">
              <li className="flex items-start gap-2">
                <MoreVertical className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#8b949e]" aria-hidden />
                <span>
                  <strong className="text-[#8b949e]">Android (Chrome):</strong> menu ⋮ di kanan atas →
                  <span className="text-[#8b949e]"> “Tambahkan ke layar utama”</span> atau “Install app”.
                </span>
              </li>
              <li className="flex items-start gap-2">
                <Share className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#8b949e]" aria-hidden />
                <span>
                  <strong className="text-[#8b949e]">iPhone (Safari):</strong> tombol Bagikan →
                  <span className="text-[#8b949e]"> “Add to Home Screen”</span>.
                </span>
              </li>
            </ol>
          )}
        </section>

        {/* unduh APK native */}
        {!isNativeApp && (
          <section className="rounded-xl border border-[#30363d] bg-[#161b22] p-4">
            <h2 className="flex items-center gap-2 text-sm font-semibold">
              <Package className="h-4 w-4 text-[#3fb950]" aria-hidden />
              APK Android (Native)
            </h2>
            <p className="mt-2 text-[11px] leading-relaxed text-[#6e7681]">
              GitPush v1.0 — APK Android murni native (Kotlin + Jetpack Compose, tanpa webview,
              ± 2,9 MB). Ikon GitPush muncul di layar utama seperti aplikasi biasa.
            </p>
            <a
              href="/gitpush.apk"
              download
              className="mt-3 flex min-h-10 w-full items-center justify-center gap-2 rounded-lg bg-[#238636] text-sm font-semibold text-white transition-colors hover:bg-[#2ea043]"
            >
              <Download className="h-4 w-4" aria-hidden />
              Unduh GitPush v1.0 (APK)
            </a>
            <p className="mt-2 text-[10px] leading-relaxed text-[#6e7681]">
              Saat install, izinkan “Install dari sumber tidak dikenal” karena APK ini ditandatangani
              sendiri (bukan dari Play Store).
            </p>
          </section>
        )}

        {/* token info */}
        <section className="rounded-xl border border-[#30363d] bg-[#161b22] p-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <KeyRound className="h-4 w-4 text-[#3fb950]" aria-hidden />
            Token Akses
          </h2>
          <p className="mt-2 font-mono text-xs text-[#8b949e]">
            {demo ? '— mode demo, tanpa token —' : maskToken(token)}
          </p>
          <p className="mt-1.5 text-[11px] leading-relaxed text-[#6e7681]">
            {demo
              ? 'Anda sedang menjelajah dengan data contoh. Upload tidak mengubah GitHub asli.'
              : 'Token disimpan hanya di perangkat ini (localStorage). Jangan bagikan ke siapa pun.'}
          </p>
          <AlertDialog>
            <AlertDialogTrigger
              className="mt-3 flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-[#f8514966] text-sm font-medium text-[#ffa198] transition-colors hover:bg-[#da36331a]"
            >
              <LogOut className="h-4 w-4" aria-hidden />
              {demo ? 'Keluar dari demo' : 'Keluar & hapus token'}
            </AlertDialogTrigger>
            <AlertDialogContent className="border-[#30363d] bg-[#161b22] text-[#e6edf3]">
              <AlertDialogHeader>
                <AlertDialogTitle>Keluar dari GitPush?</AlertDialogTitle>
                <AlertDialogDescription className="text-[#8b949e]">
                  {demo
                    ? 'Anda akan kembali ke halaman masuk.'
                    : 'Token akan dihapus dari perangkat ini. Anda perlu memasukkannya lagi pada saat berikutnya masuk.'}
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel className="border-[#30363d] bg-transparent text-[#e6edf3] hover:bg-[#21262d] hover:text-[#e6edf3]">
                  Batal
                </AlertDialogCancel>
                <AlertDialogAction
                  onClick={signOut}
                  className="bg-[#da3633] text-white hover:bg-[#f85149] focus-visible:ring-[#da3633]"
                >
                  Ya, keluar
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </section>

        {/* riwayat aktivitas */}
        <section className="rounded-xl border border-[#30363d] bg-[#161b22]">
          <div className="flex items-center justify-between border-b border-[#21262d] p-4">
            <h2 className="flex items-center gap-2 text-sm font-semibold">
              <History className="h-4 w-4 text-[#3fb950]" aria-hidden />
              Riwayat Aktivitas
            </h2>
            {history.length > 0 && (
              <button
                onClick={clearHistory}
                aria-label="Hapus riwayat"
                className="flex h-8 w-8 items-center justify-center rounded-md text-[#8b949e] transition-colors hover:bg-[#da363322] hover:text-[#f85149]"
              >
                <Trash2 className="h-4 w-4" aria-hidden />
              </button>
            )}
          </div>
          {history.length === 0 ? (
            <p className="p-4 text-xs text-[#6e7681]">
              Belum ada aktivitas. Upload massal, buat, edit, rename, dan hapus file akan tercatat di
              sini.
            </p>
          ) : (
            <ul className="divide-y divide-[#21262d]">
              {history.map((h, i) => (
                <li key={`${h.sha}-${i}`}>
                  <a
                    href={h.url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex min-h-12 items-center gap-3 px-4 py-3 transition-colors hover:bg-[#1c2128]"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{h.repo}</p>
                      <p className="truncate text-xs text-[#8b949e]">
                        {kindLabel(h)}
                        {' · branch '}
                        <span className="font-mono">{h.branch}</span> · {timeAgoId(h.at)}
                      </p>
                    </div>
                    <span className="shrink-0 rounded-md bg-[#21262d] px-1.5 py-0.5 font-mono text-[10px] text-[#8b949e]">
                      {shortSha(h.sha)}
                    </span>
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* about */}
        <section className="rounded-xl border border-dashed border-[#30363d] p-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-[#8b949e]">
            <Info className="h-4 w-4" aria-hidden />
            Tentang GitPush
          </h2>
          <p className="mt-1.5 text-[11px] leading-relaxed text-[#6e7681]">
            GitPush v1.0 (Native Android) — upload massal 1 commit, buat repository, edit, rename,
            hapus, download file/folder/repository — semuanya langsung dari aplikasi Kotlin asli.
          </p>
        </section>
      </div>
    </main>
  );
}

function MetaRow({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3 text-sm text-[#8b949e]">
      {icon}
      <span className="truncate">{text}</span>
    </div>
  );
}

/** Label baris riwayat sesuai jenis operasi. */
function kindLabel(h: { kind?: string; files: number; path?: string }): string {
  switch (h.kind) {
    case 'create':
      return `Buat file · ${h.path ?? ''}`;
    case 'edit':
      return `Edit file · ${h.path ?? ''}`;
    case 'rename':
      return `Rename file · ${h.path ?? ''}`;
    case 'delete':
      return `Hapus file · ${h.path ?? ''}`;
    default:
      return `Upload massal · ${h.files} file`;
  }
}
