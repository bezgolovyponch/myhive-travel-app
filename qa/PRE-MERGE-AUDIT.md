# Pre-merge audit

Run this before anything is merged to `main` (a merge deploys). Every audit here exists because the thing
it checks broke in real use, or was found broken by a test on the real catalogue; the case is named next to it.
Run the audits whose scope the change touches; when unsure, run all of 1-4 (about ten minutes).

## Which audits a change needs

| The change touches | Run |
|---|---|
| Anything under `myhive-backend/` | 1, 2 |
| `myhive-backend/.../ai/**` (planner, chat, prompts, edits) | 1, 2, 3 (all parts), 5 |
| Prices, quotes, extras, packages, cart, booking totals | 1, 3 (browse), 6 |
| `myhive-react-app/src/**` or `myhive-next/**` (any page or component) | 1, 3 (browse + errors; AI if the planner page or a shared component changed), 4 |
| The activity card, the cart panel, the Trip Builder, the planner page | 3 (all parts), 4 |
| Catalogue data: seed, CSV import, admin edits to activities/packages/extras | 3 (browse), 6, 7 |
| CORS, proxy, hostnames, `services/config.js`, deploy or environment settings | 4, 8 |
| Planner prompts, response schema, model or provider settings | 2, 3 (AI), 5 |
| Memory, JVM flags, dependencies, Dockerfile | 8 |

## The audits

### 1. Test suites

```bash
cd myhive-backend && sh ./gradlew test                       # 1,426 tests on 2026-10-12
cd myhive-react-app && CI=true npx react-scripts test --watchAll=false   # 753 tests
```

Pass: no failures. `VoteSessionLeadCaptureFailureTest` has failed once in a full run and passed alone and on
re-run (order-dependent); re-run before treating it as a regression.

### 2. Planner fuzz gate, on production's catalogue

*Case: the fallback, the last stop when the model fails, shipped plans with tiers out of price order in 1,394
of 40,600 brief shapes (most of them one-day trips). Nothing validated it. Found 2026-10-12.*

```bash
sh qa/export-catalogue.sh https://www.trivlu.com/api /tmp/trivlu-catalogue
cd myhive-backend && PLANNER_FUZZ_DIR=/tmp/trivlu-catalogue sh ./gradlew test --tests '*PlannerInvariantFuzzTest'
```

Pass: the test passes. It fails when the fallback ships a plan that breaks a rule, or when the validator
accepts a draft an independent rule check rejects (0 of 40,000 on 2026-10-12). Numbers land in
`/tmp/trivlu-catalogue/fuzz-report.json`.

### 3. Planner UX kit: browse flow, AI chat, error messages

*Cases: extras that could not be added from the cart; a card with no checkboxes in the planner; a chat that
named prices; cart and Trip Builder stating different prices; "this chat has expired" in a private window;
"no connection" for a backend error; an undo lost on reload.*

```bash
# backend and frontend running; after a catalogue change first: rm -rf myhive-next/.next/cache/fetch-cache
cd qa/planner-ux-kit && npm install && node kit.mjs
KIT_ONLY=browse node kit.mjs      # or: ai | errors
```

Pass: no `FAIL` or `ERROR` beyond the open ones listed at the end of this file, and no new `FLAW`.
What each check means is in `qa/planner-ux-kit/README.md`. The AI part makes about 15 model calls.

It checks, among others, the rules the owner set on 2026-10-12:

- nothing is pre-ticked; a tick reaches the plan only through "Add to trip" or "Update";
- the card is the same wherever it opens;
- "Add to trip" on a card just adds, nothing opens;
- one price for the plan, shaped "from X"; no price per activity, no exact total; extras named, not priced,
  outside the card;
- "Package price: 10% off" is said under that price in the cart panel, the Trip Builder and the AI plan;
- the chat never names what an extra costs, asks which extra once after a plain add, and is not asked again
  after an add confirmed on the card.

### 4. The same site from a phone on the network

*Cases, both 2026-10-12: every write from `http://192.168.x.x:3000` was refused by CORS and shown as "No
connection"; then the planner sent the destination "192" (the first label of the address) and showed "This
chat has expired".*

```bash
cd qa/planner-ux-kit && KIT_BASE=http://<this machine's LAN address>:3000 KIT_ONLY=ai KIT_TYPED=0 node kit.mjs
```

