// Paths that server-rendered pages hand off to the SPA. Kept here so the
// homepage and the landings cannot drift apart.

// The group-vote funnel entry. Server-rendered pages cannot open the vote setup
// modal in place: its confirm handler passes the setup through react-router
// location state, which no full page load can carry. /vote/new exists for
// exactly this reason — it mounts the SPA and opens the modal. Callers must
// localize it (localizePath / useLocalePath): this is a hard navigation, so
// LegacyRouter never sees it to add the prefix itself.
export const VOTE_FLOW_PATH = '/vote/new';
