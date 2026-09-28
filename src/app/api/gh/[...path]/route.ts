import { NextRequest } from 'next/server';

/**
 * Minimal proxy to api.github.com.
 * The user's Personal Access Token travels in the `x-gh-token` header and is
 * forwarded as a Bearer token. All requests stay on this origin, so the token
 * is only ever attached to GitHub calls made by this server.
 */

const GH_BASE = 'https://api.github.com';

type Ctx = { params: Promise<{ path: string[] }> };

async function proxy(req: NextRequest, ctx: Ctx): Promise<Response> {
  const { path } = await ctx.params;
  const token = req.headers.get('x-gh-token');

  if (!token) {
    return Response.json({ message: 'Token GitHub tidak ditemukan di header.' }, { status: 401 });
  }

  const incoming = new URL(req.url);
  const upstreamUrl = `${GH_BASE}/${path.map(decodeURIComponent).map(encodeURIComponent).join('/')}${incoming.search}`;

  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'GitPush-App',
    'Content-Type': 'application/json',
  };

  let body: string | undefined;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    body = await req.text();
  }

  try {
    const upstream = await fetch(upstreamUrl, {
      method: req.method,
      headers,
      body,
      cache: 'no-store',
    });
    const text = await upstream.text();
    return new Response(text, {
      status: upstream.status,
      headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
    });
  } catch {
    return Response.json({ message: 'Gagal menghubungi GitHub API.' }, { status: 502 });
  }
}

export const GET = proxy;
export const POST = proxy;
export const PATCH = proxy;
export const PUT = proxy;
export const DELETE = proxy;
export const dynamic = 'force-dynamic';
