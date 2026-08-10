import type { Metadata, Viewport } from 'next';
import Script from 'next/script';

// og/twitter URLs must be absolute on the canonical host — WhatsApp/Telegram
// scrapers refuse redirected og:image URLs (apex 301s to www).
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';

const TITLE = 'Trivlu — Group Travel Made Easy';
const DESCRIPTION =
  'Turn group travel chaos into epic adventures with zero stress. Trivlu is the first AI trip maker for multi-traveler experiences.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  manifest: '/manifest.json',
  icons: {
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/favicon-16.png', sizes: '16x16', type: 'image/png' },
      { url: '/favicon-32.png', sizes: '32x32', type: 'image/png' },
    ],
    apple: '/apple-touch-icon.png',
  },
  openGraph: {
    title: TITLE,
    description: DESCRIPTION,
    type: 'website',
    url: SITE_URL,
    images: [
      { url: `${SITE_URL}/og-image.png`, width: 1000, height: 1000, type: 'image/png' },
    ],
  },
  twitter: {
    card: 'summary',
    images: [`${SITE_URL}/og-image.png`],
  },
};

export const viewport: Viewport = {
  themeColor: '#000000',
};

const GTM_ID = process.env.NEXT_PUBLIC_GTM_ID || 'GTM-KB7BJLDS';

// GTM runs ONLY on the canonical domain (allowlist, not the CRA's localhost
// blocklist): the Ф0 preview URL must not pump QA traffic into the production
// container/ad audiences, and localhost still throws a cross-origin "Script
// error." — Consent Mode v2 / CookieYes load through the GTM container itself.
//
// Because the whole stack arrives through the container, that gate is also why a
// preview shows no cookie banner, no events and no Meta Pixel: none of them are
// loaded by this app directly. NEXT_PUBLIC_GTM_FORCE=true lifts the host check so
// a preview (or localhost) can verify the stack before the domain cutover.
// Whatever it collects lands in the container NEXT_PUBLIC_GTM_ID names, so point
// that at a test container when the production numbers matter.
const GTM_FORCED = process.env.NEXT_PUBLIC_GTM_FORCE === 'true';
const GTM_GUARD = GTM_FORCED ? 'true' : `/(^|\\.)trivlu\\.com$/.test(location.hostname)`;
const GTM_SNIPPET = `if(${GTM_GUARD}){(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','${GTM_ID}');}`;

// The <noscript> fallback cannot read location.hostname, so it needs a
// server-side equivalent of the same allowlist or it fires unconditionally —
// which let no-JS preview and localhost traffic reach the production container
// despite the guarantee above. NEXT_PUBLIC_SITE_URL is the deploy's own notion
// of its canonical origin, so test its host with the same pattern.
const CANONICAL_HOST = /(^|\.)trivlu\.com$/;
function isCanonicalDeploy() {
  try {
    return CANONICAL_HOST.test(new URL(SITE_URL).hostname);
  } catch {
    return false;
  }
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {/* precedence makes React 19 hoist these stylesheet links into <head> */}
        <link
          href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600&display=swap"
          rel="stylesheet"
          precedence="default"
        />
        <link
          rel="stylesheet"
          href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/regular/style.css"
          precedence="default"
        />
        {/* Plain synchronous script, first in body: GTM + the consent stack it
            loads must start at parse time, before any content — the CRA served
            this from <head>, and afterInteractive would undercount bounces and
            delay the consent banner until after hydration. */}
        <script dangerouslySetInnerHTML={{ __html: GTM_SNIPPET }} />
        {/* Turnstile loader (render=explicit) — beforeInteractive so the
            download starts from the initial HTML like CRA's head injection,
            not after the client-only SPA hydrates. Safe on localhost:
            ContactForm uses Cloudflare's "always passes" test sitekey there. */}
        <Script
          src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
          strategy="beforeInteractive"
        />
        {(isCanonicalDeploy() || GTM_FORCED) && (
          <noscript>
            <iframe
              src={`https://www.googletagmanager.com/ns.html?id=${GTM_ID}`}
              height="0"
              width="0"
              style={{ display: 'none', visibility: 'hidden' }}
            />
          </noscript>
        )}
        {children}
      </body>
    </html>
  );
}