Pass: A02 passes (the first message is accepted, `destinationSlug` is `prague`, no error on screen). Then open
`/plan` on a real phone once: the kit's browser is desktop Chromium at phone size, not a phone.

### 5. Generation speed

*Case: the model left out the empty last day in two answers of five and each one cost a second planner call
of 5-6 s; found by benchmark 2026-10-12.*

When prompts, the answer schema or the model change:

```bash
cd myhive-backend && K=<the key from .env> python3 ../docs/research/planner-speed/bench-compose.py 5
grep 'llm call model=' <backend log> | tail -30      # medians: chat 3.8 s, compose 5.6 s, copy 7.0 s
grep -c 'planner draft rejected' <backend log>        # a rejected draft is a second compose call
```

Pass: compose is not slower than the baseline in `docs/research/planner-speed/`, and no more answers break
the rules than before. A change of answer shape also needs a side-by-side read of real plans: the benchmark
checks rules, not taste.

### 6. Catalogue pages are fresh

*Case: after the extras were added, a card offered 4 extras while the catalogue had 5, and a package's
activities had no extras button, until the page cache was cleared. Pages are cached for an hour
(`revalidate = 3600`).*

After any catalogue change, on the environment it was made in: open a changed activity's card and compare
with `GET /api/activities/slug/<slug>`. The kit checks one activity as B03b.

### 7. Local catalogue matches production's extras

*Case: locally 12 activities had extras, in production 26; cards opened in the planner looked broken.*

```bash
python3 - <<'PY'
import json, urllib.request
def carriers(base):
    req = lambda p: json.load(urllib.request.urlopen(urllib.request.Request(base + p, headers={'User-Agent': 'curl'})))
    dest = [d for d in req('/destinations') if d['slug'] == 'prague'][0]
    return {a['slug']: len(a['extras']) for a in req('/activities?destinationId=' + dest['id']) if a.get('extras')}
prod, local = carriers('https://www.trivlu.com/api'), carriers('http://localhost:8080')
print('production', len(prod), 'local', len(local))
print('differences:', {s: (prod.get(s), local.get(s)) for s in set(prod) | set(local) if prod.get(s) != local.get(s)})
PY
```

Pass: the only differences are ones that are meant (on 2026-10-12: "Topless hostess" on three boats, local
only, price not confirmed).

### 8. After the deploy: the backend stays up through a build

*Case, production 2026-10-12: the JVM died while building packages (512 MiB instance); every poll answered
502 for 90 s and the chat said "took too long". Earlier: six OOM kills, `docs/incidents/2026-10-06-...`.*

```bash
curl -s https://www.trivlu.com/api/actuator/health          # {"status":"UP"}
# then one real plan on https://www.trivlu.com/plan, start to finish, and:
curl -s https://www.trivlu.com/api/ai/generations/<id from the network tab>   # status READY, not FAILED/STALE
```

Pass: READY. On Render, no `server_failed` / `oomKilled` event since the deploy, and the `JVM memory:` log line
stays under the limits in the postmortem. A build that ends `STALE` means the process restarted under it.

## Open on 2026-10-12 (a merge must not add to this list)

| What | Seen by |
|---|---|
| Undo is lost on reload; the page and the server then hold different plans | kit A22 |
| "X with an extra" for an activity not in the plan adds nothing | kit T05 |
| The extras hint on a listing card wraps to five lines | kit B02 |
| Extras' small text on the card is 3.85:1 | kit B06 |
| A refused first message has no "Try again" | kit E04 |
| An activity with five extras: the chat offers four and does not mention the fifth | kit A15 (only when such an activity is the one added) |
| Baku, one-day trips: 296 brief shapes have no valid fallback | fuzz, other destinations |
| Titles, descriptions and "why" lines are model text nobody verifies | `docs/research/planner-generation-strategy/` |
| The backend runs on a 512 MiB instance | audit 8 |
| "10% off" is now stated: whether it is guaranteed, and against which reference price, is the owner's call | discount proposal, 2026-10-12 |

## Keeping this file true

A new audit goes in only with the real case that made it necessary. When an open item is fixed, move its
check into the "must pass" set and delete the row. When a rule of the owner changes, change the kit's check
first, then the code.
