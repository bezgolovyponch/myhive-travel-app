// The mockups' display face (FKGroteskNeue, loaded from a third-party CDN)
// falls back to Inter as its own first substitute; we ship Inter directly,
// self-hosted by next/font. landing.css reads it through --font-inter.
import { Inter } from 'next/font/google';

export const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
});
