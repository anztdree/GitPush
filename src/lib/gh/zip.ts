'use client';

import JSZip from 'jszip';
import { gh, seg, fetchBlob } from './api';
import { GHError } from './errors';

// ─── Git tree helpers ────────────────────────────────────────────────────────

export interface TreeEntry {
  path: string;
  mode: string;
  type: string;
  sha: string;
  size?: number;
}

interface TreeResponse {
  tree: TreeEntry[];
  truncated?: boolean;
}

/** Batas aman unduhan ZIP (memori HP terbatas). */
const MAX_FILES = 400;
const MAX_TOTAL_BYTES = 150 * 1024 * 1024; // 150 MB
const CONCURRENCY = 4;

/** Ambil seluruh entri tree branch (recursive, ala `git ls-tree -r`). */
async function fetchBranchTree(owner: string, repo: string, branch: string): Promise<TreeEntry[]> {
  const base = `repos/${seg(owner, repo)}`;
  const ref = await gh<{ object: { sha: string } }>(`${base}/git/ref/heads/${seg(branch)}`);
  const commit = await gh<{ tree: { sha: string } }>(`${base}/git/commits/${seg(ref.object.sha)}`);
  const res = await gh<TreeResponse>(`${base}/git/trees/${seg(commit.tree.sha)}?recursive=1`);
  if (res.truncated) {
    throw new GHError(400, 'Repo terlalu besar untuk diunduh lewat API (daftar file terpotong).');
  }
  return res.tree;
}

/** Jalankan tugas dengan batas konkurensi. */
async function pool<T>(items: T[], limit: number, fn: (item: T) => Promise<void>): Promise<void> {
  const queue = [...items];
  await Promise.all(
    Array.from({ length: Math.min(limit, queue.length) }, async () => {
      for (;;) {
        const item = queue.shift();
        if (!item) return;
        await fn(item);
      }
    })
  );
}

// ─── Save helpers ────────────────────────────────────────────────────────────

/** Simpan Blob sebagai file — trigger unduhan browser/WebView. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

/** Unduh satu file berdasarkan blob sha → simpan ke perangkat. */
export async function downloadFileBySha(a: {
  owner: string;
  repo: string;
  sha: string;
  name: string;
}): Promise<void> {
  const blob = await fetchBlob(a.owner, a.repo, a.sha);
  const bytes = Uint8Array.from(atob(blob.content.replace(/\s/g, '')), (c) => c.charCodeAt(0));
  saveBlob(new Blob([bytes], { type: 'application/octet-stream' }), a.name);
}

// ─── Folder / repository ZIP download ────────────────────────────────────────

export interface ZipDownloadOptions {
  owner: string;
  repo: string;
  branch: string;
  /** folder tujuan ('' = seluruh repository) */
  dirPath?: string;
  /** nama root folder di dalam zip */
  rootName: string;
  /** nama file .zip hasil unduhan */
  zipName: string;
  onProgress?: (done: number, total: number) => void;
}

/**
 * Unduh folder (atau seluruh repo) sebagai ZIP dalam format ala GitHub:
 * ref → commit → tree recursive → filter blob → fetch blob → JSZip → simpan.
 */
export async function downloadFolderZip(o: ZipDownloadOptions): Promise<{ files: number; bytes: number }> {
  const dir = (o.dirPath ?? '').replace(/^\/+|\/+$/g, '');
  const tree = await fetchBranchTree(o.owner, o.repo, o.branch);
  const files = tree.filter(
    (t) => t.type === 'blob' && !!t.sha && (dir ? t.path.startsWith(`${dir}/`) : true)
  );

  if (files.length === 0) {
    throw new GHError(404, dir ? 'Folder ini kosong — tidak ada yang bisa diunduh.' : 'Repository masih kosong.');
  }
  if (files.length > MAX_FILES) {
    throw new GHError(400, `Terlalu banyak file (${files.length}). Maksimal ${MAX_FILES} file per unduhan ZIP.`);
  }
  const totalBytes = files.reduce((acc, f) => acc + (f.size ?? 0), 0);
  if (totalBytes > MAX_TOTAL_BYTES) {
    throw new GHError(
      400,
      `Total ukuran ${Math.round(totalBytes / 1048576)} MB melebihi batas ${MAX_TOTAL_BYTES / 1048576} MB.`
    );
  }

  const zip = new JSZip();
  let done = 0;
  await pool(files, CONCURRENCY, async (f) => {
    const blob = await fetchBlob(o.owner, o.repo, f.sha);
    const rel = dir ? f.path.slice(dir.length + 1) : f.path;
    zip.file(`${o.rootName}/${rel}`, blob.content.replace(/\s/g, ''), { base64: true });
    done += 1;
    o.onProgress?.(done, files.length);
  });

  const out = await zip.generateAsync({
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  });
  saveBlob(out, o.zipName);
  return { files: files.length, bytes: totalBytes };
}
