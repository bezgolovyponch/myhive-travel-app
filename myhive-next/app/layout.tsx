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

// GTM is skipped on localhost: the container/CookieYes aren't configured for it
// and throw a cross-origin "Script error." — same guard the CRA index.html used.
// Consent Mode v2 / CookieYes load through the GTM container itself.
const GTM_SNIPPET = `if(location.hostname!=='localhost'&&location.hostname!=='127.0.0.1'&&!location.hostname.endsWith('.localhost')){(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','GTM-KB7BJLDS');}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        {/* React 19 hoists these to <head> */}
        <link
          href="https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600&display=swap"
          rel="stylesheet"
        />
        <link
          rel="stylesheet"
          href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/regular/style.css"
        />
        <Script id="gtm" strategy="afterInteractive">
          {GTM_SNIPPET}
        </Script>
        {/* Turnstile loader (render=explicit) — safe on localhost: ContactForm
            uses Cloudflare's "always passes" test sitekey there. */}
        <Script
          src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
          strategy="afterInteractive"
        />
        <noscript>
          <iframe
            src="https://www.googletagmanager.com/ns.html?id=GTM-KB7BJLDS"
            height="0"
            width="0"
            style={{ display: 'none', visibility: 'hidden' }}
          />
        </noscript>
        {children}
      </body>
    </html>
  );
}
