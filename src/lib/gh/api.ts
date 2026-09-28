'use client';

import { GHError } from './errors';
import { demoFetch } from './demo';
import { useAppStore } from './store';
import { sanitizeDirPath, sanitizeFilePath } from './format';
import type {
  GHBranch,
  GHCommit,
  GHContent,
  GHNotification,
  GHReadme,
  GHRepo,
  GHUser,
  UploadFile,
  UploadProgress,
  UploadResult,
} from './types';

// ─── Low-level fetch (proxy or demo) ─────────────────────────────────────────

/**
 * Di dalam APK Android (WebView Capacitor) tidak ada proxy Next.js —
 * panggil api.github.com langsung. GitHub REST API mendukung CORS penuh,
 * sehingga Bearer token aman dikirim dari WebView (origin capacitor://).
 */
const IS_NATIVE =
  typeof window !== 'undefined' &&
  !!(window as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor?.isNativePlatform?.();

/** True saat aplikasi berjalan di dalam APK Android (WebView Capacitor). */
export const isNativeApp = IS_NATIVE;

interface GhInit {
  method?: string;
  body?: unknown;
}

interface GhOpts {
  /** override token (dipakai saat verifikasi token baru sebelum disimpan) */
  token?: string;
}

export async function gh<T = unknown>(path: string, init: GhInit = {}, opts: GhOpts = {}): Promise<T> {
  const { token, demo } = useAppStore.getState();
  const effectiveToken = opts.token ?? token;

  if (demo) return demoFetch<T>(path, init);

  if (!effectiveToken)
    throw new GHError(401, 'Belum masuk. Silakan masukkan token terlebih dahulu.');

  const headers: Record<string, string> = IS_NATIVE
    ? { Authorization: `Bearer ${effectiveToken}`, Accept: 'application/vnd.github+json' }
    : { 'x-gh-token': effectiveToken };
  if (init.body !== undefined) headers['Content-Type'] = 'application/json';

  const baseUrl = IS_NATIVE ? 'https://api.github.com' : '/api/gh';
  let res: Response;
  try {
    res = await fetch(`${baseUrl}/${path}`, {
      method: init.method ?? 'GET',
      headers,
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
      cache: 'no-store',
    });
  } catch {
    throw new GHError(0, 'Koneksi gagal. Periksa jaringan internet Anda.');
  }

  const text = await res.text();
  let data: { message?: string } | null = null;
  try {
    data = text ? (JSON.parse(text) as { message?: string }) : null;
  } catch {
    /* non-JSON */
  }

  if (!res.ok) {
    if (res.status === 401)
      throw new GHError(401, 'Token tidak valid atau kedaluwarsa. Periksa kembali token Anda.');
    if (res.status === 403)
      throw new GHError(403, 'Akses ditolak. Pastikan token memiliki hak akses "repo".');
    if (res.status === 404)
      throw new GHError(404, 'Tidak ditemukan. Repo mungkin kosong atau alamat salah.');
    throw new GHError(res.status, data?.message ?? `Terjadi kesalahan (HTTP ${res.status}).`);
  }

  return data as T;
}

/** Build a path with per-segment encoding. */
export function seg(...parts: Array<string | number>): string {
  return parts.map((p) => encodeURIComponent(String(p))).join('/');
}

/** Encode a repo-internal file path for the contents endpoint. */
function contentPath(path: string): string {
  return path.split('/').map(encodeURIComponent).filter(Boolean).join('/');
}

// ─── High-level API ──────────────────────────────────────────────────────────

export function fetchUser(overrideToken?: string): Promise<GHUser> {
  return gh<GHUser>('user', {}, { token: overrideToken });
}

export function fetchRepos(): Promise<GHRepo[]> {
  return gh<GHRepo[]>('user/repos?per_page=100&sort=pushed');
}

export function fetchRepo(owner: string, repo: string): Promise<GHRepo> {
  return gh<GHRepo>(`repos/${seg(owner, repo)}`);
}

export function fetchContents(owner: string, repo: string, path: string, ref: string): Promise<GHContent[]> {
  return gh<GHContent[]>(
    `repos/${seg(owner, repo)}/contents/${contentPath(path)}?ref=${encodeURIComponent(ref)}`
  );
}

export function fetchBranches(owner: string, repo: string): Promise<GHBranch[]> {
  return gh<GHBranch[]>(`repos/${seg(owner, repo)}/branches?per_page=100`);
}

export function fetchCommits(owner: string, repo: string, perPage = 5): Promise<GHCommit[]> {
  return gh<GHCommit[]>(`repos/${seg(owner, repo)}/commits?per_page=${perPage}`);
}

export function fetchReadme(owner: string, repo: string, ref: string): Promise<GHReadme> {
  return gh<GHReadme>(`repos/${seg(owner, repo)}/readme?ref=${encodeURIComponent(ref)}`);
}

// ─── Notifications ───────────────────────────────────────────────────────────

/** Unread notifications only (source for the badge). */
export function fetchUnreadNotifications(): Promise<GHNotification[]> {
  return gh<GHNotification[]>('notifications?per_page=50');
}

/** Full inbox (unread + read), newest first. */
export function fetchAllNotifications(): Promise<GHNotification[]> {
  return gh<GHNotification[]>('notifications?all=true&per_page=50');
}

export function markThreadRead(id: string): Promise<void> {
  return gh<void>(`notifications/threads/${seg(id)}`, { method: 'PATCH' });
}

export function markAllNotificationsRead(): Promise<void> {
  return gh<void>('notifications', { method: 'PUT' });
}

// ─── Blob helpers ────────────────────────────────────────────────────────────

export interface GHBlob {
  content: string; // base64
  encoding: string;
  size: number;
}

/** Ambil isi file (base64) lewat Git blobs API — bekerja untuk file > 1 MB, berbeda dari contents API. */
export function fetchBlob(owner: string, repo: string, sha: string): Promise<GHBlob> {
  return gh<GHBlob>(`repos/${seg(owner, repo)}/git/blobs/${seg(sha)}`);
}

/** Decode base64 (GitHub readme content) into a UTF-8 string. */
export function decodeB64(b64: string): string {
  const clean = b64.replace(/\s/g, '');
  const bin = atob(clean);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder('utf-8').decode(bytes);
}

/** Encode a UTF-8 string into base64 (chunked agar aman untuk string besar). */
export function encodeB64(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let bin = '';
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    bin += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return btoa(bin);
}

// ─── Single-file operations (create / edit / delete / rename) ──────────────

export interface CommitResult {
  sha: string;
  html_url: string;
}

/**
 * Buat file baru (tanpa sha) atau update file yang sudah ada (dengan sha)
 * dalam satu commit via Contents API.
 */
export async function saveFile(a: {
  owner: string;
  repo: string;
  branch: string;
  path: string;
  message: string;
  contentB64: string;
  sha?: string;
}): Promise<CommitResult> {
  const body: Record<string, unknown> = {
    message: a.message,
    content: a.contentB64,
    branch: a.branch,
  };
  if (a.sha) body.sha = a.sha;
  const res = await gh<{ commit: { sha: string; html_url: string } }>(
    `repos/${seg(a.owner, a.repo)}/contents/${contentPath(a.path)}`,
    { method: 'PUT', body }
  );
  return res.commit;
}

/** Hapus satu file dalam satu commit via Contents API. */
export async function deleteFile(a: {
  owner: string;
  repo: string;
  branch: string;
  path: string;
  sha: string;
  message: string;
}): Promise<CommitResult> {
  const res = await gh<{ commit: { sha: string; html_url: string } }>(
    `repos/${seg(a.owner, a.repo)}/contents/${contentPath(a.path)}`,
    { method: 'DELETE', body: { message: a.message, sha: a.sha, branch: a.branch } }
  );
  return res.commit;
}

/**
 * Rename (dan sekaligus pindahkan antar-folder) sebuah file dalam SATU commit
 * via Git Data API: ref → tree recursive → tree baru (copy blob ke path baru
 * + entry sha null di path lama) → commit → update ref.
 */
export async function renameFile(a: {
  owner: string;
  repo: string;
  branch: string;
  from: string;
  to: string;
  message: string;
}): Promise<CommitResult> {
  const base = `repos/${seg(a.owner, a.repo)}`;

  const ref = await gh<{ object: { sha: string } }>(`${base}/git/ref/heads/${seg(a.branch)}`);
  const headSha = ref.object.sha;
  const headCommit = await gh<{ tree: { sha: string } }>(`${base}/git/commits/${seg(headSha)}`);
  const baseTreeSha = headCommit.tree.sha;

  const tree = await gh<{
    tree: Array<{ path: string; mode: string; type: string; sha: string | null }>;
    truncated?: boolean;
  }>(`${base}/git/trees/${seg(baseTreeSha)}?recursive=1`);

  const fromEntry = tree.tree.find((t) => t.path === a.from && t.type === 'blob');
  if (!fromEntry || !fromEntry.sha)
    throw new GHError(404, `File "${a.from}" tidak ditemukan di branch ${a.branch}.`);
  if (tree.tree.some((t) => t.path === a.to))
    throw new GHError(422, `"${a.to}" sudah ada di repo — pakai nama lain.`);

  const newTree = await gh<{ sha: string }>(`${base}/git/trees`, {
    method: 'POST',
    body: {
      base_tree: baseTreeSha,
      tree: [
        { path: a.to, mode: fromEntry.mode, type: 'blob', sha: fromEntry.sha },
        { path: a.from, mode: fromEntry.mode, type: 'blob', sha: null },
      ],
    },
  });

  const commit = await gh<{ sha: string; html_url: string }>(`${base}/git/commits`, {
    method: 'POST',
    body: { message: a.message, tree: newTree.sha, parents: [headSha] },
  });

  await gh(`${base}/git/refs/heads/${seg(a.branch)}`, {
    method: 'PATCH',
    body: { sha: commit.sha, force: false },
  });

  return commit;
}

// ─── Bulk upload orchestration (all files → ONE commit) ─────────────────────

export async function fileToB64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result ?? '');
      resolve(result.slice(result.indexOf(',') + 1));
    };
    reader.onerror = () => reject(new GHError(0, `Gagal membaca file "${file.name}".`));
    reader.readAsDataURL(file);
  });
}

