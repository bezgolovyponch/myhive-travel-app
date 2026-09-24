// Checks the QWEN_* settings in myhive-backend/.env with ONE tiny real chat call per model, so a key /
// base-URL pair can be verified in seconds instead of restarting the backend. Never prints the key.
//   node dev-tools/ai-chat/check-key.mjs            (run from myhive-backend/)
//   node dev-tools/ai-chat/check-key.mjs <base-url> (try another endpoint without editing .env)
//
// A model list is NOT proof: some DashScope hosts answer GET /models with 200 for any key at all.
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_BASE = 'https://dashscope-intl.aliyuncs.com/compatible-mode/v1';

const env = {};
for (const line of (await readFile(join(HERE, '..', '..', '.env'), 'utf8')).split(/\r?\n/)) {
  const at = line.indexOf('=');
  if (at > 0 && !line.trimStart().startsWith('#')) {
    env[line.slice(0, at).trim()] = line.slice(at + 1).trim();
  }
}
const key = env.QWEN_API_KEY || '';
const base = (process.argv[2] || env.QWEN_BASE_URL || DEFAULT_BASE).replace(/\/+$/, '');
const models = [env.QWEN_CHAT_MODEL || 'qwen3.7-plus', env.QWEN_PLANNER_MODEL || 'qwen3.8-max'];
const redact = text => (key ? text.split(key).join('<key>') : text);

if (!key) {
  console.log('QWEN_API_KEY is missing from .env');
  process.exit(1);
}
console.log(`endpoint: ${base}`);
console.log(`key: sk-… (${key.length} chars)`);

let failed = false;
for (const model of models) {
  const started = Date.now();
  try {
    const response = await fetch(`${base}/chat/completions`, {
      method: 'POST',
      headers: { authorization: `Bearer ${key}`, 'content-type': 'application/json' },
      // The same switches the backend sends: JSON mode, thinking off.
      body: JSON.stringify({
        model,
        messages: [{ role: 'system', content: 'Reply with the JSON {"ok":true}.' }, { role: 'user', content: 'ping' }],
        response_format: { type: 'json_object' },
        enable_thinking: false,
        max_tokens: 30,
      }),
      signal: AbortSignal.timeout(60000),
    });
    const text = await response.text();
    if (response.ok) {
      const json = JSON.parse(text);
      console.log(`  ${model}: OK in ${Date.now() - started} ms -> ${json.choices[0].message.content.trim().slice(0, 60)}`);
    } else {
      failed = true;
      console.log(`  ${model}: HTTP ${response.status} ${redact(text).slice(0, 220)}`);
    }
  } catch (e) {
    failed = true;
    console.log(`  ${model}: ${e.name} ${redact(String(e.message)).slice(0, 160)}`);
  }
}
// exitCode, not process.exit(): exiting while fetch is still closing its sockets trips a libuv assertion on Windows.
process.exitCode = failed ? 1 : 0;
