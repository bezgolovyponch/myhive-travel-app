// SSR Privacy Policy — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/PrivacyPolicyPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyPrivacy from '../../../components/site/legacy/LegacyPrivacy';

const TITLE = 'Privacy Policy | Trivlu';
const DESCRIPTION =
  'How Trivlu collects, uses, and protects your personal data under the GDPR.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/privacy-policy',
});

export default function Page() {
  return <LegacyPrivacy />;
}
