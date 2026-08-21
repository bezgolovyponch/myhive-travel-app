import createMiddleware from 'next-intl/middleware';
import { routing } from './i18n/routing';

// Maps URLs onto the [locale] segment: bare URLs stay English (internal
// rewrite to /en/...), /de/... serves German, and /en/... redirects to the
// bare URL so the default locale never has two addresses.
export default createMiddleware(routing);

export const config = {
  // Skip the backend rewrite (/api), Next internals and anything with a file
  // extension (favicons, images, manifest, sitemap.xml, robots.txt). Public
  // page URLs never contain a dot.
  matcher: '/((?!api|_next|_vercel|.*\\..*).*)',
};
