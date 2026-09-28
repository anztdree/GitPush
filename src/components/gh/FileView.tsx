'use client';

import { useCallback, useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import {
  ArrowLeft,
  Copy,
  Download,
  FileQuestion,
  Pencil,
  RotateCcw,
  TextCursorInput,
  Trash2,
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
} from '@/components/ui/alert-dialog';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import { cn } from '@/lib/utils';
import { decodeB64, deleteFile, encodeB64, fetchBlob, renameFile } from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { baseName, formatBytes, imageMime, isImageFile, isTextFile } from '@/lib/gh/format';
import { useAppStore } from '@/lib/gh/store';
import { EmptyState, ErrorCard, NodeIcon, Spinner } from './bits';

const MAX_TEXT_VIEW = 2 * 1024 * 1024; // 2 MB
const MAX_IMAGE_VIEW = 10 * 1024 * 1024; // 10 MB
const MAX_LINES = 3000;

type ViewKind = 'image' | 'text' | 'binary' | 'toolarge';

/**
 * Overlay detail file: pratinjau (gambar / teks / markdown / biner) +
 * aksi Edit, Rename, Unduh, dan Hapus — semuanya satu commit di GitHub.
 */
export function FileView() {
  const fv = useAppStore((s) => s.fileView)!;
  const closeFileView = useAppStore((s) => s.closeFileView);
  const openEditor = useAppStore((s) => s.openEditor);
  const bumpRefresh = useAppStore((s) => s.bumpRefresh);
  const addHistory = useAppStore((s) => s.addHistory);
  const { toast } = useToast();

  const name = baseName(fv.path);
  const dir = fv.path.split('/').slice(0, -1).join('/');
  const isMd = /\.(md|mdx)$/i.test(name);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [kind, setKind] = useState<ViewKind>('binary');
  const [text, setText] = useState('');
  const [b64, setB64] = useState('');
  const [dataUrl, setDataUrl] = useState('');
  const [preview, setPreview] = useState(isMd);

  // rename dialog
  const [renameOpen, setRenameOpen] = useState(false);
  const [newName, setNewName] = useState(name);
  const [renameBusy, setRenameBusy] = useState(false);

  // delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteMsg, setDeleteMsg] = useState(`Hapus ${name}`);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const blob = await fetchBlob(fv.owner, fv.repo, fv.sha);
      const clean = blob.content.replace(/\s/g, '');
      setB64(clean);
      if (isImageFile(name) && fv.size <= MAX_IMAGE_VIEW) {
        setDataUrl(`data:${imageMime(name)};base64,${clean}`);
        setKind('image');
      } else if (isTextFile(name) && fv.size <= MAX_TEXT_VIEW) {
        setText(decodeB64(clean));
        setKind('text');
      } else if (isTextFile(name)) {
        setKind('toolarge');
      } else {
        setKind('binary');
      }
    } catch (e) {
      setError(e instanceof GHError ? e.message : 'Gagal memuat isi file.');
    } finally {
      setLoading(false);
    }
  }, [fv.owner, fv.repo, fv.sha, fv.size, name]);

  useEffect(() => {
    void load();
  }, [load]);

  const lineCount = text ? text.split('\n').length : 0;

  function openEdit() {
    openEditor({
      mode: 'edit',
      owner: fv.owner,
      repo: fv.repo,
      branch: fv.branch,
      path: fv.path,
      sha: fv.sha,
      size: fv.size,
    });
  }

  function download() {
    const raw = kind === 'text' ? encodeB64(text) : b64;
    if (!raw) return;
    const bytes = Uint8Array.from(atob(raw), (c) => c.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
    toast({ title: 'Unduhan dimulai', description: name });
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      toast({ title: 'Isi file disalin', description: `${lineCount} baris tersalin ke clipboard.` });
    } catch {
      toast({ title: 'Gagal menyalin', description: 'Browser menolak akses clipboard.' });
    }
  }

  async function submitRename(e: React.FormEvent) {
    e.preventDefault();
    const target = newName.trim().replace(/\/{2,}/g, '/').replace(/^\/+|\/+$/g, '');
    if (!target) {
      toast({ title: 'Nama tidak boleh kosong', variant: 'destructive' });
      return;
    }
    if (target === fv.path) {
      setRenameOpen(false);
      return;
    }
    if (target.split('/').some((s) => s === '.' || s === '..')) {
      toast({ title: 'Path tidak valid', description: 'Hindari "." dan ".." pada nama.', variant: 'destructive' });
      return;
    }
    setRenameBusy(true);
    try {
      const res = await renameFile({
        owner: fv.owner,
        repo: fv.repo,
        branch: fv.branch,
        from: fv.path,
        to: target,
        message: `Rename ${fv.path} ke ${target}`,
      });
      addHistory({
        repo: `${fv.owner}/${fv.repo}`,
        branch: fv.branch,
        files: 1,
        sha: res.sha,
        url: res.html_url,
        at: Date.now(),
        kind: 'rename',
        path: target,
      });
      toast({ title: 'File berhasil di-rename', description: `${name} → ${baseName(target)} (1 commit)` });
      setRenameOpen(false);
      bumpRefresh();
      closeFileView();
    } catch (err) {
      toast({
        title: 'Rename gagal',
        description: err instanceof GHError ? err.message : 'Terjadi kesalahan.',
        variant: 'destructive',
      });
    } finally {
      setRenameBusy(false);
    }
  }

  async function submitDelete() {
    setDeleteBusy(true);
    try {
      const res = await deleteFile({
        owner: fv.owner,
        repo: fv.repo,
        branch: fv.branch,
        path: fv.path,
        sha: fv.sha,
        message: deleteMsg.trim() || `Hapus ${name}`,
      });
      addHistory({
        repo: `${fv.owner}/${fv.repo}`,
        branch: fv.branch,
        files: 1,
        sha: res.sha,
        url: res.html_url,
        at: Date.now(),
        kind: 'delete',
        path: fv.path,
      });
      toast({ title: 'File dihapus', description: `${fv.path} · commit ${res.sha.slice(0, 7)}` });
      setDeleteOpen(false);
      bumpRefresh();
      closeFileView();
    } catch (err) {
      toast({
        title: 'Gagal menghapus file',
        description: err instanceof GHError ? err.message : 'Terjadi kesalahan.',
        variant: 'destructive',
      });
    } finally {
      setDeleteBusy(false);
    }
  }

  /** Resolve URL relatif di markdown → raw/blob GitHub. */
  function resolveMdUrl(url: string | undefined, target: 'link' | 'img'): string {
    if (!url) return '#';
    if (/^(https?:|data:|mailto:|tel:|#)/i.test(url)) return url;
    const clean = url.replace(/^\.\//, '').split('#')[0];
    const base = `https://${target === 'img' ? 'raw.githubusercontent.com' : 'github.com'}/${fv.owner}/${fv.repo}`;
    return target === 'img' ? `${base}/${fv.branch}/${clean}` : `${base}/blob/${fv.branch}/${clean}`;
  }

  return (
    <div
      role="dialog"
      aria-label={`Detail file ${name}`}
      className="fixed inset-0 z-40 flex flex-col bg-[#0d1117] text-[#e6edf3] animate-in fade-in duration-200"
    >
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center gap-1 border-b border-[#30363d] bg-[#0d1117]/95 px-2 backdrop-blur">
        <button
          onClick={closeFileView}
          aria-label="Kembali ke daftar file"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden />
        </button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-sm font-bold">{name}</h1>
          <p className="truncate text-[11px] text-[#8b949e]">
            {fv.owner}/{fv.repo} · {dir || 'root'} · {fv.branch}
          </p>
        </div>
        <button
          onClick={() => void load()}
          aria-label="Muat ulang isi file"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
        >
          {loading ? <Spinner /> : <RotateCcw className="h-4 w-4" aria-hidden />}
        </button>
      </header>

      {/* Body */}
      <main className="flex-1 overflow-y-auto overscroll-contain">
        <div className="mx-auto w-full max-w-2xl px-4 pb-8 pt-4">
          {/* meta */}
          <div className="flex flex-wrap items-center gap-2 text-xs text-[#8b949e]">
            <NodeIcon isDir={false} name={name} />
            <span className="font-medium text-[#c9d1d9]">{fv.path}</span>
            <span>· {formatBytes(fv.size)}</span>
            {kind === 'text' && <span>· {lineCount.toLocaleString('id-ID')} baris</span>}
          </div>

          {error && (
            <div className="mt-3">
              <ErrorCard message={error} onRetry={() => void load()} />
            </div>
          )}

          {loading && (
            <div className="mt-3 space-y-2" aria-hidden>
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <div
                  key={i}
                  className="h-5 animate-pulse rounded bg-[#21262d]"
                  style={{ width: `${95 - ((i * 13) % 40)}%` }}
                />
              ))}
            </div>
          )}

          {!loading && !error && kind === 'image' && (
            <div className="mt-3 flex items-center justify-center rounded-xl border border-[#30363d] bg-[#161b22] p-3">
              <img
                src={dataUrl}
                alt={`Pratinjau ${name}`}
                className="max-h-[52vh] w-auto max-w-full rounded-lg object-contain"
              />
            </div>
          )}

          {!loading && !error && kind === 'text' && (
            <>
              <div className="mt-3 flex items-center gap-2">
                {isMd && (
                  <div
                    role="tablist"
                    aria-label="Mode tampilan markdown"
                    className="flex overflow-hidden rounded-lg border border-[#30363d]"
                  >
                    <ModeChip active={preview} onClick={() => setPreview(true)}>
                      Tampilan
                    </ModeChip>
                    <ModeChip active={!preview} onClick={() => setPreview(false)}>
                      Mentah
                    </ModeChip>
                  </div>
                )}
                <button
                  onClick={() => void copy()}
                  className="ml-auto inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#30363d] px-3 text-xs font-medium text-[#c9d1d9] transition-colors hover:bg-[#161b22]"
                >
                  <Copy className="h-3.5 w-3.5" aria-hidden /> Salin
                </button>
              </div>

              {isMd && preview ? (
                <section
                  aria-label="Pratinjau markdown"
                  className="markdown-body mt-3 overflow-hidden rounded-xl border border-[#30363d] bg-[#161b22] px-4 py-4"
                >
                  <ReactMarkdown
                    components={{
                      a: ({ href, children }) => (
                        <a href={resolveMdUrl(href, 'link')} target="_blank" rel="noreferrer">
                          {children}
                        </a>
                      ),
                      img: ({ src, alt }) => (
                        <img
                          src={resolveMdUrl(typeof src === 'string' ? src : '', 'img')}
                          alt={alt ?? ''}
                          loading="lazy"
                          onError={(e) => {
                            (e.target as HTMLImageElement).style.display = 'none';
                          }}
                        />
                      ),
                    }}
                  >
                    {text}
                  </ReactMarkdown>
                </section>
              ) : (
                <CodeView text={text} />
              )}
            </>
          )}

          {!loading && !error && kind === 'toolarge' && (
            <div className="mt-3">
              <EmptyState
                icon={FileQuestion}
                title="File terlalu besar untuk pratinjau"
                desc={`Batas pratinjau teks ${formatBytes(MAX_TEXT_VIEW)}. Gunakan tombol Unduh di bawah untuk membuka filenya.`}
              />
            </div>
          )}

          {!loading && !error && kind === 'binary' && (
            <div className="mt-3">
              <EmptyState
                icon={FileQuestion}
                title="Pratinjau tidak tersedia"
                desc="File biner tidak dapat ditampilkan di sini. Unduh untuk membukanya di perangkat Anda."
              />
            </div>
          )}
        </div>
      </main>

      {/* Action bar */}
      <footer className="shrink-0 border-t border-[#30363d] bg-[#161b22] px-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3">
        <div className="mx-auto grid w-full max-w-2xl grid-cols-4 gap-2">
          <ActionButton icon={Pencil} label="Edit" onClick={openEdit} />
          <ActionButton
            icon={TextCursorInput}
            label="Rename"
            onClick={() => {
              setNewName(name);
              setRenameOpen(true);
            }}
          />
          <ActionButton icon={Download} label="Unduh" onClick={download} />
          <ActionButton
            icon={Trash2}
            label="Hapus"
            danger
            onClick={() => {
              setDeleteMsg(`Hapus ${name}`);
              setDeleteOpen(true);
            }}
          />
        </div>
      </footer>

      {/* Rename dialog */}
      <Dialog open={renameOpen} onOpenChange={setRenameOpen}>
        <DialogContent className="border-[#30363d] bg-[#161b22] text-[#e6edf3]">
          <DialogHeader>
            <DialogTitle>Rename file</DialogTitle>
            <DialogDescription className="text-[#8b949e]">
              Boleh sekaligus pindah folder, mis.{' '}
              <span className="font-mono text-xs text-[#c9d1d9]">docs/{name}</span>. Semua dalam satu
              commit.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={submitRename}>
            <label htmlFor="rename-input" className="mb-1.5 block font-mono text-[11px] text-[#6e7681]">
              {dir ? `${dir}/` : 'root/'}
            </label>
            <Input
              id="rename-input"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              autoFocus
              spellCheck={false}
              autoComplete="off"
              aria-label="Nama file baru"
              className="border-[#30363d] bg-[#0d1117] font-mono text-sm text-[#e6edf3] placeholder:text-[#484f58]"
            />
            <div className="mt-4 flex gap-2">
              <button
                type="button"
                onClick={() => setRenameOpen(false)}
                className="min-h-10 flex-1 rounded-lg border border-[#30363d] text-sm font-medium text-[#c9d1d9] transition-colors hover:bg-[#21262d]"
              >
                Batal
              </button>
              <button
                type="submit"
                disabled={renameBusy || !newName.trim()}
                className="flex min-h-10 flex-1 items-center justify-center gap-2 rounded-lg bg-[#238636] text-sm font-semibold text-white transition-colors hover:bg-[#2ea043] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {renameBusy && <Spinner className="text-white" />} Simpan
              </button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent className="border-[#30363d] bg-[#161b22] text-[#e6edf3]">
          <AlertDialogHeader>
            <AlertDialogTitle>Hapus {name}?</AlertDialogTitle>
            <AlertDialogDescription className="text-[#8b949e]">
              File akan dihapus dari branch <span className="font-mono text-[#c9d1d9]">{fv.branch}</span>{' '}
              dalam satu commit. Tindakan ini tidak bisa dibatalkan dari GitPush.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <Input
            value={deleteMsg}
            onChange={(e) => setDeleteMsg(e.target.value)}
            aria-label="Pesan commit"
            placeholder="Pesan commit"
            className="border-[#30363d] bg-[#0d1117] text-sm text-[#e6edf3]"
          />
          <AlertDialogFooter>
            <AlertDialogCancel
              disabled={deleteBusy}
              className="border-[#30363d] bg-transparent text-[#e6edf3] hover:bg-[#21262d] hover:text-[#e6edf3]"
            >
              Batal
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                void submitDelete();
              }}
              disabled={deleteBusy}
              className="bg-[#da3633] text-white hover:bg-[#f85149] focus-visible:ring-[#da3633]"
            >
              {deleteBusy && <Spinner className="mr-2 text-white" />} Hapus file
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

