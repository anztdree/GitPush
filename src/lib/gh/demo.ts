'use client';

/**
 * Demo mode — fully client-side mock of the GitHub API subset used by GitPush.
 * Uploads actually mutate the in-memory tree so the whole flow can be tried
 * end-to-end without a real token.
 */

import { GHError } from './errors';
import { formatBytes, isImageFile } from './format';
import type { GHBranch, GHCommit, GHContent, GHNotification, GHRepo } from './types';

// ─── Mock model ──────────────────────────────────────────────────────────────

interface DemoFile {
  type: 'file';
  size: number;
  /** sha stabil selama sesi — dipakai untuk GET git/blobs/{sha} */
  sha: string;
  /** isi file base64 (diisi saat upload / saat pertama kali dibaca) */
  b64?: string;
}
interface DemoDir {
  type: 'dir';
  children: Record<string, DemoNode>;
}
type DemoNode = DemoFile | DemoDir;

interface DemoCommit {
  sha: string;
  message: string;
  date: string;
}

interface DemoNotif {
  id: string;
  unread: boolean;
  reason: string;
  updated_at: string;
  subject: { title: string; type: string };
  repoName: string;
}

interface DemoRepo {
  meta: {
    id: number;
    name: string;
    private: boolean;
    fork: boolean;
    description: string | null;
    language: string | null;
    size: number;
    default_branch: string;
    updated_at: string;
    pushed_at: string;
  };
  branches: string[];
  heads: Record<string, string>;
  tree: DemoDir;
  commits: DemoCommit[];
}

const OWNER = 'octocat-demo';

function sha40(): string {
  let s = '';
  for (let i = 0; i < 40; i++) s += '0123456789abcdef'[Math.floor(Math.random() * 16)];
  return s;
}

const f = (size: number): DemoFile => ({ type: 'file', size, sha: sha40() });
const d = (children: Record<string, DemoNode>): DemoDir => ({ type: 'dir', children });

function daysAgo(n: number): string {
  return new Date(Date.now() - n * 86_400_000).toISOString();
}

function makeRepo(
  id: number,
  name: string,
  description: string | null,
  language: string | null,
  opts: { days: number; commits: string[]; tree: DemoDir; private?: boolean; default_branch?: string; empty?: boolean }
): DemoRepo {
  const defaultBranch = opts.default_branch ?? 'main';
  const head = sha40();
  return {
    meta: {
      id,
      name,
      private: opts.private ?? false,
      fork: false,
      description,
      language,
      size: opts.empty ? 0 : 1024 + Math.floor(Math.random() * 40_000),
      default_branch: defaultBranch,
      updated_at: daysAgo(opts.days),
      pushed_at: daysAgo(opts.days),
    },
    branches: opts.empty ? [] : [defaultBranch, ...(opts.default_branch ? [] : ['dev'])],
    heads: opts.empty ? {} : { [defaultBranch]: head },
    tree: opts.tree,
    commits: (opts.commits ?? []).map((message, i) => ({
      sha: sha40(),
      message,
      date: daysAgo(opts.days + i * 3),
    })),
  };
}

