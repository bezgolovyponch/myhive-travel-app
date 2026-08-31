// SSR shell for the vote share link: real OG tags for WhatsApp/Meta scrapers,
// body is the same client-only SPA as before (LegacyAppShim). noindex stays —
// tokenized links must not be indexed, but scrapers still read OG from head.
import type { Metadata } from 'next';
import LegacyAppShim from '../../../../components/LegacyAppShim';
import { voteMetadata } from '../../../../lib/vote-meta';

export const dynamic = 'force-dynamic';

interface PageParams {
  params: Promise<{ shareToken: string }>;
}

export async function generateMetadata({ params }: PageParams): Promise<Metadata> {
  const { shareToken } = await params;
  return voteMetadata(shareToken);
}

export default function VotePage() {
  return <LegacyAppShim />;
}
