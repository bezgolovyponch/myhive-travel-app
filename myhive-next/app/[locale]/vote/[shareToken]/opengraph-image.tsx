import { ImageResponse } from 'next/og';
import { api } from '../../../../lib/api';

export const runtime = 'nodejs';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const alt = 'Group vote on Trivlu';

// Satori's image loader throws on a non-2xx/undecodable response, which would
// otherwise crash the whole ImageResponse for one rotten activity photo (seed
// and user-supplied URLs do rot — e.g. an Unsplash asset returning 404 HTML).
// HEAD-check candidates first so a dead URL degrades to fewer photos instead
// of a 500. Bounded with a timeout too: a slow/hanging host must not stall
// the whole OG response for scrapers — an aborted probe just drops that tile.
async function reachableImageUrls(urls: string[]): Promise<string[]> {
  const checks = await Promise.all(
    urls.map(async (u) => {
      try {
        const res = await fetch(u, { method: 'HEAD', signal: AbortSignal.timeout(1500) });
        return res.ok && (res.headers.get('content-type') ?? '').startsWith('image/') ? u : null;
      } catch {
        return null;
      }
    })
  );
  return checks.filter((u): u is string => !!u);
}

export default async function OgImage({
  params,
}: {
  params: Promise<{ shareToken: string }>;
}) {
  const { shareToken } = await params;
  let session = null;
  let activities: { imageUrl?: string | null }[] = [];
  try {
    [session, activities] = await Promise.all([
      api.getVoteSession(shareToken),
      api.getVoteActivities(shareToken).then((a) => a ?? []),
    ]);
  } catch {
    // fall through to brand-only image
  }
  const candidates = activities
    .map((a) => a.imageUrl)
    .filter((u): u is string => !!u && /^https?:\/\//.test(u))
    .slice(0, 4);
  const photos = await reachableImageUrls(candidates);
  const who = session?.groomName ? `${session.groomName}'s stag do` : 'the stag do';
  const voted = session?.participantCount ?? 0;

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          background: '#0d0b14',
          color: '#ffffff',
          fontFamily: 'sans-serif',
        }}
      >
        <div style={{ display: 'flex', flex: 1 }}>
          {photos.length > 0 ? (
            photos.map((src, i) => (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                key={i}
                src={src}
                alt=""
                style={{ width: `${100 / photos.length}%`, height: '100%', objectFit: 'cover' }}
              />
            ))
          ) : (
            <div style={{ display: 'flex', flex: 1, alignItems: 'center', justifyContent: 'center', fontSize: 64 }}>
              Trivlu
            </div>
          )}
        </div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '28px 48px',
            fontSize: 44,
            fontWeight: 700,
          }}
        >
          <span>Vote on {who}</span>
          <span style={{ color: '#b9a7ff' }}>
            {voted > 0 ? `${voted} voted` : 'Trivlu'}
          </span>
        </div>
      </div>
    ),
    size
  );
}
