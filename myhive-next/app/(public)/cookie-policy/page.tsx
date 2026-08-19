// SSR Cookie Policy — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/CookiePolicyPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyCookie from '../../../components/site/legacy/LegacyCookie';

const TITLE = 'Cookie Policy | Trivlu';
const DESCRIPTION =
  'Learn how Trivlu uses cookies and how to manage your cookie preferences.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/cookie-policy',
});

export default function Page() {
  return <LegacyCookie />;
}
