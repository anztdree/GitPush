'use client';

import { useCallback, useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import {
  ArrowLeft,
  ChevronRight,
  GitBranch,
  Star,
  GitFork,
  Upload,
  RefreshCw,
  FolderGit2,
  GitCommitHorizontal,
  Database,
  CircleDot,
  FileText,
  FilePlus2,
  Download,
  FolderDown,
  FileDown,
  MoreVertical,
} from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { fetchBranches, fetchCommits, fetchContents, fetchReadme, fetchRepo, decodeB64 } from '@/lib/gh/api';
import { downloadFileBySha, downloadFolderZip } from '@/lib/gh/zip';
import { GHError } from '@/lib/gh/errors';
import { useAppStore } from '@/lib/gh/store';
import { formatBytes, langColor, shortSha, timeAgoId } from '@/lib/gh/format';
import { useToast } from '@/hooks/use-toast';
import type { GHBranch, GHCommit, GHContent, GHRepo } from '@/lib/gh/types';
import { Avatar, EmptyState, ErrorCard, LangDot, NodeIcon, Spinner } from './bits';

export function RepoView() {
  const location = useAppStore((s) => s.location)!;
  const navigatePath = useAppStore((s) => s.navigatePath);
  const closeRepo = useAppStore((s) => s.closeRepo);
  const openUpload = useAppStore((s) => s.openUpload);
  const openFileView = useAppStore((s) => s.openFileView);
  const openEditor = useAppStore((s) => s.openEditor);
  const refreshTick = useAppStore((s) => s.refreshTick);
  const { toast } = useToast();

  const branch = location.branch ?? location.defaultBranch;

  const [meta, setMeta] = useState<GHRepo | null>(null);
  const [contents, setContents] = useState<GHContent[] | null>(null);
  const [branches, setBranches] = useState<GHBranch[] | null>(null);
  const [commits, setCommits] = useState<GHCommit[] | null>(null);
  const [readme, setReadme] = useState<{ title: string; body: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // unduh ZIP (repo / folder) & unduh file dari daftar
  const [zipBusy, setZipBusy] = useState<'repo' | 'folder' | null>(null);
  const [zipProgress, setZipProgress] = useState<{ done: number; total: number } | null>(null);
  const [fileDlBusy, setFileDlBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [m, c] = await Promise.all([
        fetchRepo(location.owner, location.repo),
        fetchContents(location.owner, location.repo, location.path, branch),
      ]);
      setMeta(m);
      setContents(c);
    } catch (e) {
      if (e instanceof GHError && e.status === 404 && !location.path) {
        // repo kosong — bukan error fatal
        setMeta(null);
        setContents([]);
      } else {
        setError(e instanceof GHError ? e.message : 'Gagal memuat repositori.');
      }
    } finally {
      setLoading(false);
    }
  }, [location.owner, location.repo, location.path, branch]);

  useEffect(() => {
    void load();
  }, [load, refreshTick]);

  useEffect(() => {
    let alive = true;
    fetchBranches(location.owner, location.repo)
      .then((b) => alive && setBranches(b))
      .catch(() => alive && setBranches(null));
    fetchCommits(location.owner, location.repo, 5)
      .then((c) => alive && setCommits(c))
      .catch(() => alive && setCommits(null));
    return () => {
      alive = false;
    };
  }, [location.owner, location.repo, refreshTick]);

  // README hanya di root repo (fitur khas web GitHub)
  useEffect(() => {
    if (location.path !== '') {
      setReadme(null);
      return;
    }
    let alive = true;
    fetchReadme(location.owner, location.repo, branch)
      .then((r) => {
        if (alive) setReadme({ title: r.name, body: decodeB64(r.content) });
      })
      .catch(() => {
        if (alive) setReadme(null);
      });
    return () => {
      alive = false;
    };
  }, [location.owner, location.repo, location.path, branch, refreshTick]);

  const crumbs = location.path ? location.path.split('/') : [];
  const displayName = location.repo;

  function selectBranch(name: string) {
    useAppStore.getState().openRepo({ ...location, branch: name });
  }

  /** Unduh seluruh repository (branch aktif) sebagai ZIP. */
  async function handleDownloadRepo() {
    if (zipBusy) return;
    setZipBusy('repo');
    setZipProgress(null);
    try {
      const zipName = `${location.repo}-${branch}.zip`;
      const res = await downloadFolderZip({
        owner: location.owner,
        repo: location.repo,
        branch,
        dirPath: '',
        rootName: `${location.repo}-${branch}`,
        zipName,
        onProgress: (done, total) => setZipProgress({ done, total }),
      });
      toast({ title: 'Repository diunduh', description: `${res.files} file → ${zipName}` });
    } catch (e) {
      toast({
        title: 'Gagal mengunduh repository',
        description: e instanceof GHError ? e.message : 'Terjadi kesalahan tak terduga.',
        variant: 'destructive',
      });
    } finally {
      setZipBusy(null);
      setZipProgress(null);
    }
  }

  /** Unduh satu folder sebagai ZIP. */
  async function handleDownloadFolder(path: string, name: string) {
    if (zipBusy) return;
    setZipBusy('folder');
    setZipProgress(null);
    try {
      const res = await downloadFolderZip({
        owner: location.owner,
        repo: location.repo,
        branch,
        dirPath: path,
        rootName: name,
        zipName: `${name}.zip`,
        onProgress: (done, total) => setZipProgress({ done, total }),
      });
      toast({ title: 'Folder diunduh', description: `${res.files} file → ${name}.zip` });
    } catch (e) {
      toast({
        title: 'Gagal mengunduh folder',
        description: e instanceof GHError ? e.message : 'Terjadi kesalahan tak terduga.',
        variant: 'destructive',
      });
    } finally {
      setZipBusy(null);
      setZipProgress(null);
    }
  }

  /** Unduh satu file langsung dari daftar (tanpa membuka detail). */
  async function handleDownloadFile(item: GHContent) {
    if (fileDlBusy) return;
    setFileDlBusy(item.path);
    try {
      await downloadFileBySha({ owner: location.owner, repo: location.repo, sha: item.sha, name: item.name });
      toast({ title: 'Unduhan dimulai', description: item.name });
    } catch (e) {
      toast({
        title: 'Gagal mengunduh file',
        description: e instanceof GHError ? e.message : 'Terjadi kesalahan tak terduga.',
        variant: 'destructive',
      });
    } finally {
      setFileDlBusy(null);
    }
  }

  /** Resolve URL relatif di README → raw/blob GitHub (path gambar & tautan). */
  function resolveReadmeUrl(url: string | undefined, kind: 'link' | 'img'): string {
    if (!url) return '#';
    if (/^(https?:|data:|mailto:|tel:|#)/i.test(url)) return url;
    const clean = url.replace(/^\.\//, '').split('#')[0];
    const base = `https://${kind === 'img' ? 'raw.githubusercontent.com' : 'github.com'}/${location.owner}/${location.repo}`;
    return kind === 'img' ? `${base}/${branch}/${clean}` : `${base}/blob/${branch}/${clean}`;
  }

  return (
    <main className="flex-1 overflow-y-auto overscroll-contain">
      <div className="mx-auto w-full max-w-2xl px-4 pb-8 pt-3">
        {/* Sub header */}
        <div className="flex items-center gap-2">
          <button
            onClick={closeRepo}
            aria-label="Kembali ke daftar repositori"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden />
          </button>
          <div className="min-w-0">
            <h1 className="truncate text-base font-bold">{displayName}</h1>
            {meta && (
              <p className="truncate text-xs text-[#8b949e]">
                {location.owner}/<span className="font-medium text-[#c9d1d9]">{location.repo}</span>
              </p>
            )}
          </div>
          <button
            onClick={() => void load()}
            aria-label="Muat ulang"
            className="ml-auto flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
          >
            {loading ? <Spinner /> : <RefreshCw className="h-4 w-4" aria-hidden />}
          </button>
        </div>

        {/* Repo info */}
        {meta && (
          <section className="mt-3 rounded-xl border border-[#30363d] bg-[#161b22] p-4">
            {meta.description && (
              <p className="text-sm leading-relaxed text-[#8b949e]">{meta.description}</p>
            )}
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-[#8b949e]">
              <LangDot color={langColor(meta.language)} lang={meta.language} />
              <span className="inline-flex items-center gap-1">
                <Star className="h-3.5 w-3.5" aria-hidden /> {meta.stargazers_count}
              </span>
              <span className="inline-flex items-center gap-1">
                <GitFork className="h-3.5 w-3.5" aria-hidden /> {meta.forks_count}
              </span>
              <span className="inline-flex items-center gap-1">
                <CircleDot className="h-3.5 w-3.5" aria-hidden /> {meta.open_issues_count} issue
              </span>
              <span className="inline-flex items-center gap-1">
                <Database className="h-3.5 w-3.5" aria-hidden /> {formatBytes(meta.size * 1024)}
              </span>
              {meta.private && (
                <span className="rounded-full border border-[#30363d] px-2 py-0.5 text-[10px]">Privat</span>
              )}
            </div>
            <div className="mt-3 flex items-center gap-2 border-t border-[#21262d] pt-3">
              <button
                onClick={() => void handleDownloadRepo()}
                disabled={zipBusy !== null}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#3fb95066] bg-[#2386361a] px-3 text-xs font-semibold text-[#3fb950] transition-colors hover:bg-[#23863633] disabled:opacity-60"
              >
                <Download className="h-3.5 w-3.5" aria-hidden />
                {zipBusy === 'repo' && zipProgress
                  ? `Mengunduh… ${zipProgress.done}/${zipProgress.total}`
                  : 'Download repository'}
              </button>
              <a
                href={`${meta.html_url}/issues`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#30363d] bg-[#0d1117] px-3 text-xs font-medium text-[#c9d1d9] transition-colors hover:bg-[#21262d]"
              >
                <CircleDot className="h-3.5 w-3.5" aria-hidden /> Issues
              </a>
            </div>
          </section>
        )}

        {/* Branch selector */}
        <div className="mt-3">
          <DropdownMenu>
            <DropdownMenuTrigger
              className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#30363d] bg-[#0d1117] px-3 text-xs font-medium text-[#e6edf3] transition-colors hover:bg-[#161b22] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#3fb950]"
              aria-label="Pilih branch"
            >
              <GitBranch className="h-3.5 w-3.5 text-[#8b949e]" aria-hidden />
              {branch}
              <ChevronRight className="h-3 w-3 rotate-90 text-[#8b949e]" aria-hidden />
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="start"
              className="border-[#30363d] bg-[#161b22] text-[#e6edf3] data-[state=open]:animate-none"
            >
              {(branches ?? [{ name: branch, commit: { sha: '' }, protected: false }]).map((b) => (
                <DropdownMenuItem
                  key={b.name}
                  onClick={() => selectBranch(b.name)}
                  className="gap-2 text-xs data-[highlighted]:bg-[#21262d] data-[highlighted]:text-[#e6edf3] focus:bg-[#21262d]"
                >
                  <GitBranch className="h-3.5 w-3.5 text-[#8b949e]" aria-hidden />
                  {b.name}
                  {b.name === branch && <span className="ml-auto text-[10px] text-[#3fb950]">aktif</span>}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {error && (
          <div className="mt-3">
            <ErrorCard message={error} onRetry={() => void load()} />
          </div>
        )}

        {/* Banner progres unduhan ZIP */}
        {!error && zipBusy && (
          <div
            role="status"
            className="mt-3 flex items-center gap-2 rounded-lg border border-[#30363d] bg-[#161b22] px-3 py-2 text-xs text-[#8b949e]"
          >
            <Spinner />
            <span>
              Menyiapkan ZIP {zipBusy === 'repo' ? 'repository' : 'folder'}
              {zipProgress ? `… ${zipProgress.done}/${zipProgress.total} file` : '…'}
            </span>
          </div>
        )}

        {/* Breadcrumbs */}
        {!error && (
          <nav aria-label="Lokasi folder" className="mt-3 flex flex-wrap items-center gap-0.5 font-mono text-xs">
            <button
              onClick={() => navigatePath('')}
              className={
                crumbs.length === 0
                  ? 'font-semibold text-[#3fb950]'
                  : 'text-[#8b949e] transition-colors hover:text-[#3fb950]'
              }
            >
              root
            </button>
            {crumbs.map((c, i) => {
              const isLast = i === crumbs.length - 1;
              const target = crumbs.slice(0, i + 1).join('/');
              return (
                <span key={target} className="flex items-center gap-0.5">
                  <span className="text-[#30363d]">/</span>
                  <button
                    onClick={() => navigatePath(target)}
                    className={
                      isLast
                        ? 'font-semibold text-[#3fb950]'
                        : 'text-[#8b949e] transition-colors hover:text-[#3fb950]'
                    }
                  >
                    {c}
                  </button>
                </span>
              );
            })}
          </nav>
        )}

        {/* Contents */}
        {!error && loading && (
          <div className="mt-3 space-y-2" aria-hidden>
            {[0, 1, 2, 3, 4].map((i) => (
              <div key={i} className="flex animate-pulse items-center gap-3 rounded-lg border border-[#21262d] bg-[#161b22] px-4 py-3">
                <div className="h-4 w-4 rounded bg-[#21262d]" />
                <div className="h-3 flex-1 rounded bg-[#21262d]" />
                <div className="h-3 w-10 rounded bg-[#21262d]" />
              </div>
            ))}
          </div>
        )}

        {!error && !loading && contents && contents.length === 0 && (
          <div className="mt-3">
            <EmptyState
              icon={FolderGit2}
              title={location.path ? 'Folder kosong' : 'Repositori kosong'}
              desc={
                location.path
                  ? 'Belum ada file di folder ini.'
                  : 'Repo ini masih kosong. Upload file pertama sekarang — commit & branch akan dibuat otomatis.'
              }
              action={
                <div className="mt-1 flex w-full max-w-xs flex-col gap-2">
                  <button
                    onClick={() =>
                      openUpload({
                        owner: location.owner,
                        repo: location.repo,
                        branch,
                        defaultBranch: location.defaultBranch,
                        path: location.path,
                      })
                    }
                    className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-[#238636] px-4 text-sm font-semibold text-white transition-colors hover:bg-[#2ea043]"
                  >
                    <Upload className="h-4 w-4" aria-hidden /> Upload File
                  </button>
                  <button
                    onClick={() =>
                      openEditor({
                        mode: 'create',
                        owner: location.owner,
                        repo: location.repo,
                        branch,
                        path: location.path,
                      })
                    }
                    className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-[#30363d] px-4 text-sm font-medium text-[#c9d1d9] transition-colors hover:bg-[#21262d]"
                  >
                    <FilePlus2 className="h-4 w-4" aria-hidden /> Buat file baru
                  </button>
                </div>
              }
            />
          </div>
        )}

        {!error && !loading && contents && contents.length > 0 && (
          <ul className="mt-3 divide-y divide-[#21262d] overflow-hidden rounded-xl border border-[#30363d] bg-[#161b22]">
            {contents.map((item) => (
              <li key={item.path} className="flex items-stretch">
                {item.type === 'dir' ? (
                  <button
                    onClick={() => navigatePath(item.path)}
                    className="flex min-h-12 flex-1 items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[#1c2128]"
                  >
                    <NodeIcon isDir name={item.name} />
                    <span className="truncate text-sm font-medium">{item.name}</span>
                    <ChevronRight className="ml-auto h-4 w-4 shrink-0 text-[#6e7681]" aria-hidden />
                  </button>
                ) : (
                  <button
                    onClick={() =>
                      openFileView({
                        owner: location.owner,
                        repo: location.repo,
                        branch,
                        path: item.path,
                        size: item.size,
                        sha: item.sha,
                      })
                    }
                    className="flex min-h-12 flex-1 items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[#1c2128]"
                  >
                    <NodeIcon isDir={false} name={item.name} />
                    <span className="truncate text-sm font-medium">{item.name}</span>
                    <span className="ml-auto flex shrink-0 items-center gap-2 text-xs text-[#6e7681]">
                      {item.size > 0 && <span>{formatBytes(item.size)}</span>}
                      <ChevronRight className="h-4 w-4" aria-hidden />
                    </span>
                  </button>
                )}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <button
                      aria-label={`Aksi untuk ${item.name}`}
                      className="flex w-11 shrink-0 items-center justify-center border-l border-[#21262d] text-[#8b949e] transition-colors hover:bg-[#1c2128] hover:text-[#e6edf3] focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#3fb950]"
                    >
                      {fileDlBusy === item.path ? <Spinner /> : <MoreVertical className="h-4 w-4" aria-hidden />}
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent
                    align="end"
                    className="border-[#30363d] bg-[#161b22] text-[#e6edf3] data-[state=open]:animate-none"
                  >
                    {item.type === 'dir' ? (
                      <DropdownMenuItem
                        onClick={() => void handleDownloadFolder(item.path, item.name)}
                        disabled={zipBusy !== null}
                        className="gap-2 text-xs data-[highlighted]:bg-[#21262d] data-[highlighted]:text-[#e6edf3] focus:bg-[#21262d]"
                      >
                        <FolderDown className="h-4 w-4 text-[#8b949e]" aria-hidden />
                        Download folder (ZIP)
                      </DropdownMenuItem>
                    ) : (
                      <DropdownMenuItem
                        onClick={() => void handleDownloadFile(item)}
                        disabled={fileDlBusy !== null}
                        className="gap-2 text-xs data-[highlighted]:bg-[#21262d] data-[highlighted]:text-[#e6edf3] focus:bg-[#21262d]"
                      >
                        <FileDown className="h-4 w-4 text-[#8b949e]" aria-hidden />
                        Download file
                      </DropdownMenuItem>
                    )}
                    {item.type === 'file' && (
                      <DropdownMenuItem
                        onClick={() =>
                          openFileView({
                            owner: location.owner,
                            repo: location.repo,
                            branch,
                            path: item.path,
                            size: item.size,
                            sha: item.sha,
                          })
                        }
                        className="gap-2 text-xs data-[highlighted]:bg-[#21262d] data-[highlighted]:text-[#e6edf3] focus:bg-[#21262d]"
                      >
                        <FileText className="h-4 w-4 text-[#8b949e]" aria-hidden />
                        Detail file
                      </DropdownMenuItem>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              </li>
            ))}
          </ul>
        )}

        {/* README (ala tampilan web GitHub, hanya di root) */}
        {!error && !loading && location.path === '' && readme && (
          <section aria-label="README" className="mt-6 overflow-hidden rounded-xl border border-[#30363d] bg-[#161b22]">
            <div className="flex items-center gap-2 border-b border-[#21262d] px-4 py-2.5">
              <FileText className="h-4 w-4 text-[#8b949e]" aria-hidden />
              <h2 className="truncate text-xs font-semibold text-[#c9d1d9]">{readme.title}</h2>
              <span className="ml-auto rounded-full border border-[#30363d] px-2 py-0.5 text-[10px] text-[#8b949e]">
                {branch}
              </span>
            </div>
            <div className="markdown-body px-4 py-4">
              <ReactMarkdown
                components={{
                  a: ({ href, children }) => (
                    <a href={resolveReadmeUrl(href, 'link')} target="_blank" rel="noreferrer">
                      {children}
                    </a>
                  ),
                  img: ({ src, alt }) => (
                    <img
                      src={resolveReadmeUrl(typeof src === 'string' ? src : '', 'img')}
                      alt={alt ?? ''}
                      loading="lazy"
                      onError={(e) => {
                        (e.target as HTMLImageElement).style.display = 'none';
                      }}
                    />
                  ),
                }}
              >
                {readme.body}
              </ReactMarkdown>
            </div>
          </section>
        )}

        {/* Recent commits */}
        {!error && commits && commits.length > 0 && (
          <section aria-label="Commit terbaru" className="mt-6">
            <h2 className="text-sm font-semibold text-[#8b949e]">Commit Terbaru</h2>
            <ul className="mt-2 divide-y divide-[#21262d] overflow-hidden rounded-xl border border-[#30363d] bg-[#161b22]">
              {commits.map((c) => (
                <li key={c.sha}>
                  <a
                    href={c.html_url}
                    target="_blank"
                    rel="noreferrer"
                    className="flex min-h-14 items-center gap-3 px-4 py-3 transition-colors hover:bg-[#1c2128]"
                  >
                    <Avatar
                      src={c.author?.avatar_url || null}
                      name={c.commit.author?.name ?? 'Git'}
                      className="h-7 w-7 text-[10px]"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{c.commit.message.split('\n')[0]}</p>
                      <p className="truncate text-xs text-[#8b949e]">
                        {c.commit.author?.name ?? 'GitHub'} · {timeAgoId(c.commit.author?.date ?? Date.now())}
                      </p>
                    </div>
                    <span className="shrink-0 rounded-md bg-[#21262d] px-1.5 py-0.5 font-mono text-[10px] text-[#8b949e]">
                      {shortSha(c.sha)}
                    </span>
                  </a>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Sticky upload CTA + tombol buat file */}
        {!error && !loading && (
          <div className="sticky bottom-3 mt-6 flex gap-2">
            <button
              onClick={() =>
                openUpload({
                  owner: location.owner,
                  repo: location.repo,
                  branch,
                  defaultBranch: location.defaultBranch,
                  path: location.path,
                })
              }
              className="flex min-h-12 flex-1 items-center justify-center gap-2 rounded-xl bg-[#238636] text-sm font-bold text-white shadow-lg shadow-[#23863644] transition-colors hover:bg-[#2ea043]"
            >
              <Upload className="h-4 w-4" aria-hidden />
              Upload massal ke {location.path || 'repo'}
              <GitCommitHorizontal className="h-4 w-4 opacity-70" aria-hidden />
            </button>
            <button
              onClick={() =>
                openEditor({
                  mode: 'create',
                  owner: location.owner,
                  repo: location.repo,
                  branch,
                  path: location.path,
                })
              }
              aria-label="Buat file baru"
              title="Buat file baru"
              className="flex min-h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-[#30363d] bg-[#161b22] text-[#c9d1d9] shadow-lg transition-colors hover:bg-[#21262d]"
            >
              <FilePlus2 className="h-5 w-5" aria-hidden />
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
