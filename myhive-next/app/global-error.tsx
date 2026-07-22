'use client';

// Backstop for uncaught client exceptions anywhere in the tree (the legacy SPA
// has no top-level error boundary of its own). Must render its own <html>/<body>
// because it replaces the root layout when triggered.
export default function GlobalError({ reset }: { reset: () => void }) {
  return (
    <html lang="en">
      <body>
        <div style={{ padding: '4rem', textAlign: 'center' }}>
          <p>Something went wrong. Please refresh the page.</p>
          <button onClick={() => reset()}>Try again</button>
        </div>
      </body>
    </html>
  );
}
