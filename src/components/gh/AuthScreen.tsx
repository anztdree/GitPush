'use client';

import { useState } from 'react';
import { Upload, Eye, EyeOff, ExternalLink, ArrowRight, ShieldCheck, Package } from 'lucide-react';
import { fetchUser } from '@/lib/gh/api';
import { GHError } from '@/lib/gh/errors';
import { useAppStore } from '@/lib/gh/store';
import { Spinner } from './bits';

const TOKEN_URL = 'https://github.com/settings/tokens/new?scopes=repo&description=GitPush';

export function AuthScreen() {
  const [token, setToken] = useState('');
  const [show, setShow] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const signIn = useAppStore((s) => s.signIn);
  const signInDemo = useAppStore((s) => s.signInDemo);

  async function handleSignIn() {
    const t = token.trim();
    if (!t) {
      setError('Token masih kosong. Tempel Personal Access Token Anda di sini.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const user = await fetchUser(t);
      signIn(t, user);
    } catch (e) {
      setError(
        e instanceof GHError
          ? e.message
          : 'Gagal memverifikasi token. Coba lagi sebentar.'
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center bg-[#0d1117] px-4 py-10 text-[#e6edf3]">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="mb-8 flex flex-col items-center text-center">
          <span className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-[#238636] shadow-lg shadow-[#23863633]">
            <Upload className="h-8 w-8 text-white" aria-hidden />
          </span>
          <h1 className="text-2xl font-bold tracking-tight">GitPush</h1>
          <p className="mt-2 text-sm leading-relaxed text-[#8b949e]">
            Upload massal, edit, rename & hapus file GitHub langsung dari HP — tampilan ala
            aplikasi GitHub.
          </p>
        </div>

        {/* Login card */}
        <section
          aria-label="Masuk dengan token"
          className="rounded-xl border border-[#30363d] bg-[#161b22] p-5"
        >
          <label htmlFor="token" className="text-sm font-medium">
            Personal Access Token
          </label>
          <div className="relative mt-2">
            <input
              id="token"
              type={show ? 'text' : 'password'}
              value={token}
              onChange={(e) => setToken(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && !loading && handleSignIn()}
              placeholder="ghp_••••••••••••••••"
              autoComplete="off"
              spellCheck={false}
              className="w-full rounded-lg border border-[#30363d] bg-[#0d1117] px-3 py-2.5 pr-11 font-mono text-sm outline-none transition-colors placeholder:text-[#6e7681] focus:border-[#3fb950] focus:ring-1 focus:ring-[#3fb950]"
            />
            <button
              type="button"
              onClick={() => setShow((v) => !v)}
              aria-label={show ? 'Sembunyikan token' : 'Tampilkan token'}
              className="absolute right-1 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-[#8b949e] transition-colors hover:text-[#e6edf3]"
            >
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>

          <a
            href={TOKEN_URL}
            target="_blank"
            rel="noreferrer"
            className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-[#3fb950] hover:underline"
          >
            Buat token di GitHub (scope <code className="font-mono">repo</code> otomatis tercentang)
            <ExternalLink className="h-3 w-3" aria-hidden />
          </a>

          {error && (
            <p
              role="alert"
              className="mt-3 rounded-lg border border-[#f8514940] bg-[#da36331a] px-3 py-2 text-xs leading-relaxed text-[#ffa198]"
            >
              {error}
            </p>
          )}

          <button
            onClick={handleSignIn}
            disabled={loading}
            className="mt-4 flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-[#238636] text-sm font-semibold text-white transition-colors hover:bg-[#2ea043] disabled:opacity-60"
          >
            {loading ? <Spinner /> : <ArrowRight className="h-4 w-4" aria-hidden />}
            {loading ? 'Memverifikasi…' : 'Masuk'}
          </button>
        </section>

        {/* Demo */}
        <div className="my-5 flex items-center gap-3 text-xs text-[#6e7681]">
          <span className="h-px flex-1 bg-[#30363d]" />
          atau
          <span className="h-px flex-1 bg-[#30363d]" />
        </div>

        <button
          onClick={signInDemo}
          className="flex min-h-11 w-full items-center justify-center gap-2 rounded-lg border border-[#30363d] bg-[#0d1117] text-sm font-semibold text-[#e6edf3] transition-colors hover:border-[#8b949e] hover:bg-[#161b22]"
        >
          <ArrowRight className="h-4 w-4" aria-hidden />
          Coba Mode Demo (tanpa token)
        </button>

        <a
          href="/gitpush.apk"
          download
          className="mt-4 flex min-h-10 w-full items-center justify-center gap-1.5 rounded-lg text-xs font-medium text-[#3fb950] transition-colors hover:bg-[#2386361a]"
        >
          <Package className="h-3.5 w-3.5" aria-hidden />
          Unduh APK Android native v1.0 (± 2,9 MB)
        </a>

        <p className="mt-6 flex items-start justify-center gap-1.5 text-center text-[11px] leading-relaxed text-[#6e7681]">
          <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
          Token disimpan hanya di perangkat Anda dan hanya dipakai untuk mengakses akun GitHub Anda sendiri.
        </p>
      </div>
    </main>
  );
}
