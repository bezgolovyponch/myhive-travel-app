// Dev-only playground for the AI planner API (docs/api/ai-planner-api.md).
// Serves index.html and forwards /ai/* to the backend, so the browser sees one origin and CORS never
// comes into it. No dependencies: `node server.mjs`, then open http://localhost:4173
//
//   BACKEND_URL  backend base, default http://localhost:8080 (use http://localhost:8080/api for a prod-profile run)
//   PORT         port of this playground, default 4173
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BACKEND_URL = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');
const PORT = Number(process.env.PORT || 4173);
const HERE = dirname(fileURLToPath(import.meta.url));

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function forward(req, res) {
  const started = Date.now();
  const body = req.method === 'GET' || req.method === 'HEAD' ? undefined : await readBody(req);
  try {
    const upstream = await fetch(BACKEND_URL + req.url, {
      method: req.method,
      headers: { 'content-type': req.headers['content-type'] || 'application/json', accept: 'application/json' },
      body,
    });
    const payload = Buffer.from(await upstream.arrayBuffer());
    res.writeHead(upstream.status, {
      'content-type': upstream.headers.get('content-type') || 'application/json',
      'x-upstream-ms': String(Date.now() - started),
    });
    res.end(payload);
    console.log(`${req.method} ${req.url} -> ${upstream.status} (${Date.now() - started} ms)`);
  } catch (e) {
    // The backend is down or still starting: answer in the API's own error shape so the page can show it.
    res.writeHead(502, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ status: 502, error: 'BACKEND_UNREACHABLE', message: `${BACKEND_URL}: ${e.message}` }));
    console.log(`${req.method} ${req.url} -> backend unreachable: ${e.message}`);
  }
}

createServer(async (req, res) => {
  if (req.url.startsWith('/ai/') || req.url.startsWith('/destinations')) {
    await forward(req, res);
    return;
  }
  if (req.url === '/' || req.url.startsWith('/?') || req.url === '/index.html') {
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
    res.end(await readFile(join(HERE, 'index.html')));
    return;
  }
  res.writeHead(404, { 'content-type': 'text/plain' });
  res.end('not found');
}).listen(PORT, '127.0.0.1', () => {
  console.log(`AI planner playground: http://localhost:${PORT}  ->  ${BACKEND_URL}`);
});
