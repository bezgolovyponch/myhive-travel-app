// SSR About page — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/AboutPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyAbout from '../../../components/site/legacy/LegacyAbout';

const TITLE = 'About Trivlu — Group Travel Made Easy';
const DESCRIPTION =
  "We built Trivlu so best men can plan a stag the whole group actually agrees on. Here's who we are and why.";

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/about',
});

export default function Page() {
  return <LegacyAbout />;
}
