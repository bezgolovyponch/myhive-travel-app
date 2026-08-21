// Wraps `next dev` with a watcher on the canonical CRA sources: predev's
// one-shot sync otherwise leaves edits to myhive-react-app invisible until a
// server restart — the bundler watches the legacy-src copy, not the original.
import { spawn, spawnSync } from 'node:child_process';
import { cpSync, existsSync, rmSync } from 'node:fs';
import { watch } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const syncScript = path.join(here, 'sync-legacy.mjs');

// Mirrors the jobs in sync-legacy.mjs (source → dest + filter). Watch events
// are synced per-path rather than re-running the full script: the full sync
// rm+recreates legacy-src, which kills webpack's directory watchers on Windows
// — after that, edits stop reaching the dev server until a restart.
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

function syncOne(job, file) {
  const src = path.join(job.src, file);
  const dest = path.join(job.dest, file);
  if (!existsSync(src)) {
    // Deleted at the source: mirror the deletion.
    rmSync(dest, { recursive: true, force: true });
    return;
  }
  if (!job.filter(src)) return;
  cpSync(src, dest, { recursive: true, filter: job.filter });
}

let timer = null;
const pending = new Map(); // job -> Set of changed relative paths (null = unknown)
for (const job of jobs) {
  watch(job.src, { recursive: true }, (_event, file) => {
    if (!pending.has(job)) pending.set(job, new Set());
    pending.get(job).add(file ?? null);
    clearTimeout(timer);
    timer = setTimeout(() => {
      for (const [j, files] of pending) {
        if (files.has(null)) {
          // Watcher couldn't name the path — fall back to the full clean sync.
          console.log('[dev] legacy change (unknown path) — full re-sync');
          spawnSync(process.execPath, [syncScript], { stdio: 'inherit' });
        } else {
          for (const f of files) {
            console.log(`[dev] legacy change (${f}) — syncing`);
            syncOne(j, f);
          }
        }
      }
      pending.clear();
    }, 300);
  });
}

// Inherit stdio so next dev owns the terminal; Ctrl-C reaches both processes
// via the shared process group, and we exit when next dev does.
// shell on Windows: npx is npx.cmd there, which spawn() can neither find nor
// (since the CVE-2024-27980 fix) execute without a shell.
const next = spawn('npx', ['next', 'dev'], {
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
next.on('exit', (code) => process.exit(code ?? 0));
