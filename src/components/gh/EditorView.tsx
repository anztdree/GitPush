'use client';

import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
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
import { Input } from '@/components/ui/input';
import { useToast } from '@/hooks/use-toast';
import { decodeB64, encodeB64, fetchBlob, saveFile } from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { baseName, formatBytes } from '@/lib/gh/format';
import { useAppStore } from '@/lib/gh/store';
import { ErrorCard, Spinner } from './bits';

const MAX_EDIT_BYTES = 1024 * 1024; // 1 MB — batas nyaman Contents API

/**
 * Overlay editor file: membuat file baru atau mengedit file yang ada,
 * lengkap dengan pesan commit (1 commit per simpan).
 */
export function EditorView() {
  const ed = useAppStore((s) => s.editor)!;
  const closeEditor = useAppStore((s) => s.closeEditor);
  const closeFileView = useAppStore((s) => s.closeFileView);
  const bumpRefresh = useAppStore((s) => s.bumpRefresh);
  const addHistory = useAppStore((s) => s.addHistory);
  const { toast } = useToast();

  const isEdit = ed.mode === 'edit';
  const name = isEdit ? baseName(ed.path) : '';
  const dir = isEdit ? ed.path.split('/').slice(0, -1).join('/') : ed.path;

  const [nameInput, setNameInput] = useState(name);
  const [content, setContent] = useState('');
  const [message, setMessage] = useState(isEdit ? `Update ${name}` : '');
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(isEdit);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const loadContent = useCallback(async () => {
    if (!isEdit) return;
    if (!ed.sha) {
      setError('Sha file tidak diketahui. Buka ulang file dari daftar.');
      return;
    }
    if ((ed.size ?? 0) > MAX_EDIT_BYTES) {
      setError(
        `File ini ${formatBytes(ed.size ?? 0)} — melebihi batas edit 1 MB. Gunakan GitHub PC untuk file sebesar ini.`
      );
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const blob = await fetchBlob(ed.owner, ed.repo, ed.sha);
      setContent(decodeB64(blob.content));
    } catch (e) {
      setError(e instanceof GHError ? e.message : 'Gagal memuat isi file.');
    } finally {
      setLoading(false);
    }
  }, [isEdit, ed.owner, ed.repo, ed.sha, ed.size]);

  useEffect(() => {
    void loadContent();
  }, [loadContent]);

  function requestClose() {
    if (dirty && !busy) setConfirmOpen(true);
    else closeEditor();
  }

  /** Tab menyisipkan 2 spasi (seperti editor kode). */
  function handleTab(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== 'Tab') return;
    e.preventDefault();
    const ta = e.currentTarget;
    const start = ta.selectionStart;
    const end = ta.selectionEnd;
    setContent(content.slice(0, start) + '  ' + content.slice(end));
    setDirty(true);
    requestAnimationFrame(() => {
      ta.selectionStart = ta.selectionEnd = start + 2;
    });
  }

  const finalPath = isEdit
    ? ed.path
    : [...dir.split('/').filter(Boolean), nameInput.trim()].filter(Boolean).join('/');
  const finalName = baseName(finalPath);
  const sizeBytes = new TextEncoder().encode(content).length;
  const tooBig = sizeBytes > MAX_EDIT_BYTES;
  const canCommit =
    !!finalPath && !!finalName && !tooBig && !busy && !loading && !error && (dirty || !isEdit);

  async function commit() {
    if (!finalName) {
      toast({ title: 'Nama file wajib diisi', variant: 'destructive' });
      return;
    }
    setBusy(true);
    try {
      const msg = message.trim() || (isEdit ? `Update ${finalName}` : `Create ${finalName}`);
      const res = await saveFile({
        owner: ed.owner,
        repo: ed.repo,
        branch: ed.branch,
        path: finalPath,
        message: msg,
        contentB64: encodeB64(content),
        sha: isEdit ? ed.sha : undefined,
      });
      addHistory({
        repo: `${ed.owner}/${ed.repo}`,
        branch: ed.branch,
        files: 1,
        sha: res.sha,
        url: res.html_url,
        at: Date.now(),
        kind: isEdit ? 'edit' : 'create',
        path: finalPath,
      });
      toast({
        title: isEdit ? 'Perubahan di-commit' : 'File berhasil dibuat',
        description: `${finalPath} · commit ${res.sha.slice(0, 7)}`,
      });
      bumpRefresh();
      closeEditor();
      if (isEdit) closeFileView();
    } catch (err) {
      toast({
        title: 'Gagal menyimpan file',
        description: err instanceof GHError ? err.message : 'Terjadi kesalahan.',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-label={isEdit ? `Edit file ${name}` : 'Buat file baru'}
      className="fixed inset-0 z-[45] flex flex-col bg-[#0d1117] text-[#e6edf3] animate-in fade-in duration-200"
    >
      {/* Header */}
      <header className="flex h-14 shrink-0 items-center gap-1 border-b border-[#30363d] bg-[#0d1117]/95 px-2 backdrop-blur">
        <button
          onClick={requestClose}
          aria-label="Tutup editor"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[#8b949e] transition-colors hover:bg-[#161b22] hover:text-[#e6edf3]"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden />
        </button>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-sm font-bold">{isEdit ? 'Edit file' : 'File baru'}</h1>
          <p className="truncate text-[11px] text-[#8b949e]">
            {ed.owner}/{ed.repo} · {ed.branch}
          </p>
        </div>
        {dirty && (
          <span className="mr-1 shrink-0 rounded-full bg-[#bb800926] px-2 py-0.5 text-[10px] font-bold text-[#e3b341]">
            belum di-commit
          </span>
        )}
      </header>

      {/* Body */}
      <main className="flex-1 overflow-y-auto overscroll-contain">
        <div className="mx-auto flex h-full w-full max-w-2xl flex-col gap-3 px-4 pb-4 pt-3">
          {error && <ErrorCard message={error} onRetry={() => void loadContent()} />}

          {/* path */}
          {isEdit ? (
            <p className="truncate rounded-lg border border-[#21262d] bg-[#161b22] px-3 py-2.5 font-mono text-xs text-[#8b949e]">
              {ed.path}
            </p>
          ) : (
            <div className="flex items-center gap-1 rounded-lg border border-[#30363d] bg-[#161b22] px-3 transition-colors focus-within:border-[#3fb950]">
              {dir && <span className="shrink-0 font-mono text-xs text-[#6e7681]">{dir}/</span>}
              <input
                value={nameInput}
                onChange={(e) => {
                  setNameInput(e.target.value);
                  setDirty(true);
                }}
                placeholder="nama-file.txt"
                spellCheck={false}
                autoComplete="off"
                aria-label="Nama file baru"
                className="h-11 w-full bg-transparent font-mono text-sm text-[#e6edf3] outline-none placeholder:text-[#484f58]"
              />
            </div>
          )}

          {/* textarea isi file */}
          {loading ? (
            <div className="min-h-[240px] flex-1 space-y-2" aria-hidden>
              {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
                <div
                  key={i}
                  className="h-4 animate-pulse rounded bg-[#21262d]"
                  style={{ width: `${92 - ((i * 11) % 45)}%` }}
                />
              ))}
            </div>
          ) : (
            !error && (
              <textarea
                value={content}
                onChange={(e) => {
                  setContent(e.target.value);
                  setDirty(true);
                }}
                onKeyDown={handleTab}
                placeholder="// Tulis isi file di sini…"
                aria-label="Isi file"
                spellCheck={false}
                className="min-h-[240px] w-full flex-1 resize-none rounded-xl border border-[#30363d] bg-[#161b22] p-3 font-mono text-[12.5px] leading-5 text-[#c9d1d9] outline-none transition-colors focus:border-[#3fb950] placeholder:text-[#484f58]"
              />
            )
          )}

          {/* info */}
          <p className="text-[11px] text-[#6e7681]">
            {formatBytes(sizeBytes)} · {content ? content.split('\n').length : 0} baris
            {tooBig && (
              <span className="text-[#ffa198]"> — melebihi batas edit {formatBytes(MAX_EDIT_BYTES)}</span>
            )}
          </p>

          {/* pesan commit */}
          <Input
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder={
              isEdit ? `Update ${name}` : finalName ? `Create ${finalName}` : 'Pesan commit'
            }
            aria-label="Pesan commit"
            className="border-[#30363d] bg-[#161b22] text-sm text-[#e6edf3] placeholder:text-[#484f58]"
          />
        </div>
      </main>

      {/* Footer */}
      <footer className="shrink-0 border-t border-[#30363d] bg-[#161b22] px-4 pb-[max(0.75rem,env(safe-area-inset-bottom))] pt-3">
        <div className="mx-auto w-full max-w-2xl">
          <button
            onClick={() => void commit()}
            disabled={!canCommit}
            className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#238636] text-sm font-bold text-white shadow-lg shadow-[#23863644] transition-colors hover:bg-[#2ea043] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {busy && <Spinner className="text-white" />}
            {busy ? 'Menyimpan…' : isEdit ? 'Commit perubahan' : 'Buat file'}
          </button>
        </div>
      </footer>

      {/* konfirmasi buang perubahan */}
      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent className="border-[#30363d] bg-[#161b22] text-[#e6edf3]">
          <AlertDialogHeader>
            <AlertDialogTitle>Buang perubahan?</AlertDialogTitle>
            <AlertDialogDescription className="text-[#8b949e]">
              Ada perubahan yang belum di-commit. Jika keluar sekarang, perubahan akan hilang.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="border-[#30363d] bg-transparent text-[#e6edf3] hover:bg-[#21262d] hover:text-[#e6edf3]">
              Lanjut mengedit
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={closeEditor}
              className="bg-[#da3633] text-white hover:bg-[#f85149] focus-visible:ring-[#da3633]"
            >
              Ya, buang
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
