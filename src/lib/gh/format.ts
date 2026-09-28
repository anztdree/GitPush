// ─── Formatting & small helpers ──────────────────────────────────────────────

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

/** Relative time in Indonesian, e.g. "3 jam lalu". */
export function timeAgoId(dateStr: string | number): string {
  const then = typeof dateStr === 'number' ? dateStr : new Date(dateStr).getTime();
  const diff = Date.now() - then;
  const s = Math.floor(diff / 1000);
  if (s < 45) return 'baru saja';
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} menit lalu`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} jam lalu`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d} hari lalu`;
  const mo = Math.floor(d / 30);
  if (mo < 12) return `${mo} bulan lalu`;
  const y = Math.floor(mo / 12);
  return `${y} tahun lalu`;
}

const LANG_COLORS: Record<string, string> = {
  JavaScript: '#f1e05a',
  TypeScript: '#3178c6',
  Python: '#3572A5',
  Java: '#b07219',
  'C#': '#178600',
  PHP: '#4F5D95',
  Ruby: '#701516',
  Go: '#00ADD8',
  Rust: '#dea584',
  Kotlin: '#A97BFF',
  Swift: '#F05138',
  Dart: '#00B4AB',
  HTML: '#e34c26',
  CSS: '#563d7c',
  Shell: '#89e051',
  C: '#555555',
  'C++': '#f34b7d',
  Vue: '#41b883',
  Svelte: '#ff3e00',
};

export function langColor(lang: string | null): string {
  if (!lang) return '#8b949e';
  return LANG_COLORS[lang] ?? '#8b949e';
}

export type FileKind = 'code' | 'image' | 'doc' | 'archive' | 'media' | 'config' | 'file';

export function fileKind(name: string): FileKind {
  const ext = name.split('.').pop()?.toLowerCase() ?? '';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'ico', 'avif'].includes(ext)) return 'image';
  if (['md', 'txt', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'csv', 'rtf'].includes(ext)) return 'doc';
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'apk', 'jar'].includes(ext)) return 'archive';
  if (['mp4', 'mov', 'avi', 'mkv', 'webm', 'mp3', 'wav', 'ogg', 'flac', 'm4a'].includes(ext)) return 'media';
  if (['json', 'yml', 'yaml', 'toml', 'ini', 'env', 'lock', 'xml'].includes(ext) || name.startsWith('.')) return 'config';
  if (['js', 'ts', 'tsx', 'jsx', 'py', 'java', 'kt', 'go', 'rs', 'rb', 'php', 'c', 'cpp', 'h', 'cs', 'swift', 'dart', 'vue', 'svelte', 'html', 'css', 'scss', 'sh', 'sql'].includes(ext)) return 'code';
  return 'file';
}

// ─── File type helpers (viewer & editor) ────────────────────────────────────

const TEXT_EXTS = [
  'js', 'jsx', 'ts', 'tsx', 'mjs', 'cjs', 'json', 'jsonc', 'md', 'mdx', 'txt', 'csv', 'tsv',
  'yml', 'yaml', 'xml', 'html', 'htm', 'css', 'scss', 'sass', 'less', 'py', 'pyw', 'java',
  'kt', 'kts', 'go', 'rs', 'rb', 'php', 'c', 'h', 'cpp', 'hpp', 'cc', 'cs', 'swift', 'dart',
  'sh', 'bash', 'zsh', 'fish', 'bat', 'cmd', 'ps1', 'sql', 'toml', 'ini', 'cfg', 'conf',
  'env', 'log', 'gradle', 'properties', 'lock', 'svg', 'vue', 'svelte', 'astro', 'prisma',
  'graphql', 'gql', 'proto', 'dockerfile', 'mk', 'diff', 'patch', 'gitignore', 'gitattributes',
  'editorconfig', 'npmrc', 'babelrc', 'eslintrc', 'prettierrc', 'http', 'rest', 'vim', 'lua',
  'pl', 'r', 'scala', 'clj', 'ex', 'exs', 'erb', 'hbs', 'twig', 'ejs', 'pug', 'jade', 'tf',
];

const TEXT_BASENAMES = [
  'license', 'licence', 'readme', 'changelog', 'contributing', 'authors', 'contributors',
  'dockerfile', 'makefile', 'procfile', 'rakefile', 'gemfile', 'notice', 'code_of_conduct',
  'security', 'citation', 'dockerignore', 'npmrc', 'nvmrc', 'node-version', 'flake8',
];

/** Kira-kira file teks (bisa dilihat & diedit sebagai string)? */
export function isTextFile(name: string): boolean {
  const base = (name.split('/').pop() ?? name).toLowerCase();
  if (TEXT_BASENAMES.includes(base)) return true;
  const ext = base.includes('.') ? base.split('.').pop()! : '';
  if (!ext) return base.startsWith('.'); // .zshrc, .env, dsb.
  return TEXT_EXTS.includes(ext);
}

const IMAGE_MIMES: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  svg: 'image/svg+xml',
  bmp: 'image/bmp',
  ico: 'image/x-icon',
  avif: 'image/avif',
};

export function isImageFile(name: string): boolean {
  const ext = (name.split('.').pop() ?? '').toLowerCase();
  return ext in IMAGE_MIMES;
}

export function imageMime(name: string): string {
  const ext = (name.split('.').pop() ?? '').toLowerCase();
  return IMAGE_MIMES[ext] ?? 'application/octet-stream';
}

/** Nama file (segmen terakhir) dari sebuah path. */
export function baseName(path: string): string {
  return path.split('/').pop() ?? path;
}

/** Sanitize a user-provided target folder path. Returns '' for root. */
export function sanitizeDirPath(input: string): string {
  return (
    input
      .replace(/\\/g, '/')
      .split('/')
      .map((seg) => seg.trim())
      .filter((seg) => seg.length > 0 && seg !== '.' && seg !== '..')
      .join('/')
  );
}

/** Sanitize a full file path for the git tree entry. */
export function sanitizeFilePath(input: string): string {
  return input
    .replace(/\\/g, '/')
    .split('/')
    .map((seg) => seg.trim())
    .filter((seg) => seg.length > 0 && seg !== '.' && seg !== '..')
    .join('/');
}

export function maskToken(token: string): string {
  if (token.length <= 8) return '••••••••';
  return `${token.slice(0, 4)}••••••••${token.slice(-3)}`;
}

export function shortSha(sha: string): string {
  return sha.slice(0, 7);
}

export function pluralFiles(n: number): string {
  return `${n} file`;
}
