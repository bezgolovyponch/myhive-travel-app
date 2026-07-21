// Optional catch-all: in Ф0 the legacy SPA owns every route and does its own
// client-side routing. Ф1 adds real Server Component pages for public URLs,
// which take precedence over this catch-all, and narrows it to (legacy) subtrees.
export default function CatchAllPage() {
  return <div>myhive-next skeleton — legacy app mounts here in Task 3</div>;
}
