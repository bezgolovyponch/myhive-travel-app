// Paths that server-rendered pages hand off to the SPA. Kept here so the
// landings cannot drift apart.

// The group-vote funnel entry. The landings' CTAs open the vote setup modal in
// place and only use this as the anchors' crawlable href (and as the cart
// panel's empty-state CTA target — see LandingCart.tsx); a confirmed setup
// travels in /vote/new's query string (legacy-src/utils/voteSetup.js), which
// survives the full page load out of a server-rendered page. Callers must
// localize it (localizePath / useLocalePath): a hard navigation never passes
// through LegacyRouter, so nothing adds the prefix for them.
export const VOTE_FLOW_PATH = '/vote/new';
