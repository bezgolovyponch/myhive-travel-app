// Deeper vote URLs (/waiting, /results, ...) — same SPA mount, same metadata
// (shared links occasionally point at subpaths).
import type { Metadata } from 'next';
import LegacyAppShim from '../../../../components/LegacyAppShim';
import { voteMetadata } from '../../../../lib/vote-meta';

export const dynamic = 'force-dynamic';

export async function generateMetadata({
  params,
}: {
  params: Promise<{ shareToken: string }>;
}): Promise<Metadata> {
  const { shareToken } = await params;
  return voteMetadata(shareToken);
}

export default function VoteSubPage() {
  return <LegacyAppShim />;
}
