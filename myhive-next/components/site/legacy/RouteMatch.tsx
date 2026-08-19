'use client';

// CRA pages read their identifiers with useParams(), which only resolves inside
// a matched <Route>. LegacyRouter supplies the real pathname as the router
// location, so re-declaring the one CRA route pattern here is enough for the
// page to see the same params it sees in the SPA. Matching is pure computation
// over the location, so it works during server render too.
import { Routes, Route } from 'react-router-dom';

export default function RouteMatch({
  pattern,
  children,
}: {
  pattern: string;
  children: React.ReactNode;
}) {
  return (
    <Routes>
      <Route path={pattern} element={children} />
    </Routes>
  );
}