// ─── Sub-komponen ────────────────────────────────────────────────────────────

function ModeChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        'min-h-9 px-3 text-xs font-medium transition-colors',
        active ? 'bg-[#21262d] text-[#e6edf3]' : 'bg-transparent text-[#8b949e] hover:text-[#e6edf3]'
      )}
    >
      {children}
    </button>
  );
}

function ActionButton({
  icon: Icon,
  label,
  onClick,
  danger,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex min-h-12 flex-col items-center justify-center gap-1 rounded-lg border text-[11px] font-medium transition-colors',
        danger
          ? 'border-[#f8514966] text-[#ffa198] hover:bg-[#da36331a]'
          : 'border-[#30363d] text-[#c9d1d9] hover:bg-[#21262d]'
      )}
    >
      <Icon className="h-4 w-4" aria-hidden />
      {label}
    </button>
  );
}

function CodeView({ text }: { text: string }) {
  const lines = text.split('\n');
  const shown = lines.slice(0, MAX_LINES);
  return (
    <div className="mt-3 overflow-hidden rounded-xl border border-[#30363d] bg-[#0d1117]">
      {lines.length > MAX_LINES && (
        <p className="border-b border-[#21262d] bg-[#161b22] px-4 py-2 text-[11px] text-[#8b949e]">
          Menampilkan {MAX_LINES.toLocaleString('id-ID')} dari {lines.length.toLocaleString('id-ID')}{' '}
          baris — buka Edit untuk mengubah bagian awal file.
        </p>
      )}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse font-mono text-[11.5px] leading-5">
          <tbody>
            {shown.map((l, i) => (
              <tr key={i}>
                <td className="w-10 select-none border-r border-[#21262d] px-2 text-right align-top text-[#484f58]">
                  {i + 1}
                </td>
                <td className="whitespace-pre px-3 align-top text-[#c9d1d9]">{l || '\u00A0'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
