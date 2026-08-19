// Wraps `next dev` with a watcher on the canonical CRA sources: predev's
// one-shot sync otherwise leaves edits to myhive-react-app invisible until a
// server restart — the bundler watches the legacy-src copy, not the original.
import { spawn, spawnSync } from 'node:child_process';
import { watch } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const syncScript = path.join(here, 'sync-legacy.mjs');
const watched = [
  path.resolve(here, '../../myhive-react-app/src'),
  path.resolve(here, '../../myhive-react-app/public'),
];

let timer = null;
for (const dir of watched) {
  watch(dir, { recursive: true }, (_event, file) => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      console.log(`[dev] legacy change (${file ?? 'unknown'}) — re-syncing`);
      spawnSync(process.execPath, [syncScript], { stdio: 'inherit' });
    }, 300);
  });
}

// Inherit stdio so next dev owns the terminal; Ctrl-C reaches both processes
// via the shared process group, and we exit when next dev does.
const next = spawn('npx', ['next', 'dev'], { stdio: 'inherit' });
next.on('exit', (code) => process.exit(code ?? 0));
