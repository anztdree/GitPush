// ─── GitHub API types (subset used by GitPush) ───────────────────────────────

export interface GHUser {
  login: string;
  name: string | null;
  avatar_url: string;
  bio: string | null;
  company: string | null;
  location: string | null;
  public_repos: number;
  followers: number;
  following: number;
  html_url: string;
}

export interface GHRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  fork: boolean;
  description: string | null;
  html_url: string;
  stargazers_count: number;
  forks_count: number;
  open_issues_count: number;
  language: string | null;
  size: number; // KB
  default_branch: string;
  updated_at: string;
  pushed_at: string;
  owner: { login: string; avatar_url: string };
}

export interface GHContent {
  name: string;
  path: string;
  type: 'file' | 'dir';
  size: number;
  sha: string;
  html_url: string | null;
  download_url: string | null;
}

export interface GHBranch {
  name: string;
  commit: { sha: string };
  protected: boolean;
}

export interface GHCommit {
  sha: string;
  html_url: string;
  commit: {
    message: string;
    author: { name: string; date: string } | null;
  };
  author?: { login: string; avatar_url: string } | null;
}

export interface GHNotification {
  id: string;
  unread: boolean;
  reason: string;
  updated_at: string;
  subject: {
    title: string;
    url: string | null;
    type: 'PullRequest' | 'Issue' | 'Release' | 'Discussion' | 'CheckSuite' | 'RepositoryVulnerabilityAlert' | string;
  };
  repository: {
    full_name: string;
    html_url: string;
    owner: { login: string; avatar_url: string };
  };
}

/** README fetched via GET /repos/{owner}/{repo}/readme */
export interface GHReadme {
  name: string;
  path: string;
  content: string; // base64 (encoding: 'base64')
  encoding: string;
  html_url: string | null;
}

// ─── App-level types ─────────────────────────────────────────────────────────

/** A file queued for bulk upload (base64 content prepared client-side). */
export interface UploadFile {
  id: string;
  name: string;
  /** relative path inside the commit (may include webkitRelativePath) */
  relPath: string;
  size: number;
  b64: string;
}

export interface UploadTarget {
  owner: string;
  repo: string;
  branch: string;
  /** destination folder inside the repo ('' = root) */
  path: string;
  message: string;
}

export interface UploadProgress {
  stage: 'prepare' | 'ref' | 'blob' | 'tree' | 'commit' | 'done';
  done: number;
  total: number;
}

export interface UploadResult {
  sha: string;
  html_url: string;
  files: number;
  branch: string;
}

/** Entry in the local history (persisted to localStorage). */
export interface HistoryEntry {
  repo: string;
  branch: string;
  files: number;
  sha: string;
  url: string;
  at: number;
  /** jenis operasi — entri lama tanpa kind dianggap upload */
  kind?: 'upload' | 'create' | 'edit' | 'rename' | 'delete';
  /** path file untuk operasi single-file */
  path?: string;
}

/** File yang sedang dibuka di FileView (overlay detail file). */
export interface FileLocation {
  owner: string;
  repo: string;
  branch: string;
  /** path lengkap file, mis. "src/index.ts" */
  path: string;
  size: number;
  /** git blob sha (dari contents API) — dipakai untuk PUT/DELETE */
  sha: string;
}

/** Prefill untuk EditorView (buat / edit file). */
export interface EditorPrefill {
  mode: 'create' | 'edit';
  owner: string;
  repo: string;
  branch: string;
  /** edit: path lengkap file; create: folder tujuan ('' = root) */
  path: string;
  /** edit: blob sha saat ini */
  sha?: string;
  size?: number;
}
