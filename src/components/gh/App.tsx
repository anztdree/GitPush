'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { Upload } from 'lucide-react';
import { useAppStore, type InstallPromptEvent } from '@/lib/gh/store';
import { fetchUnreadNotifications, fetchUser } from '@/lib/gh/api';
import { AuthScreen } from './AuthScreen';
import { BottomNav, Header } from './Navigation';
import { HomeView } from './HomeView';
import { NotificationsView } from './NotificationsView';
import { RepoView } from './RepoView';
import { FileView } from './FileView';
import { EditorView } from './EditorView';
import { ProfileView } from './ProfileView';
import { UploadView } from './UploadView';

/** Detect hydration without setState-in-effect (SSR-safe). */
function useMounted(): boolean {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false
  );
}

export default function App() {
  const mounted = useMounted();
  const token = useAppStore((s) => s.token);
  const user = useAppStore((s) => s.user);
  const demo = useAppStore((s) => s.demo);
  const tab = useAppStore((s) => s.tab);
  const location = useAppStore((s) => s.location);
  const fileView = useAppStore((s) => s.fileView);
  const editor = useAppStore((s) => s.editor);
  const uploadOpen = useAppStore((s) => s.uploadOpen);

  // token persisted but user not yet resolved (e.g. interrupted first login)
  useEffect(() => {
    if (!mounted || demo || !token || user) return;
    let alive = true;
    fetchUser()
      .then((u) => {
        if (alive) useAppStore.getState().signIn(token, u);
      })
      .catch(() => {
        if (alive) useAppStore.getState().signOut();
      });
    return () => {
      alive = false;
    };
  }, [mounted, demo, token, user]);

  // badge jumlah notifikasi belum dibaca (saat masuk app & saat pindah ke tab notifikasi)
  useEffect(() => {
    if (!mounted || !user) return;
    let alive = true;
    fetchUnreadNotifications()
      .then((list) => {
        if (alive) useAppStore.getState().setNotifUnread(list.length);
      })
      .catch(() => {
        /* badge best-effort saja */
      });
    return () => {
      alive = false;
    };
  }, [mounted, user, tab === 'notifs']);

  // PWA: daftarkan service worker agar bisa di-install (Add to Home Screen)
  useEffect(() => {
    if (!mounted || !('serviceWorker' in navigator)) return;
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* tidak fatal */
    });
  }, [mounted]);

  // PWA install: tangkap beforeinstallprompt agar tombol "Install" bisa ditampilkan
  useEffect(() => {
    if (!mounted) return;
    const store = useAppStore.getState();
    const standalone =
      window.matchMedia('(display-mode: standalone)').matches ||
      // iOS Safari
      (window.navigator as Navigator & { standalone?: boolean }).standalone === true;
    if (standalone) store.setInstalled();

    const onPrompt = (e: Event) => {
      e.preventDefault();
      useAppStore.getState().setInstallEvent(e as InstallPromptEvent);
    };
    const onInstalled = () => useAppStore.getState().setInstalled();
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, [mounted]);

  // PWA shortcut "Upload massal" (manifest shortcuts → /?action=upload)
  useEffect(() => {
    if (!mounted || !user) return;
    const action = new URLSearchParams(window.location.search).get('action');
    if (action === 'upload') {
      useAppStore.getState().openUpload();
      window.history.replaceState({}, '', window.location.pathname);
    }
  }, [mounted, user]);

  if (!mounted) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center bg-[#0d1117] text-[#e6edf3]">
        <span className="flex h-14 w-14 animate-pulse items-center justify-center rounded-2xl bg-[#238636]">
          <Upload className="h-7 w-7 text-white" aria-hidden />
        </span>
        <p className="mt-4 text-sm font-medium text-[#8b949e]">GitPush</p>
      </div>
    );
  }

  if (!user) {
    return <AuthScreen />;
  }

  return (
    <div className="flex h-dvh flex-col bg-[#0d1117] text-[#e6edf3]">
      <Header />
      {location ? (
        <RepoView key={`${location.owner}/${location.repo}`} />
      ) : tab === 'profile' ? (
        <ProfileView />
      ) : tab === 'notifs' ? (
        <NotificationsView />
      ) : (
        <HomeView />
      )}
      <BottomNav />
      {uploadOpen && <UploadView />}
      {fileView && <FileView key={`${fileView.path}@${fileView.sha}`} />}
      {editor && <EditorView key={`${editor.mode}:${editor.path}`} />}
    </div>
  );
}
