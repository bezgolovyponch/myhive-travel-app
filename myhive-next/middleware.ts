import createMiddleware from 'next-intl/middleware';
import { NextResponse, type NextRequest } from 'next/server';
import { routing } from './i18n/routing';
import { isGone } from './lib/gone';

// Maps URLs onto the [locale] segment: bare URLs stay English (internal
// rewrite to /en/...), /de/... serves German, /en/... redirects to the bare
// URL so the default locale never has two addresses, and, via
// routing.pathnames, German public paths (/de/jga-prag/...) map onto the
// EN-named route folders.
const intl = createMiddleware(routing);

export default function middleware(req: NextRequest) {
  // 410 before locale handling: these URLs are gone in every locale.
  if (isGone(req.nextUrl.pathname)) {
    return new NextResponse('Gone', { status: 410, headers: { 'content-type': 'text/plain' } });
  }
  return intl(req);
}

export const config = {
  // Skip the backend rewrite (/api), Next internals and anything with a file
  // extension (favicons, images, manifest, sitemap.xml, robots.txt). Public
  // page URLs never contain a dot.
  matcher: '/((?!api|_next|_vercel|.*\..*).*)',
};
