import type { Metadata, Viewport } from 'next';
import { notFound } from 'next/navigation';
import { hasLocale } from 'next-intl';
import { getMessages, getTranslations, setRequestLocale } from 'next-intl/server';
import { routing } from '@/i18n/routing';
import LegacyLocaleProvider from '@/components/LegacyLocaleProvider';

// og/twitter URLs must be absolute on the canonical host — WhatsApp/Telegram
// scrapers refuse redirected og:image URLs (apex 301s to www).
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'https://www.trivlu.com';

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: 'meta.site' });
  const title = t('title');
  const description = t('description');

  return {
    title,
    description,
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
      title,
      description,
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
}

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
//
// Forcing it is necessary but NOT sufficient, and the difference is invisible:
// CookieYes keeps its own domain allowlist, so on a host it does not recognise
// the banner still renders while window.getCkyConsent never initialises —
// measured on localhost, undefined even after clicking Accept, against a
// function under www.trivlu.com. utils/consent.js is deny-by-default, so
// ad_storage is never granted and the Meta Pixel stays blocked no matter what
// the visitor clicks. A forced host has to be added in the CookieYes dashboard
// too before "the banner appeared" means the consent stack actually works.
const GTM_FORCED = process.env.NEXT_PUBLIC_GTM_FORCE === 'true';

// Lifting the host gate must never silently fall back to the production
// container: everything the QA session clicks would land in the real GA4
// property and Meta audiences, and ad audiences cannot be un-polluted.
// Documenting that was not enough, so the id now has to be named explicitly —
// point it at a test container, or repeat GTM-KB7BJLDS when hitting production
// on purpose. Throwing beats degrading: a forced preview that quietly measures
// nothing is worse than a build that refuses.
if (GTM_FORCED && !process.env.NEXT_PUBLIC_GTM_ID) {
  throw new Error(
    'NEXT_PUBLIC_GTM_FORCE=true requires NEXT_PUBLIC_GTM_ID to be set explicitly. ' +
      'It otherwise defaults to the production container (GTM-KB7BJLDS), so the ' +
      'forced preview would push its traffic into real GA4/Meta audiences. ' +
      'Set a test container id, or repeat GTM-KB7BJLDS to opt into that.'
  );
}

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
//
// Read the raw variable, NOT the SITE_URL constant: that one falls back to the
// canonical origin so og:image stays absolute for link scrapers, and reusing it
// here made the gate fail OPEN — a preview that forgot to set the variable
// rendered the production iframe, i.e. the exact leak this gate prevents.
// Unset means "unknown deploy", which must resolve to no tracking.
const CANONICAL_HOST = /(^|\.)trivlu\.com$/;
function isCanonicalDeploy() {
  const configuredOrigin = process.env.NEXT_PUBLIC_SITE_URL;
  if (!configuredOrigin) return false;
  try {
    return CANONICAL_HOST.test(new URL(configuredOrigin).hostname);
  } catch {
    return false;
  }
}

export default async function RootLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }
  // Pins the locale for this render so getTranslations/getMessages read the
  // route param instead of request headers — required to keep ISR static.
  setRequestLocale(locale);
  // Messages for the legacy components' LocaleContext (client side). Already
  // merged with the English fallback by i18n/request.ts.
  const messages = await getMessages();

  return (
    // suppressHydrationWarning: browser auto-translate (Chrome rewrites lang
    // and adds class="translated-ltr" on <html> before hydration) and similar
    // extensions mutate this element's attributes; React would warn on every
    // such visit. Attribute-only and one element deep — real content
    // mismatches below still surface.
    <html lang={locale} suppressHydrationWarning>
      <head>
        {/* ТЗ §3 wants the loader as high in <head> as possible, and this is the
            only placement that gets there. React hoists <link precedence> and
            next/script hoists a src= script, but an INLINE script is hoisted by
            neither — with strategy="beforeInteractive" it still rendered into
            <body>, verified in .next/server/app/index.html. An explicit <head>
            in the root layout is what actually works. GTM and the consent stack
            it pulls must start at parse time; afterInteractive would hold the
            banner until hydration and undercount bounces. */}
        <script dangerouslySetInnerHTML={{ __html: GTM_SNIPPET }} />
        {/* Turnstile (render=explicit) is in <head> too, and as a plain async
            script rather than next/script: beforeInteractive emitted a loader
            <script> into <body> ahead of the GTM noscript, which §3 wants first.
            async+defer matches what the CRA injected. Safe on localhost —
            ContactForm uses Cloudflare's "always passes" test sitekey there. */}
        <script
          src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
          async
          defer
        />
      </head>
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
        {/* ТЗ §3: the noscript iframe belongs immediately after <body> opens.
            Next always emits its own <div hidden> as the first body child, which
            no layout can remove; this is otherwise the first element. */}
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
        <LegacyLocaleProvider locale={locale} messages={messages}>
          {children}
        </LegacyLocaleProvider>
      </body>
    </html>
  );
}
