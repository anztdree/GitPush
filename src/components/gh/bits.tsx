'use client';

import { Folder, FileCode2, FileImage, FileText, FileArchive, Film, Music2, FileCog, File, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fileKind } from '@/lib/gh/format';
import type { FileKind } from '@/lib/gh/format';

// ─── Spinner ─────────────────────────────────────────────────────────────────

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-4 w-4 animate-spin', className)} aria-hidden />;
}

// ─── Avatar with initials fallback ───────────────────────────────────────────

const AVATAR_COLORS = ['#238636', '#9e6a03', '#8957e5', '#bf3989', '#1f6feb', '#da3633'];

export function Avatar({
  src,
  name,
  className,
}: {
  src?: string | null;
  name: string;
  className?: string;
}) {
  const initials = (name || '?').slice(0, 2).toUpperCase();
  const color =
    AVATAR_COLORS[(name || '').length % AVATAR_COLORS.length] ?? AVATAR_COLORS[0];
  return (
    <span
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full font-semibold text-white',
        className
      )}
      style={src ? undefined : { backgroundColor: color }}
      aria-hidden
    >
      {src ? (
        <img src={src} alt={name} className="h-full w-full object-cover" />
      ) : (
        initials
      )}
    </span>
  );
}

// ─── Language dot ────────────────────────────────────────────────────────────

export function LangDot({ color, lang }: { color: string; lang: string | null }) {
  if (!lang) return null;
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-[#8b949e]">
      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: color }} aria-hidden />
      {lang}
    </span>
  );
}

// ─── File / folder icons ─────────────────────────────────────────────────────

const KIND_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  folder: Folder,
  code: FileCode2,
  image: FileImage,
  doc: FileText,
  archive: FileArchive,
  media: Film,
  config: FileCog,
  file: File,
};

const MUSIC_ICON = Music2;

const KIND_COLOR: Record<string, string> = {
  folder: 'text-[#e3b341]',
  code: 'text-[#3fb950]',
  image: 'text-[#d2a8ff]',
  doc: 'text-[#8b949e]',
  archive: 'text-[#f0883e]',
  media: 'text-[#f778ba]',
  config: 'text-[#e3b341]',
  file: 'text-[#8b949e]',
};

export function NodeIcon({ isDir, name, className }: { isDir: boolean; name: string; className?: string }) {
  const kind: FileKind = isDir ? 'folder' : fileKind(name);
  const isAudio = /\.(mp3|wav|ogg|flac|m4a)$/i.test(name);
  const Icon = isAudio ? MUSIC_ICON : KIND_ICON[kind] ?? KIND_ICON.file;
  return <Icon className={cn('h-4 w-4 shrink-0', KIND_COLOR[kind] ?? 'text-[#8b949e]', className)} aria-hidden />;
}

// ─── Empty / error states ────────────────────────────────────────────────────

export function EmptyState({
  icon: Icon,
  title,
  desc,
  action,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  desc?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-[#30363d] px-6 py-10 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#161b22]">
        <Icon className="h-6 w-6 text-[#8b949e]" aria-hidden />
      </span>
      <div>
        <p className="font-medium text-[#e6edf3]">{title}</p>
        {desc && <p className="mt-1 max-w-xs text-sm text-[#8b949e]">{desc}</p>}
      </div>
      {action}
    </div>
  );
}

export function ErrorCard({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-[#f8514940] bg-[#da36331a] p-4 text-sm text-[#ffa198]"
    >
      <p className="font-medium">Terjadi kesalahan</p>
      <p className="mt-1 text-[#e6b3af]">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-3 min-h-9 rounded-lg border border-[#f8514966] px-3 text-sm font-medium text-[#ffa198] transition-colors hover:bg-[#f8514922]"
        >
          Coba lagi
        </button>
      )}
    </div>
  );
}
