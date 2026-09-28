'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  X,
  UploadCloud,
  FilePlus2,
  FolderPlus,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  ExternalLink,
  GitBranch,
  FolderInput,
  ChevronDown,
  Upload,
} from 'lucide-react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Progress } from '@/components/ui/progress';
import { useToast } from '@/hooks/use-toast';
import { fetchBranches, fetchRepos, ghBulkUpload, fileToB64 } from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { useAppStore } from '@/lib/gh/store';
import { formatBytes, sanitizeDirPath, shortSha } from '@/lib/gh/format';
import type { GHBranch, GHRepo, UploadFile, UploadProgress, UploadResult } from '@/lib/gh/types';
import { EmptyState, ErrorCard, NodeIcon, Spinner } from './bits';

const MAX_FILE_BYTES = 95 * 1024 * 1024; // GitHub blob API hard limit
const WARN_FILE_BYTES = 25 * 1024 * 1024;

interface PickedFile {
  id: string;
  name: string;
  relPath: string;
  size: number;
  file: File;
}

type Phase = 'form' | 'uploading' | 'success' | 'error';

interface ChosenRepo {
  owner: string;
  repo: string;
  defaultBranch: string;
}

export function UploadView() {
  const prefill = useAppStore((s) => s.uploadPrefill);
  const closeUpload = useAppStore((s) => s.closeUpload);
  const addHistory = useAppStore((s) => s.addHistory);
  const bumpRefresh = useAppStore((s) => s.bumpRefresh);
  const { toast } = useToast();

  // ── repo selection ──
  const [manualRepo, setManualRepo] = useState<ChosenRepo | null>(null);
  const [repos, setRepos] = useState<GHRepo[] | null>(null);
  const [reposLoading, setReposLoading] = useState(false);
  const [reposError, setReposError] = useState<string | null>(null);
  const [repoQuery, setRepoQuery] = useState('');

  const chosen: ChosenRepo | null =
    manualRepo ??
    (prefill
      ? {
          owner: prefill.owner,
          repo: prefill.repo,
          defaultBranch: prefill.defaultBranch ?? 'main',
        }
      : null);

  const loadRepos = useCallback(async () => {
    setReposLoading(true);
    setReposError(null);
    try {
      setRepos(await fetchRepos());
    } catch (e) {
      setReposError(e instanceof GHError ? e.message : 'Gagal memuat repositori.');
    } finally {
      setReposLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!chosen) void loadRepos();
  }, [chosen, loadRepos]);

  const filteredRepos = useMemo(() => {
    if (!repos) return [];
    const q = repoQuery.trim().toLowerCase();
    if (!q) return repos;
    return repos.filter(
      (r) => r.full_name.toLowerCase().includes(q) || (r.description ?? '').toLowerCase().includes(q)
    );
  }, [repos, repoQuery]);

  // ── branch ──
  const [branches, setBranches] = useState<GHBranch[] | null>(null);
  const [branch, setBranch] = useState<string>(chosen?.defaultBranch ?? 'main');

  useEffect(() => {
    setBranch(chosen?.defaultBranch ?? 'main');
    if (!chosen) {
      setBranches(null);
      return;
    }
    let alive = true;
    fetchBranches(chosen.owner, chosen.repo)
      .then((b) => {
        if (!alive) return;
        setBranches(b);
        if (b.length > 0 && !b.some((x) => x.name === (chosen?.defaultBranch ?? 'main'))) {
          setBranch(b[0].name);
        }
      })
      .catch(() => alive && setBranches(null));
    return () => {
      alive = false;
    };
  }, [chosen]);

  // ── form fields ──
  const [dirPath, setDirPath] = useState('');
  const [picked, setPicked] = useState<PickedFile[]>([]);
  const [msgTouched, setMsgTouched] = useState(false);
  const [message, setMessage] = useState('');
  const [dragOver, setDragOver] = useState(false);

  // ── upload state ──
  const [phase, setPhase] = useState<Phase>('form');
  const [progress, setProgress] = useState<UploadProgress>({ stage: 'ref', done: 0, total: 0 });
  const [result, setResult] = useState<UploadResult | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const filesInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);

  // set webkitdirectory (non-standard attribute) via DOM
  useEffect(() => {
    const el = folderInputRef.current;
    if (el) {
      el.setAttribute('webkitdirectory', '');
      el.setAttribute('directory', '');
    }
  }, []);

  // reset path when switching repo
  useEffect(() => {
    setDirPath(prefill?.path ?? '');
  }, [prefill]);

  const autoMessage = `Tambah ${picked.length} file via GitPush`;
  const effectiveMessage = msgTouched && message.trim() ? message.trim() : autoMessage;

  const totalBytes = picked.reduce((acc, f) => acc + f.size, 0);
  const dirDisplay = sanitizeDirPath(dirPath);

  function addFiles(list: FileList | File[]) {
    const incoming = Array.from(list);
    const accepted: PickedFile[] = [];
    let skipped = 0;

    for (const file of incoming) {
      if (file.size > MAX_FILE_BYTES) {
        skipped += 1;
        continue;
      }
      const relPath =
        (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
      accepted.push({
        id: `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2, 7)}`,
        name: file.name,
        relPath,
        size: file.size,
        file,
      });
    }

    if (skipped > 0) {
      toast({
        title: 'File terlalu besar',
        description: `${skipped} file dilewati karena melebihi batas 95 MB (batas GitHub).`,
      });
    }

    if (accepted.length === 0) return;

    setPicked((prev) => {
      // merge & dedupe by relPath (newest wins)
      const map = new Map(prev.map((p) => [p.relPath, p]));
      for (const p of accepted) map.set(p.relPath, p);
      return Array.from(map.values());
    });
  }

  function removeFile(id: string) {
    setPicked((prev) => prev.filter((p) => p.id !== id));
  }

  async function startUpload() {
    if (!chosen || picked.length === 0) return;
    setPhase('uploading');
    setErrorMsg(null);
    setResult(null);

    try {
      // 1) prepare base64 locally
      setProgress({ stage: 'prepare', done: 0, total: picked.length });
      const prepared: UploadFile[] = [];
      for (let i = 0; i < picked.length; i++) {
        const p = picked[i];
        const b64 = await fileToB64(p.file);
        prepared.push({ id: p.id, name: p.name, relPath: p.relPath, size: p.size, b64 });
        setProgress({ stage: 'prepare', done: i + 1, total: picked.length });
      }

      // 2) push to GitHub (single commit for ALL files)
      const res = await ghBulkUpload(
        {
          owner: chosen.owner,
          repo: chosen.repo,
          branch,
          path: dirDisplay,
          message: effectiveMessage,
        },
        prepared,
        (p) => setProgress(p)
      );

      setResult(res);
      setPhase('success');
      addHistory({
        repo: `${chosen.owner}/${chosen.repo}`,
        branch,
        files: res.files,
        sha: res.sha,
        url: res.html_url,
        at: Date.now(),
      });
      bumpRefresh();
    } catch (e) {
      setErrorMsg(e instanceof GHError ? e.message : 'Upload gagal. Coba lagi sebentar.');
      setPhase('error');
    }
  }

  function resetForAnotherRun() {
    setPhase('form');
    setPicked([]);
    setProgress({ stage: 'ref', done: 0, total: 0 });
    setResult(null);
    setErrorMsg(null);
    setMsgTouched(false);
    setMessage('');
  }

  function handleClose() {
    if (phase === 'uploading') return; // prevent closing mid-upload
    if (phase === 'success') resetForAnotherRun();
    closeUpload();
  }

  const progressPercent = useMemo(() => {
    const { stage, done, total } = progress;
    if (stage === 'prepare') return total > 0 ? Math.round((done / total) * 8) : 2;
    if (stage === 'ref') return 10;
    if (stage === 'blob') return 15 + Math.round((done / Math.max(total, 1)) * 75);
    if (stage === 'tree') return 93;
    if (stage === 'commit') return 97;
    return 100;
  }, [progress]);

  const stageLabel = useMemo(() => {
    const { stage, done, total } = progress;
    switch (stage) {
      case 'prepare':
        return `Menyiapkan file ${done}/${total}…`;
      case 'ref':
        return 'Mengambil info branch…';
      case 'blob':
        return `Mengunggah ke GitHub ${done}/${total} file…`;
      case 'tree':
        return 'Menyusun struktur file…';
      case 'commit':
        return 'Membuat commit…';
      default:
        return 'Selesai';
    }
  }, [progress]);

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col bg-[#0d1117] text-[#e6edf3] animate-in fade-in slide-in-from-bottom-6 duration-300"
      role="dialog"
      aria-modal="true"
      aria-label="Upload massal"
    >
      {/* header */}
      <header className="flex h-14 shrink-0 items-center gap-3 border-b border-[#30363d] px-4">
        <button
          onClick={handleClose}
          disabled={phase === 'uploading'}
          aria-label="Tutup"
          className="flex h-10 w-10 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3] disabled:opacity-40"
        >
          <X className="h-5 w-5" aria-hidden />
        </button>
        <h1 className="text-base font-bold">Upload Massal</h1>
        <span className="ml-auto rounded-full bg-[#23863633] px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-[#3fb950]">
          1 commit
        </span>
      </header>

      {/* body */}
      <div className="flex-1 overflow-y-auto overscroll-contain">
        <div className="mx-auto w-full max-w-2xl px-4 py-4">
          {/* ── success ── */}
          {phase === 'success' && result && (
            <div className="flex flex-col items-center py-8 text-center">
              <span className="flex h-16 w-16 items-center justify-center rounded-full bg-[#23863633]">
                <CheckCircle2 className="h-9 w-9 text-[#3fb950]" aria-hidden />
              </span>
              <h2 className="mt-4 text-lg font-bold">Upload berhasil!</h2>
              <p className="mt-1 text-sm text-[#8b949e]">
                {result.files} file ter-commit ke branch{' '}
                <span className="font-mono text-[#e6edf3]">{result.branch}</span> dalam satu commit.
              </p>
              <p className="mt-2 rounded-md bg-[#161b22] px-3 py-1.5 font-mono text-xs text-[#8b949e]">
                {shortSha(result.sha)}
              </p>
              <a
                href={result.html_url}
                target="_blank"
                rel="noreferrer"
                className="mt-5 inline-flex min-h-10 items-center gap-1.5 rounded-lg border border-[#30363d] px-4 text-sm font-medium transition-colors hover:bg-[#161b22]"
              >
                Lihat commit di GitHub <ExternalLink className="h-3.5 w-3.5" aria-hidden />
              </a>
              <div className="mt-4 flex w-full max-w-xs flex-col gap-2">
                <button
                  onClick={resetForAnotherRun}
                  className="flex min-h-11 items-center justify-center gap-2 rounded-lg bg-[#238636] text-sm font-semibold transition-colors hover:bg-[#2ea043]"
                >
                  <Upload className="h-4 w-4" aria-hidden /> Upload lagi
                </button>
                <button
                  onClick={handleClose}
                  className="flex min-h-11 items-center justify-center rounded-lg border border-[#30363d] text-sm font-medium transition-colors hover:bg-[#161b22]"
                >
                  Selesai
                </button>
              </div>
            </div>
          )}

          {/* ── uploading ── */}
          {phase === 'uploading' && (
            <div className="flex flex-col items-center justify-center py-16">
              <span className="flex h-16 w-16 items-center justify-center rounded-full bg-[#161b22]">
                <UploadCloud className="h-8 w-8 animate-pulse text-[#3fb950]" aria-hidden />
              </span>
              <p className="mt-5 text-sm font-medium">{stageLabel}</p>
              <p className="mt-1 text-xs text-[#8b949e]">
                Semua file akan masuk ke satu commit — jangan tutup halaman ini.
              </p>
              <div className="mt-6 w-full max-w-sm">
                <Progress value={progressPercent} className="h-2 bg-[#21262d] [&>div]:bg-[#3fb950]" />
              </div>
              <p className="mt-2 font-mono text-xs text-[#6e7681]">{progressPercent}%</p>
            </div>
          )}

          {/* ── error ── */}
          {phase === 'error' && (
            <div className="pt-2">
              <ErrorCard message={errorMsg ?? 'Upload gagal.'} onRetry={() => void startUpload()} />
              <button
                onClick={resetForAnotherRun}
                className="mt-3 min-h-10 w-full rounded-lg border border-[#30363d] text-sm font-medium transition-colors hover:bg-[#161b22]"
              >
                Kembali ke form
              </button>
            </div>
          )}

          {/* ── form ── */}
          {phase === 'form' && (
            <div className="space-y-5">
              {/* Step 1: repo */}
              <section aria-label="Repositori tujuan">
                <SectionTitle step={1} title="Repositori tujuan" />

                {chosen ? (
                  <div className="flex items-center gap-3 rounded-xl border border-[#30363d] bg-[#161b22] p-4">
                    <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-[#23863633]">
                      <FolderInput className="h-5 w-5 text-[#3fb950]" aria-hidden />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold">
                        <span className="text-[#8b949e]">{chosen.owner}/</span>
                        {chosen.repo}
                      </p>
                      <p className="text-xs text-[#8b949e]">Repo terpilih</p>
                    </div>
                    <button
                      onClick={() => setManualRepo(null)}
                      className="ml-auto min-h-9 rounded-lg border border-[#30363d] px-3 text-xs font-medium transition-colors hover:bg-[#21262d]"
                    >
                      Ganti
                    </button>
                  </div>
                ) : (
                  <div className="rounded-xl border border-[#30363d] bg-[#161b22]">
                    <div className="border-b border-[#21262d] p-3">
                      <input
                        type="search"
                        value={repoQuery}
                        onChange={(e) => setRepoQuery(e.target.value)}
                        placeholder="Cari repositori…"
                        aria-label="Cari repositori tujuan"
                        className="w-full rounded-lg border border-[#30363d] bg-[#0d1117] px-3 py-2 text-sm outline-none placeholder:text-[#6e7681] focus:border-[#3fb950]"
                      />
                    </div>
                    {reposLoading && (
                      <div className="flex items-center justify-center gap-2 py-8 text-sm text-[#8b949e]">
                        <Spinner /> Memuat repositori…
                      </div>
                    )}
                    {reposError && (
                      <div className="p-3">
                        <ErrorCard message={reposError} onRetry={() => void loadRepos()} />
                      </div>
                    )}
                    {!reposLoading && !reposError && filteredRepos.length === 0 && (
                      <div className="p-3">
                        <EmptyState icon={FolderInput} title="Tidak ada repositori" desc="Coba kata kunci lain." />
                      </div>
                    )}
                    <ul className="max-h-64 divide-y divide-[#21262d] overflow-y-auto">
                      {filteredRepos.map((r) => (
                        <li key={r.id}>
                          <button
                            onClick={() =>
                              setManualRepo({ owner: r.owner.login, repo: r.name, defaultBranch: r.default_branch })
                            }
                            className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-[#1c2128]"
                          >
                            <NodeIcon isDir={false} name={r.name} />
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-sm font-medium">{r.name}</span>
                              {r.description && (
                                <span className="block truncate text-xs text-[#8b949e]">{r.description}</span>
                              )}
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </section>

              {/* Step 2: settings + files */}
              {chosen && (
                <section aria-label="Pengaturan upload">
                  <SectionTitle step={2} title="Branch, folder & file" />

                  <div className="space-y-3 rounded-xl border border-[#30363d] bg-[#161b22] p-4">
                    {/* branch + folder row */}
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                      {/* branch */}
                      <div>
                        <label htmlFor="ub-branch" className="mb-1.5 block text-xs font-medium text-[#8b949e]">
                          Branch tujuan
                        </label>
                        <DropdownMenu>
                          <DropdownMenuTrigger
                            id="ub-branch"
                            className="flex min-h-11 w-full items-center gap-2 rounded-lg border border-[#30363d] bg-[#0d1117] px-3 text-sm outline-none transition-colors hover:bg-[#1c2128] focus-visible:ring-2 focus-visible:ring-[#3fb950]"
                          >
                            <GitBranch className="h-4 w-4 shrink-0 text-[#8b949e]" aria-hidden />
                            <span className="truncate font-mono text-xs">{branch}</span>
                            <ChevronDown className="ml-auto h-4 w-4 shrink-0 text-[#8b949e]" aria-hidden />
                          </DropdownMenuTrigger>
                          <DropdownMenuContent
                            align="start"
                            className="max-h-72 overflow-y-auto border-[#30363d] bg-[#161b22] text-[#e6edf3]"
                          >
                            {(branches ?? []).map((b) => (
                              <DropdownMenuItem
                                key={b.name}
                                onClick={() => setBranch(b.name)}
                                className="font-mono text-xs data-[highlighted]:bg-[#21262d] focus:bg-[#21262d]"
                              >
                                {b.name}
                                {b.name === branch && <span className="ml-auto text-[10px] text-[#3fb950]">aktif</span>}
                              </DropdownMenuItem>
                            ))}
                            {branches?.length === 0 && (
                              <DropdownMenuItem disabled className="text-xs">
                                Branch akan dibuat otomatis ({branch})
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                        {branches?.length === 0 && (
                          <p className="mt-1 text-[11px] leading-relaxed text-[#d29922]">
                            Repo kosong — branch <span className="font-mono">{branch}</span> dibuat otomatis saat
                            upload pertama.
                          </p>
                        )}
                      </div>

                      {/* folder */}
                      <div>
                        <label htmlFor="ub-path" className="mb-1.5 block text-xs font-medium text-[#8b949e]">
                          Folder tujuan (opsional)
                        </label>
                        <input
                          id="ub-path"
                          type="text"
                          value={dirPath}
                          onChange={(e) => setDirPath(e.target.value)}
                          placeholder="/ (root)"
                          spellCheck={false}
                          autoComplete="off"
                          className="min-h-11 w-full rounded-lg border border-[#30363d] bg-[#0d1117] px-3 font-mono text-xs outline-none transition-colors placeholder:text-[#6e7681] focus:border-[#3fb950] focus:ring-1 focus:ring-[#3fb950]"
                        />
                        <p className="mt-1 text-[11px] text-[#6e7681]">
                          Contoh: <span className="font-mono">docs/gambar</span> — folder dibuat otomatis.
                        </p>
                      </div>
                    </div>

                    {/* drop zone */}
                    <div
                      role="button"
                      tabIndex={0}
                      aria-label="Pilih file untuk diunggah"
                      onClick={() => filesInputRef.current?.click()}
                      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && filesInputRef.current?.click()}
                      onDragOver={(e) => {
                        e.preventDefault();
                        setDragOver(true);
                      }}
                      onDragLeave={() => setDragOver(false)}
                      onDrop={(e) => {
                        e.preventDefault();
                        setDragOver(false);
                        if (e.dataTransfer?.files?.length) addFiles(e.dataTransfer.files);
                      }}
                      className={`flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors ${
                        dragOver
                          ? 'border-[#3fb950] bg-[#23863614]'
                          : 'border-[#30363d] bg-[#0d1117] hover:border-[#8b949e]'
                      }`}
                    >
                      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#161b22]">
                        <UploadCloud className="h-5 w-5 text-[#3fb950]" aria-hidden />
                      </span>
                      <p className="text-sm font-medium">Ketuk untuk memilih file</p>
                      <p className="text-xs text-[#8b949e]">Bisa pilih banyak file sekaligus · atau seret &amp; lepas</p>
                      <input
                        ref={filesInputRef}
                        type="file"
                        multiple
                        className="hidden"
                        aria-hidden
                        onChange={(e) => {
                          if (e.target.files?.length) addFiles(e.target.files);
                          e.target.value = '';
                        }}
                      />
                    </div>

                    {/* folder select (desktop) */}
                    <button
                      onClick={() => folderInputRef.current?.click()}
                      className="flex min-h-10 w-full items-center justify-center gap-2 rounded-lg border border-[#30363d] text-xs font-medium text-[#8b949e] transition-colors hover:bg-[#21262d] hover:text-[#e6edf3]"
                    >
                      <FolderPlus className="h-4 w-4" aria-hidden />
                      Pilih satu folder utuh (dengan struktur sub-folder)
                    </button>
                    <input
                      ref={folderInputRef}
                      type="file"
                      multiple
                      className="hidden"
                      aria-hidden
                      onChange={(e) => {
                        if (e.target.files?.length) addFiles(e.target.files);
                        e.target.value = '';
                      }}
                    />

                    {/* file list */}
                    {picked.length > 0 && (
                      <div>
                        <div className="mb-2 flex items-center justify-between">
                          <p className="text-xs font-medium text-[#8b949e]">
                            {picked.length} file · {formatBytes(totalBytes)}
                          </p>
                          <button
                            onClick={() => setPicked([])}
                            className="inline-flex min-h-8 items-center gap-1 rounded-md px-2 text-xs text-[#f85149] transition-colors hover:bg-[#da363322]"
                          >
                            <Trash2 className="h-3.5 w-3.5" aria-hidden /> Hapus semua
                          </button>
                        </div>
                        <ul className="max-h-64 divide-y divide-[#21262d] overflow-y-auto rounded-lg border border-[#30363d] bg-[#0d1117]">
                          {picked.map((p) => (
                            <li key={p.id} className="flex items-center gap-3 px-3 py-2.5">
                              <NodeIcon isDir={false} name={p.name} />
                              <span className="min-w-0 flex-1">
                                <span className="block truncate font-mono text-xs" title={p.relPath}>
                                  {p.relPath}
                                </span>
                                <span
                                  className={`block text-[10px] ${
                                    p.size > WARN_FILE_BYTES ? 'font-medium text-[#d29922]' : 'text-[#6e7681]'
                                  }`}
                                >
                                  {formatBytes(p.size)}
                                  {p.size > WARN_FILE_BYTES && ' · file besar, mohon tunggu'}
                                </span>
                              </span>
                              <button
                                onClick={() => removeFile(p.id)}
                                aria-label={`Hapus ${p.name} dari daftar`}
                                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-[#8b949e] transition-colors hover:bg-[#da363322] hover:text-[#f85149]"
                              >
                                <X className="h-4 w-4" aria-hidden />
                              </button>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* commit message */}
                    <div>
                      <label htmlFor="ub-msg" className="mb-1.5 block text-xs font-medium text-[#8b949e]">
                        Pesan commit
                      </label>
                      <textarea
                        id="ub-msg"
                        rows={2}
                        value={msgTouched && message.trim() ? message : effectiveMessage}
                        onChange={(e) => {
                          setMsgTouched(true);
                          setMessage(e.target.value);
                        }}
                        className="w-full resize-none rounded-lg border border-[#30363d] bg-[#0d1117] px-3 py-2.5 text-sm outline-none transition-colors focus:border-[#3fb950] focus:ring-1 focus:ring-[#3fb950]"
                      />
                    </div>
                  </div>
                </section>
              )}
            </div>
          )}
        </div>
      </div>

      {/* footer CTA */}
      {phase === 'form' && (
        <footer className="shrink-0 border-t border-[#30363d] bg-[#161b22] p-4 pb-[calc(1rem+env(safe-area-inset-bottom))]">
          <button
            onClick={() => void startUpload()}
            disabled={!chosen || picked.length === 0}
            className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#238636] text-sm font-bold text-white shadow-lg shadow-[#23863644] transition-colors hover:bg-[#2ea043] disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none"
          >
            <FilePlus2 className="h-4 w-4" aria-hidden />
            {picked.length > 0
              ? `Upload ${picked.length} file${dirDisplay ? ` → ${dirDisplay}/` : ''}`
              : 'Pilih file untuk memulai'}
          </button>
        </footer>
      )}
    </div>
  );
}

function SectionTitle({ step, title }: { step: number; title: string }) {
  return (
    <div className="mb-2.5 flex items-center gap-2">
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#238636] text-[10px] font-bold text-white">
        {step}
      </span>
      <h2 className="text-sm font-semibold">{title}</h2>
    </div>
  );
}