const repos: DemoRepo[] = [
  makeRepo(1, 'belajar-react-native', 'Aplikasi belajar React Native bahasa Indonesia 📱', 'TypeScript', {
    days: 1,
    commits: ['Add modul 5: navigasi', 'Update README', 'Initial commit'],
    tree: d({
      'README.md': f(4200),
      'package.json': f(1200),
      'tsconfig.json': f(660),
      app: d({ 'index.tsx': f(2100), '_layout.tsx': f(980) }),
      components: d({ 'Button.tsx': f(1500), 'Card.tsx': f(2300) }),
      assets: d({ 'logo.png': f(150_000), 'hero.jpg': f(1_200_000) }),
    }),
  }),
  makeRepo(2, 'portofolio-saya', 'Website portofolio pribadi, dibuat dengan HTML + CSS', 'HTML', {
    days: 3,
    commits: ['Tambah proyek terbaru', 'Perbaiki responsive', 'Initial commit'],
    tree: d({
      'index.html': f(8400),
      'style.css': f(5600),
      'script.js': f(1900),
      img: d({ 'profil.jpg': f(340_000), 'proyek-1.png': f(820_000) }),
    }),
  }),
  makeRepo(3, 'catatan-kuliah', 'Catatan & rangkuman materi kuliah semester 1-4', 'Markdown', {
    days: 6,
    commits: ['Rangkum UAS basis data', 'Tambah catatan jarkom', 'Initial commit'],
    tree: d({
      'README.md': f(2100),
      'semester-1': d({ 'matematika-diskrit.md': f(12_000), 'pemrograman-dasar.md': f(9800) }),
      'semester-2': d({ 'basis-data.md': f(15_400), 'jaringan-komputer.md': f(11_200) }),
      'foto-kampus.jpg': f(2_400_000),
    }),
  }),
  makeRepo(4, 'toko-online-flutter', 'Aplikasi toko online sederhana dengan Flutter & Firebase', 'Dart', {
    days: 9,
    commits: ['Fitur keranjang belanja', 'Integrasi Firebase Auth', 'Initial commit'],
    tree: d({
      'pubspec.yaml': f(2400),
      'README.md': f(3300),
      lib: d({ 'main.dart': f(1200), 'screens': d({ 'home.dart': f(6800), 'cart.dart': f(5400) }) }),
      'assets/products.json': f(45_000),
    }),
  }),
  makeRepo(5, 'script-otomasi', 'Kumpulan script Python untuk otomasi harian', 'Python', {
    days: 14,
    commits: ['Tambah script rename-massal.py', 'Initial commit'],
    tree: d({
      'README.md': f(1100),
      'rename-massal.py': f(3200),
      'backup-otomatis.py': f(4100),
      'requirements.txt': f(120),
    }),
  }),
  makeRepo(6, 'wallpaper-apk', 'Aplikasi wallpaper Android (Kotlin) dengan API Unsplash', 'Kotlin', {
    days: 21,
    commits: ['Fix crash di Android 14', 'Initial commit'],
    tree: d({
      'build.gradle': f(1800),
      'README.md': f(2600),
      app: d({ 'src': d({ 'MainActivity.kt': f(5600) }) }),
    }),
  }),
  makeRepo(7, 'dotfiles', 'Konfigurasi terminal & editor saya', 'Shell', {
    days: 30,
    commits: ['Update zshrc', 'Initial commit'],
    tree: d({ '.zshrc': f(3400), '.gitconfig': f(600), 'install.sh': f(2100) }),
  }),
  makeRepo(8, 'dokumen-pribadi', 'Arsip dokumen penting (privat)', 'Markdown', {
    days: 45,
    commits: ['Update CV', 'Initial commit'],
    tree: d({ 'cv.pdf': f(320_000), 'sertifikat/': d({ 'siswa-2024.pdf': f(180_000) }) }),
    private: true,
  }),
  makeRepo(9, 'repo-kosong', 'Repo baru — coba upload file pertama di sini! 🚀', null, {
    days: 0,
    commits: [],
    tree: d({}),
    empty: true,
  }),
];

// upload scratch state
const pendingBlobs = new Map<string, { size: number; b64: string }>(); // sha -> blob
const pendingTrees = new Map<string, { path: string; sha: string | null }[]>(); // treeSha -> entries

