'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { EditorPrefill, FileLocation, GHUser, HistoryEntry } from './types';

export type Tab = 'home' | 'notifs' | 'upload' | 'profile';

/** Event `beforeinstallprompt` (Chrome/Edge — install PWA seperti APK). */
export interface InstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export interface RepoLocation {
  owner: string;
  repo: string;
  /** folder path currently browsed ('' = root) */
  path: string;
  /** branch override (defaults to repo default branch when undefined) */
  branch?: string;
  defaultBranch: string;
}

export interface UploadPrefill {
  owner: string;
  repo: string;
  branch?: string;
  defaultBranch?: string;
  path?: string;
}

interface AppState {
  /** Personal Access Token (empty when in demo mode or signed out) */
  token: string;
  user: GHUser | null;
  demo: boolean;
  tab: Tab;
  /** currently browsed repository (repo view open when non-null) */
  location: RepoLocation | null;
  /** file detail overlay (FileView) */
  fileView: FileLocation | null;
  /** file editor overlay (EditorView — create / edit) */
  editor: EditorPrefill | null;
  /** bulk upload overlay open? */
  uploadOpen: boolean;
  uploadPrefill: UploadPrefill | null;
  history: HistoryEntry[];
  /** bumped after a successful upload so views can refetch */
  refreshTick: number;
  /** jumlah notifikasi belum dibaca (badge, max 50) */
  notifUnread: number;
  /** event install PWA (jika browser menawarkan prompt install) */
  installEvent: InstallPromptEvent | null;
  /** true jika app berjalan sebagai aplikasi terpasang (standalone) */
  installed: boolean;

  signIn: (token: string, user: GHUser) => void;
  signInDemo: () => void;
  signOut: () => void;
  setTab: (tab: Tab) => void;
  openRepo: (loc: RepoLocation) => void;
  navigatePath: (path: string) => void;
  closeRepo: () => void;
  openFileView: (f: FileLocation) => void;
  closeFileView: () => void;
  openEditor: (e: EditorPrefill) => void;
  closeEditor: () => void;
  /** tutup semua overlay jelajah (repo + file + editor) — dipakai saat pindah tab */
  closeOverlays: () => void;
  openUpload: (prefill?: UploadPrefill) => void;
  closeUpload: () => void;
  addHistory: (entry: HistoryEntry) => void;
  clearHistory: () => void;
  bumpRefresh: () => void;
  setNotifUnread: (n: number) => void;
  setInstallEvent: (e: InstallPromptEvent | null) => void;
  setInstalled: () => void;
}

/** Pindahkan data persist lama 'gitbulk-store' → 'gitpush-store' (sekali, lalu hapus). */
function migrateLegacyStorage(): void {
  if (typeof window === 'undefined') return;
  try {
    if (localStorage.getItem('gitpush-store')) return;
    const legacy = localStorage.getItem('gitbulk-store');
    if (legacy) {
      localStorage.setItem('gitpush-store', legacy);
      localStorage.removeItem('gitbulk-store');
    }
  } catch {
    /* localStorage tidak tersedia — abaikan */
  }
}

migrateLegacyStorage();

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      token: '',
      user: null,
      demo: false,
      tab: 'home',
      location: null,
      fileView: null,
      editor: null,
      uploadOpen: false,
      uploadPrefill: null,
      history: [],
      refreshTick: 0,
      notifUnread: 0,
      installEvent: null,
      installed: false,

      signIn: (token, user) =>
        set({ token, user, demo: false, tab: 'home', location: null, fileView: null, editor: null }),
      signInDemo: () =>
        set({
          demo: true,
          token: '',
          user: DEMO_USER,
          tab: 'home',
          location: null,
          fileView: null,
          editor: null,
        }),
      signOut: () =>
        set({
          token: '',
          user: null,
          demo: false,
          tab: 'home',
          location: null,
          fileView: null,
          editor: null,
          uploadOpen: false,
          uploadPrefill: null,
        }),
      setTab: (tab) => set({ tab }),
      openRepo: (loc) => set({ location: loc }),
      navigatePath: (path) => {
        const cur = get().location;
        if (cur) set({ location: { ...cur, path } });
      },
      closeRepo: () => set({ location: null }),
      openFileView: (f) => set({ fileView: f }),
      closeFileView: () => set({ fileView: null }),
      openEditor: (e) => set({ editor: e }),
      closeEditor: () => set({ editor: null }),
      closeOverlays: () => set({ location: null, fileView: null, editor: null }),
      openUpload: (prefill) =>
        set({ uploadOpen: true, uploadPrefill: prefill ?? null }),
      closeUpload: () => set({ uploadOpen: false, uploadPrefill: null }),
      addHistory: (entry) => {
        const next = [entry, ...get().history].slice(0, 15);
        set({ history: next });
      },
      clearHistory: () => set({ history: [] }),
      bumpRefresh: () => set((s) => ({ refreshTick: s.refreshTick + 1 })),
      setNotifUnread: (n) => set({ notifUnread: n }),
      setInstallEvent: (e) => set({ installEvent: e }),
      setInstalled: () => set({ installed: true, installEvent: null }),
    }),
    {
      // nama store baru (rebranding GitBulk → GitPush); data lama dimigrasikan di atas
      name: 'gitpush-store',
      storage: createJSONStorage(() => localStorage),
      partialize: (s) => ({
        token: s.token,
        user: s.user,
        demo: s.demo,
        history: s.history,
      }),
    }
  )
);

export const DEMO_USER: GHUser = {
  login: 'octocat-demo',
  name: 'Octo Demo',
  avatar_url: '',
  bio: 'Akun demo untuk mencoba GitPush tanpa token.',
  company: null,
  location: 'Jakarta, Indonesia',
  public_repos: 8,
  followers: 128,
  following: 42,
  html_url: 'https://github.com/octocat',
};
