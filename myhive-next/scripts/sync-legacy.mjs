// Copies the canonical legacy CRA source into this project so the bundler never
// has to resolve modules outside the project root (avoids a second React copy
// being pulled from myhive-react-app/node_modules). legacy-src/ is gitignored:
// the single source of truth stays in myhive-react-app/src.
import { cpSync, rmSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const src = path.resolve(here, '../../myhive-react-app/src');
const dest = path.resolve(here, '../legacy-src');

if (!existsSync(src)) {
  console.error(`sync-legacy: source not found: ${src}`);
  process.exit(1);
}

rmSync(dest, { recursive: true, force: true });
cpSync(src, dest, {
  recursive: true,
  filter: (p) => !p.endsWith('.test.js') && !p.endsWith('setupTests.js'),
});
console.log(`sync-legacy: ${src} -> ${dest}`);
