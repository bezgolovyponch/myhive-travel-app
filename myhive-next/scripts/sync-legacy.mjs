// Copies the canonical legacy CRA source into this project so the bundler never
// has to resolve modules outside the project root (avoids a second React copy
// being pulled from myhive-react-app/node_modules). Also copies CRA's static
// assets (favicons, logos, og-image, manifest) so brand updates in the CRA repo
// can't leave this app serving stale copies. legacy-src/ and public/ are
// gitignored: the single source of truth stays in myhive-react-app.
import { cpSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

// index.html is CRA's shell (app/layout.tsx owns the document here);
// robots.txt is owned by app/robots.ts (a public/ copy would conflict).
const PUBLIC_EXCLUDES = new Set(['index.html', 'robots.txt']);

const jobs = [
  {
    src: path.resolve(here, '../../myhive-react-app/src'),
    dest: path.resolve(here, '../legacy-src'),
    filter: (p) => !p.endsWith('.test.js') && !p.endsWith('setupTests.js'),
  },
  {
    src: path.resolve(here, '../../myhive-react-app/public'),
    dest: path.resolve(here, '../public'),
    filter: (p) => !PUBLIC_EXCLUDES.has(path.basename(p)),
  },
];

for (const { src, dest, filter } of jobs) {
  if (!existsSync(src)) {
    console.error(`sync-legacy: source not found: ${src}`);
    process.exit(1);
  }
  rmSync(dest, { recursive: true, force: true });
  cpSync(src, dest, { recursive: true, filter });
  console.log(`sync-legacy: ${src} -> ${dest}`);
}