// mock inbox (module state agar mutasi "tandai dibaca" persist selama sesi)
const notifications: DemoNotif[] = [
  {
    id: 'n1', unread: true, reason: 'review_requested', updated_at: new Date(Date.now() - 2 * 3600_000).toISOString(),
    subject: { title: 'Tambah validasi form login', type: 'PullRequest' }, repoName: 'belajar-react-native',
  },
  {
    id: 'n2', unread: true, reason: 'mention', updated_at: new Date(Date.now() - 5 * 3600_000).toISOString(),
    subject: { title: 'Perbaiki gambar hero yang rusak', type: 'Issue' }, repoName: 'portofolio-saya',
  },
  {
    id: 'n3', unread: true, reason: 'push', updated_at: new Date(Date.now() - 26 * 3600_000).toISOString(),
    subject: { title: 'Rangkum UAS basis data', type: 'PullRequest' }, repoName: 'catatan-kuliah',
  },
  {
    id: 'n4', unread: false, reason: 'ci_activity', updated_at: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    subject: { title: 'Build #42 gagal di branch dev', type: 'CheckSuite' }, repoName: 'toko-online-flutter',
  },
  {
    id: 'n5', unread: false, reason: 'release', updated_at: new Date(Date.now() - 6 * 86_400_000).toISOString(),
    subject: { title: 'v1.2.0 — dukungan Android 14', type: 'Release' }, repoName: 'wallpaper-apk',
  },
];

function notifToGH(n: DemoNotif): GHNotification {
  return {
    id: n.id,
    unread: n.unread,
    reason: n.reason,
    updated_at: n.updated_at,
    subject: { title: n.subject.title, url: null, type: n.subject.type },
    repository: {
      full_name: `${OWNER}/${n.repoName}`,
      html_url: `https://github.com/${OWNER}/${n.repoName}`,
      owner: { login: OWNER, avatar_url: '' },
    },
  };
}