/**
 * Upload many files to a repo in a single commit using the Git Data API:
 * ref → blobs → tree → commit → update ref. Falls back to creating the
 * initial ref when the branch/repo is empty.
 */
export async function ghBulkUpload(
  target: { owner: string; repo: string; branch: string; path: string; message: string },
  files: UploadFile[],
  onProgress: (p: UploadProgress) => void
): Promise<UploadResult> {
  const base = `repos/${seg(target.owner, target.repo)}`;
  const dir = sanitizeDirPath(target.path);

  // 1) Resolve base commit & tree (empty repo/branch → create initial commit)
  onProgress({ stage: 'ref', done: 0, total: files.length });
  let baseCommitSha: string | null = null;
  let baseTreeSha: string | null = null;
  try {
    const ref = await gh<{ object: { sha: string } }>(`${base}/git/ref/heads/${seg(target.branch)}`);
    baseCommitSha = ref.object.sha;
    const commit = await gh<{ tree: { sha: string } }>(`${base}/git/commits/${seg(baseCommitSha)}`);
    baseTreeSha = commit.tree.sha;
  } catch (e) {
    if (!(e instanceof GHError) || ![404, 409, 422].includes(e.status)) throw e;
  }

  // 2) Create blobs (concurrent workers keep requests small & fast)
  let done = 0;
  const entries: Array<{ path: string; mode: string; type: string; sha: string }> = [];
  const queue = [...files];

  const worker = async (): Promise<void> => {
    for (;;) {
      const file = queue.shift();
      if (!file) return;
      const rel = sanitizeFilePath(file.relPath);
      const full = rel ? (dir ? `${dir}/${rel}` : rel) : '';
      if (!full) throw new GHError(400, `Path tidak valid untuk file "${file.name}".`);
      const blob = await gh<{ sha: string }>(`${base}/git/blobs`, {
        method: 'POST',
        body: { content: file.b64, encoding: 'base64' },
      });
      entries.push({ path: full, mode: '100644', type: 'blob', sha: blob.sha });
      done += 1;
      onProgress({ stage: 'blob', done, total: files.length });
    }
  };

  await Promise.all(Array.from({ length: Math.min(3, files.length) }, worker));

  // 3) Tree with all new files
  onProgress({ stage: 'tree', done: files.length, total: files.length });
  const tree = await gh<{ sha: string }>(`${base}/git/trees`, {
    method: 'POST',
    body: baseTreeSha ? { base_tree: baseTreeSha, tree: entries } : { tree: entries },
  });

  // 4) Commit
  onProgress({ stage: 'commit', done: files.length, total: files.length });
  const commit = await gh<{ sha: string; html_url: string }>(`${base}/git/commits`, {
    method: 'POST',
    body: {
      message: target.message,
      tree: tree.sha,
      parents: baseCommitSha ? [baseCommitSha] : [],
    },
  });

  // 5) Move the branch (or create it for the first commit)
  if (baseCommitSha) {
    await gh(`${base}/git/refs/heads/${seg(target.branch)}`, {
      method: 'PATCH',
      body: { sha: commit.sha, force: false },
    });
  } else {
    await gh(`${base}/git/refs`, {
      method: 'POST',
      body: { ref: `refs/heads/${target.branch}`, sha: commit.sha },
    });
  }

  onProgress({ stage: 'done', done: files.length, total: files.length });
  return { sha: commit.sha, html_url: commit.html_url, files: files.length, branch: target.branch };
}
