// Shared metadata builder for /vote/[shareToken] and its subpaths. Not
// exported from a page.tsx: Next 15 pages may only export route-recognized
// symbols (default, generateMetadata, dynamic, etc.), so a shared helper used
// by two page files has to live outside the route tree.
import type { Metadata } from 'next';
import { api } from './api';
import { pageMetadata } from './seo';

export async function voteMetadata(shareToken: string): Promise<Metadata> {
  let session = null;
  try {
    session = await api.getVoteSession(shareToken);
  } catch {
    // backend down → generic tags beat a 500 on a shared link
  }
  const who = session?.groomName ? `${session.groomName}'s stag do` : 'the stag do';
  const voted = session?.participantCount ?? 0;
  const title = `Vote on ${who} — Trivlu`;
  const description = session
    ? `${voted} ${voted === 1 ? 'vote is' : 'votes are'} in for ${session.destinationName}. Pick the activities you want — it takes a minute.`
    : 'Pick the activities you want for the trip — it takes a minute.';
  return pageMetadata({
    title,
    description,
    path: `/vote/${shareToken}`,
    noindex: true,
  });
}