function b64encode(str: string): string {
  const bytes = new TextEncoder().encode(str);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

function readmeFor(repo: DemoRepo): string {
  return `# ${repo.meta.name}\n\n${repo.meta.description ?? 'Repositori demo GitPush.'}\n\n## Fitur\n\n- Upload massal lewat **GitPush** — banyak file, satu commit\n- Jelajahi file & folder langsung dari HP\n- Notifikasi GitHub di genggaman\n\n## Cara upload massal\n\n1. Buka tab **Unggah**\n2. Pilih repo \`${repo.meta.name}\` dan folder tujuan\n3. Pilih banyak file / folder sekaligus\n4. Tulis pesan commit → **Upload**\n\n\`\`\`bash\ngit clone https://github.com/${OWNER}/${repo.meta.name}.git\ncd ${repo.meta.name}\ngit log --oneline -5\n\`\`\`\n\n> Catatan: repositori ini adalah data demo di dalam GitPush.\n`;
}

function getRepo(name: string): DemoRepo {
  const r = repos.find((x) => x.meta.name === name);
  if (!r) throw new GHError(404, 'Repositori tidak ditemukan.');
  return r;
}

function getNode(root: DemoDir, path: string): DemoNode | null {
  if (!path) return root;
  const segs = path.split('/').filter(Boolean);
  let cur: DemoNode = root;
  for (const s of segs) {
    if (cur.type !== 'dir') return null;
    const next = cur.children[s];
    if (!next) return null;
    cur = next;
  }
  return cur;
}

function putFile(root: DemoDir, path: string, size: number, sha?: string, b64?: string): void {
  const segs = path.split('/').filter(Boolean);
  let cur = root;
  for (let i = 0; i < segs.length - 1; i++) {
    const s = segs[i];
    const next = cur.children[s];
    if (next && next.type === 'dir') {
      cur = next;
    } else {
      const fresh = d({});
      cur.children[s] = fresh;
      cur = fresh;
    }
  }
  cur.children[segs[segs.length - 1]] = sha ? { type: 'file', size, sha, b64 } : f(size);
}

/** Hapus file dari tree; folder induk yang jadi kosong ikut dihapus (seperti git). */
function deleteNode(root: DemoDir, path: string): boolean {
  const segs = path.split('/').filter(Boolean);
  if (segs.length === 0) return false;
  const stack: DemoDir[] = [root];
  let cur: DemoDir = root;
  for (let i = 0; i < segs.length - 1; i++) {
    const next = cur.children[segs[i]];
    if (!next || next.type !== 'dir') return false;
    stack.push(next);
    cur = next;
  }
  const last = segs[segs.length - 1];
  const target = cur.children[last];
  if (!target || target.type !== 'file') return false;
  delete cur.children[last];
  for (let i = stack.length - 1; i >= 1; i--) {
    if (Object.keys(stack[i].children).length === 0) {
      delete stack[i - 1].children[segs[i - 1]];
    }
  }
  return true;
}

/** Cari file berdasarkan blob sha di dalam tree (untuk GET git/blobs). */
function findFileInTree(root: DemoDir, prefix: string, sha: string): { path: string; node: DemoFile } | null {
  for (const [name, n] of Object.entries(root.children)) {
    const p = prefix ? `${prefix}/${name}` : name;
    if (n.type === 'file') {
      if (n.sha === sha) return { path: p, node: n };
    } else {
      const sub = findFileInTree(n, p, sha);
      if (sub) return sub;
    }
  }
  return null;
}

/** Pseudo tree sha per repo (dipakai GET git/commits → tree). */
function treeShaFor(repo: DemoRepo): string {
  return `tree${repo.meta.id}`.padEnd(40, '0');
}

/** Ratakan tree menjadi entri git tree (recursive). */
function flattenTree(root: DemoDir, prefix: string): Array<{ path: string; mode: string; type: string; sha: string }> {
  const out: Array<{ path: string; mode: string; type: string; sha: string }> = [];
  for (const [name, n] of Object.entries(root.children)) {
    const p = prefix ? `${prefix}/${name}` : name;
    if (n.type === 'file') {
      out.push({ path: p, mode: '100644', type: 'blob', sha: n.sha });
    } else {
      out.push({ path: p, mode: '040000', type: 'tree', sha: sha40() });
      out.push(...flattenTree(n, p));
    }
  }
  return out;
}

/** Catat commit demo: buat sha, geser head branch (repo kosong → branch pertama). */
function commitDemo(repo: DemoRepo, branch: string, message: string): string {
  if (!(branch in repo.heads)) {
    if (repo.branches.length > 0) throw new GHError(404, `Branch "${branch}" tidak ditemukan.`);
    repo.branches.push(branch);
  }
  const sha = sha40();
  repo.heads[branch] = sha;
  repo.commits.unshift({ sha, message, date: new Date().toISOString() });
  if (repo.meta.size === 0) repo.meta.size = 1024;
  repo.meta.updated_at = repo.meta.pushed_at = new Date().toISOString();
  return sha;
}

// ─── Konten contoh untuk file demo ──────────────────────────────────────────

function defaultText(path: string, size: number): string {
  const name = path.split('/').pop() ?? path;
  const ext = name.includes('.') ? name.split('.').pop()!.toLowerCase() : '';
  const kb = formatBytes(size || 1024);
  if (ext === 'md')
    return `# ${name}\n\n> File contoh dari mode demo GitPush.\n\nDokumen markdown ini bisa **diedit** langsung dari HP —\ntekan tombol **Edit**, ubah isinya, lalu commit.\n\n## Daftar\n\n- Item pertama\n- Item kedua\n`;
  if (ext === 'json')
    return `{\n  "name": "${name}",\n  "demo": true,\n  "ukuran": "${kb}"\n}\n`;
  if (['js', 'jsx', 'ts', 'tsx', 'mjs'].includes(ext))
    return `// ${path} — file contoh (mode demo)\nexport function halo() {\n  console.log('Halo dari ${name}');\n}\n`;
  if (ext === 'py')
    return `# ${path} — file contoh (mode demo)\ndef halo():\n    print("Halo dari ${name}")\n`;
  if (ext === 'html' || ext === 'htm')
    return `<!doctype html>\n<html>\n  <head><title>${name}</title></head>\n  <body><h1>Halo dari ${name}</h1></body>\n</html>\n`;
  if (ext === 'css' || ext === 'scss')
    return `/* ${path} — file contoh */\nbody {\n  margin: 0;\n}\n`;
  return `${name}\n${'='.repeat(name.length)}\n\nFile contoh mode demo GitPush (${kb}).\nBuka dengan tombol Edit untuk mengubah isinya,\natau gunakan Rename / Hapus di halaman file.\n`;
}

/** Gambar placeholder PNG untuk file gambar demo (canvas, sinkron). */
function demoImageB64(path: string): string {
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 640;
    canvas.height = 360;
    const ctx = canvas.getContext('2d');
    if (!ctx) return b64encode(defaultText(path, 0));
    ctx.fillStyle = '#161b22';
    ctx.fillRect(0, 0, 640, 360);
    let h = 0;
    for (const ch of path) h = (h * 31 + ch.charCodeAt(0)) % 360;
    ctx.fillStyle = `hsl(${h}, 40%, 38%)`;
    ctx.beginPath();
    ctx.arc(320, 140, 66, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#e6edf3';
    ctx.font = 'bold 22px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(path.split('/').pop() ?? path, 320, 252);
    ctx.fillStyle = '#8b949e';
    ctx.font = '14px sans-serif';
    ctx.fillText('Gambar contoh — mode demo GitPush', 320, 282);
    const dataUrl = canvas.toDataURL('image/png');
    return dataUrl.slice(dataUrl.indexOf(',') + 1);
  } catch {
    return b64encode(defaultText(path, 0));
  }
}

function contentsOf(repo: DemoRepo, path: string): GHContent[] {
  const node = getNode(repo.tree, path);
  if (!node) throw new GHError(404, 'This repository is empty or path not found.');
  if (node.type === 'file') {
    return [{ name: path.split('/').pop()!, path, type: 'file', size: node.size, sha: node.sha, html_url: '#', download_url: null }];
  }
  return Object.entries(node.children)
    .map(([name, n]) => ({
      name,
      path: path ? `${path}/${name}` : name,
      type: n.type as 'file' | 'dir',
      size: n.type === 'file' ? n.size : 0,
      sha: n.type === 'file' ? n.sha : sha40(),
      html_url: '#',
      download_url: null,
    }))
    .sort((a, b) => (a.type === b.type ? a.name.localeCompare(b.name) : a.type === 'dir' ? -1 : 1));
}

function toGHRepo(r: DemoRepo): GHRepo {
  return {
    id: r.meta.id,
    name: r.meta.name,
    full_name: `${OWNER}/${r.meta.name}`,
    private: r.meta.private,
    fork: r.meta.fork,
    description: r.meta.description,
    html_url: `https://github.com/${OWNER}/${r.meta.name}`,
    stargazers_count: Math.floor(r.meta.id * 3.7) % 24,
    forks_count: Math.floor(r.meta.id * 1.3) % 7,
    open_issues_count: Math.floor(r.meta.id * 1.7) % 9,
    language: r.meta.language,
    size: r.meta.size,
    default_branch: r.meta.default_branch,
    updated_at: r.meta.updated_at,
    pushed_at: r.meta.pushed_at,
    owner: { login: OWNER, avatar_url: '' },
  };
}

const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));

