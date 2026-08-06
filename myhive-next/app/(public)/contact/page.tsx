// SSR contact page — a thin server shell: metadata only. The markup is the canonical
// CRA page (legacy-src/pages/ContactPage.js), so this route cannot drift from what the SPA renders.
import { pageMetadata } from '../../../lib/seo';
import LegacyContact from '../../../components/site/legacy/LegacyContact';

const TITLE = 'Contact Trivlu — Talk to the Team';
const DESCRIPTION =
  "Questions about your stag do or booking? Reach the Trivlu team by WhatsApp, Messenger or email — we're quick to reply.";

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/contact',
});

export default function Page() {
  return <LegacyContact />;
}
