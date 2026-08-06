// SSR Refund & Cancellation Policy — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/RefundPolicyPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyRefund from '../../../components/site/legacy/LegacyRefund';

const TITLE = 'Refund Policy | Trivlu';
const DESCRIPTION =
  "Trivlu's cancellation and refund terms for tours, activities, and packages.";

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/refund-policy',
});

export default function Page() {
  return <LegacyRefund />;
}