export async function demoFetch<T = any>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  await sleep(200 + Math.random() * 350);
  const method = (init.method ?? 'GET').toUpperCase();
  const body = (init.body ?? null) as {
    content?: string;
    tree?: { path: string; sha: string | null }[];
    message?: string;
    sha?: string;
    ref?: string;
    branch?: string;
  };
  const [rawPath, qs = ''] = path.split('?');
  const params = new URLSearchParams(qs);
  const segs = rawPath.split('/').filter(Boolean).map(decodeURIComponent);

  // GET user → handled by caller (store) — support anyway
  if (rawPath === 'user') {
    return {
      login: OWNER, name: 'Octo Demo', avatar_url: '', bio: 'Akun demo untuk mencoba GitPush tanpa token.',
      company: null, location: 'Jakarta, Indonesia', public_repos: repos.length, followers: 128, following: 42,
      html_url: 'https://github.com/octocat',
    } as T;
  }

  // ── Notifications ──
  if (rawPath === 'notifications' && method === 'GET') {
    const all = params.get('all') === 'true';
    const list = notifications
      .filter((n) => all || n.unread)
      .sort((a, b) => (a.unread !== b.unread ? (a.unread ? -1 : 1) : a.updated_at < b.updated_at ? 1 : -1))
      .map(notifToGH);
    return list as T;
  }
  if (rawPath === 'notifications' && method === 'PUT') {
    for (const n of notifications) n.unread = false;
    return null as T;
  }
  if (segs[0] === 'notifications' && segs[1] === 'threads' && method === 'PATCH') {
    const n = notifications.find((x) => x.id === segs[2]);
    if (!n) throw new GHError(404, 'Notifikasi tidak ditemukan.');
    n.unread = false;
    return null as T;
  }

  if (rawPath.startsWith('user/repos')) {
    return repos.map(toGHRepo).sort((a, b) => (a.pushed_at < b.pushed_at ? 1 : -1)) as T;
  }

  if (segs[0] === 'repos' && segs[1] === OWNER) {
    const repo = getRepo(segs[2]);
    const rest = segs.slice(3);

    // GET repo meta
    if (rest.length === 0) return toGHRepo(repo) as T;

    // GET readme
    if (rest[0] === 'readme' && method === 'GET') {
      return {
        name: 'README.md',
        path: 'README.md',
        content: b64encode(readmeFor(repo)),
        encoding: 'base64',
        html_url: '#',
      } as T;
    }

    // GET contents
    if (rest[0] === 'contents' && method === 'GET') {
      if (repo.meta.size === 0) throw new GHError(404, 'This repository is empty.');
      const p = rest.slice(1).join('/');
      return contentsOf(repo, p) as T;
    }

    // PUT contents — buat / update satu file (satu commit)
    if (rest[0] === 'contents' && method === 'PUT') {
      const p = rest.slice(1).join('/');
      if (!p) throw new GHError(422, 'Path file tidak valid.');
      const existing = getNode(repo.tree, p);
      if (existing && existing.type === 'dir')
        throw new GHError(422, `"${p}" adalah folder — tidak bisa ditimpa dengan file.`);
      const b64 = String(body?.content ?? '').replace(/\s/g, '');
      const size = Math.floor((b64.length * 3) / 4);
      putFile(repo.tree, p, size, sha40(), b64);
      const branch = String(body?.branch ?? repo.meta.default_branch);
      const message = String(body?.message ?? `Update ${p}`);
      const sha = commitDemo(repo, branch, message);
      return { commit: { sha, html_url: `https://github.com/${OWNER}/${repo.meta.name}/commit/${sha}` } } as T;
    }

    // DELETE contents — hapus satu file (satu commit)
    if (rest[0] === 'contents' && method === 'DELETE') {
      const p = rest.slice(1).join('/');
      const node = getNode(repo.tree, p);
      if (!node) throw new GHError(404, 'File tidak ditemukan.');
      if (node.type === 'dir')
        throw new GHError(422, 'Folder tidak bisa dihapus lewat endpoint ini — hapus filenya satu per satu.');
      if (!deleteNode(repo.tree, p)) throw new GHError(404, 'File tidak ditemukan.');
      const branch = String(body?.branch ?? repo.meta.default_branch);
      const message = String(body?.message ?? `Delete ${p}`);
      const sha = commitDemo(repo, branch, message);
      return { commit: { sha, html_url: `https://github.com/${OWNER}/${repo.meta.name}/commit/${sha}` } } as T;
    }

    // GET branches
    if (rest[0] === 'branches' && method === 'GET') {
      const list: GHBranch[] = repo.branches.map((name) => ({ name, commit: { sha: repo.heads[name] ?? sha40() }, protected: false }));
      return list as T;
    }

    // GET commits
    if (rest[0] === 'commits' && method === 'GET') {
      const per = Number(params.get('per_page') ?? 10);
      const list: GHCommit[] = repo.commits.slice(0, per).map((c) => ({
        sha: c.sha,
        html_url: `https://github.com/${OWNER}/${repo.meta.name}/commit/${c.sha}`,
        commit: { message: c.message, author: { name: 'Octo Demo', date: c.date } },
        author: { login: OWNER, avatar_url: '' },
      }));
      return list as T;
    }

    // POST blobs
    if (rest[0] === 'git' && rest[1] === 'blobs' && method === 'POST') {
      const b64 = String(body?.content ?? '').replace(/\s/g, '');
      const size = Math.floor((b64.length * 3) / 4);
      const sha = sha40();
      pendingBlobs.set(sha, { size, b64 });
      return { sha } as T;
    }

    // GET blobs — isi file (base64)
    if (rest[0] === 'git' && rest[1] === 'blobs' && method === 'GET') {
      const found = findFileInTree(repo.tree, '', rest[2] ?? '');
      if (!found) throw new GHError(404, 'Blob tidak ditemukan (mungkin file belum di-commit).');
      if (!found.node.b64) {
        found.node.b64 = isImageFile(found.path)
          ? demoImageB64(found.path)
          : b64encode(defaultText(found.path, found.node.size));
      }
      return { content: found.node.b64, encoding: 'base64', size: found.node.size } as T;
    }

    // GET git/commits/{sha} — dipakai alur upload & rename (butuh tree sha)
    if (rest[0] === 'git' && rest[1] === 'commits' && method === 'GET') {
      return {
        sha: rest[2] ?? sha40(),
        tree: { sha: treeShaFor(repo) },
        parents: [],
        message: '',
        html_url: `https://github.com/${OWNER}/${repo.meta.name}/commit/${rest[2] ?? ''}`,
      } as T;
    }

    // GET git/trees/{sha}?recursive=1 — daftar seluruh entri tree
    if (rest[0] === 'git' && rest[1] === 'trees' && method === 'GET') {
      return {
        sha: rest[2] ?? treeShaFor(repo),
        tree: flattenTree(repo.tree, ''),
        truncated: false,
      } as T;
    }

    // GET ref / PATCH ref / POST refs
    if (rest[0] === 'git' && (rest[1] === 'ref' || rest[1] === 'refs')) {
      if (rest[1] === 'ref' && method === 'GET') {
        const branch = rest.slice(3).join('/'); // ['heads', ...branchparts]
        if (!(branch in repo.heads)) throw new GHError(404, 'No commit found for the branch (repo mungkin kosong).');
        return { ref: `refs/heads/${branch}`, object: { sha: repo.heads[branch], type: 'commit' } } as T;
      }
      if (rest[1] === 'refs' && method === 'PATCH') {
        const branch = rest.slice(3).join('/');
        if (!(branch in repo.heads)) throw new GHError(422, 'Prop updating is not allowed (branch tidak ada).');
        repo.heads[branch] = body.sha;
        repo.meta.pushed_at = new Date().toISOString();
        return { ok: true } as T;
      }
      if (rest[1] === 'refs' && method === 'POST') {
        const refName = String(body.ref ?? ''); // refs/heads/x
        const branch = refName.replace('refs/heads/', '');
        repo.branches.push(branch);
        repo.heads[branch] = body.sha;
        return { ok: true } as T;
      }
    }

    // POST trees
    if (rest[0] === 'git' && rest[1] === 'trees' && method === 'POST') {
      const sha = sha40();
      pendingTrees.set(sha, body.tree ?? []);
      return { sha } as T;
    }

    // POST commits
    if (rest[0] === 'git' && rest[1] === 'commits' && method === 'POST') {
      const sha = sha40();
      for (const entry of pendingTrees.get(body.tree) ?? []) {
        if (entry.sha === null) {
          deleteNode(repo.tree, entry.path); // rename: hapus path lama
          continue;
        }
        const blob = pendingBlobs.get(entry.sha);
        putFile(repo.tree, entry.path, blob?.size ?? 0, entry.sha, blob?.b64);
      }
      pendingTrees.delete(body.tree);
      repo.commits.unshift({ sha, message: body.message ?? 'Update', date: new Date().toISOString() });
      if (repo.meta.size === 0) repo.meta.size = 1024;
      return { sha, html_url: `https://github.com/${OWNER}/${repo.meta.name}/commit/${sha}` } as T;
    }
  }

  throw new GHError(404, `[demo] Endpoint tidak dikenal: ${method} ${rawPath}`);
}
