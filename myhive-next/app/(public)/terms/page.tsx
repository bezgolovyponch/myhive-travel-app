// SSR Terms & Conditions — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/TermsPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyTerms from '../../../components/site/legacy/LegacyTerms';

const TITLE = 'Terms & Conditions | Trivlu';
const DESCRIPTION =
  'The terms and conditions that apply when you book group trips and experiences through Trivlu.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/terms',
});

export default function Page() {
  return <LegacyTerms />;
}
