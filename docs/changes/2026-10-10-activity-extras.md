# 2026-10-10 — Activity extras (optional add-ons), Prague catalogue content, research

Branch `worktree-activity-extras` (base `main` @ 4216d6e). Spec:
`docs/superpowers/specs/2026-10-10-activity-extras-design.md`; plan:
`docs/superpowers/plans/2026-10-10-activity-extras.md`; research report:
`docs/research/reports/Prague stag activity listings UX.md`.

## What shipped

**Backend**
- `Activity.extras` — JSON TEXT column (list of `{code, name, description, price, pricing:
  PER_GROUP|PER_PERSON, translations}`) via `ActivityExtrasConverter`; `BookingItem.extras`
  snapshot via `BookingItemExtrasConverter`; Flyway `V14__activity_extras.sql` (two additive
  `ADD COLUMN IF NOT EXISTS … TEXT`).
- `ActivityExtras` (normalise / validate / localise / canonical JSON / parse), limits: ≤ 10 per
  activity, code ≤ 40 `[a-z0-9-]` (generated from the name), name ≤ 80, description ≤ 300,
  price ≥ 0 scale ≤ 2. Write semantics: DTO `null` = unchanged, `[]` = clear.
- Pricing rule, four synced copies — `line = max(price × N, minPrice) + Σ extras`; extras are
  never floored and never package-discounted: `BookingService.lineBase/lineExtras`,
  `PriceQuoteService` (`Item.extraCodes`), `PlanAssembler`/`PlanPricer.itemExtras` (AI),
  `myhive-react-app/src/utils/tripPricing.js`. `VoteSessionService.flooredLine` untouched
  (ballots carry no extras).
- Booking: the client sends extra codes; the server resolves them against the catalogue and
  snapshots name/price/unit (`UNKNOWN_EXTRA` 400 under trusted pricing, dropped on the
  lenient lead path); `toExportRequest` and the lenient path's emails itemise the snapshot;
  `verifyChargeablePricing` recomputes with extras. Emails list `+ name: €X per group|person`
  (keys `email.itinerary.activity.extra.perGroup|perPerson`, EN/DE).
- CSV: optional `extras` column (LAST, after `featured_weight`), canonical JSON; absent =
  untouched, blank = clear, malformed/> 10 = `INVALID_EXTRAS`; export→import round-trips with
  no diff.
- Vote/pool/AI DTOs carry available `extras` (`VotePoolActivityDTO` also `selectedExtras`,
  `ResultActivityDTO`, `VoteActivityResponse`).
- AI planner: `CatalogActivity.extras`, `ItemDraft.extraCodes`, `ItemResult.extras` (priced),
  chat ops `ADD_EXTRA`/`REMOVE_EXTRA` (`EditRequest.extra`, `ExtraNameResolver`, applied in
  Java by `PackageEditor` after all placements; reasons `UNKNOWN_EXTRA`, `ALREADY_HAS_EXTRA`,
  `EXTRA_NOT_SELECTED`), prompt lists each activity's extras, confirmation sentence states the
  amount ("Added Private show to River Boat Cruise (+ €160 for the group) in the Basic
  package."), `select` returns `tripItems[].extras/selectedExtras`. Old `ai_generations.result`
  rows read back with empty lists. Contract: `docs/api/ai-planner-api.md`.

**Frontend (CRA source, synced into Next)**
- Cart items: `extras` (available) + `selectedExtras` (codes); `SET_ITEM_EXTRAS`; vote-replace
  keeps ticks; quote key and booking payload send only selected, still-offered codes.
- `ActivityCard`: one "Optional extras · …" chip, still no price. `ActivityDetailPage`: opt-in
  checklist (`ItemExtrasPicker`), summary per unit, selection scoped per activity.
  `ActivityPreviewModal`: read-only list. Trip Builder rows: chips with the group amount, inline
  "Add extras" panel (`ItemExtrasRow`, full-width on mobile). Dropdown: "+ €X extras".
- Admin: `ExtrasEditor` in the activity form, extras count column, booking detail lines incl.
  subtotal, AI bench and `AiPackageView` chips. i18n namespace `extras.*` + `cards.extras*`
  (EN/DE). Nothing is pre-selected anywhere (EU CRD Art. 22).

**Content & research**
- `docs/research/prague-catalogue-2026-10.csv` — 82 rows: 72 prod rows (ids from prod) with
  rewritten `description`/`includes`, 26 carriers with 69 extras (EN + DE), 10 new rows
  (blank id) with Pexels images (`prague-images-manifest.json`). Notes and 8 owner questions:
  `docs/research/prague-catalogue-notes.md`.
- Dev seed: `data-prague.sql` seeds the extras of 12 carriers.

## Verification
- Backend `./gradlew test`: 1414 tests, 0 failures. Jest: 98 suites / 745 tests.
- Content CSV import eligibility (scratch JUnit on the real file, ids remapped by slug to the dev
  seed): preview errors = exactly 10 × `IMAGE_UPLOAD_UNAVAILABLE` (new rows; dev has no R2),
  0 other errors, 0 warnings; with blank image URLs on the creates: 0 errors, apply = 72
  updated + 10 created.
- Browser (Playwright, CRA 3100 ↔ backend 8081): card chip, detail checklist, Trip Builder
  chips/panel, quote €477 → €333 when unticking a €160 group extra, booking total
  €370 = max(37 × 4, 290) + 20 × 4, no console errors; mobile layout fixed and re-verified.

## Rollout
1. Deploy backend (V14 is additive; old frontends send no extras).
2. Deploy frontend.
3. Owner imports `docs/research/prague-catalogue-2026-10.csv` through Admin → Import CSV
   (preview first; the 10 new rows need R2, which prod has). Export a backup CSV first.
4. Before `AI_ENABLED=true` for the public: cap the extras-catalogue line in the chat prompt
   (`PackagesView.extrasCatalog`) and re-measure prompt tokens on the prod catalogue.

## Hybrid mode: local backend on the production database (`proddb`)

Cherry-picked onto this branch from `wip/planner-trim-and-proddb` (commit f358599). What it is and
how it is run is documented in `myhive-backend/src/main/resources/application-proddb.properties`
and `myhive-backend/.env.proddb.example`; the short form:

- `cd myhive-backend && ./gradlew bootRun -Penv=proddb [--args='--server.port=8080']` — `-Penv=<name>`
  makes `bootRun` load `.env.<name>` instead of `.env`; the file must set
  `SPRING_PROFILES_ACTIVE=prod,proddb` and must not be combined with `--spring.profiles.active`
  (Gradle refuses both, to stop the H2 seed from booting with prod credentials exported).
- `.env.proddb` (gitignored) holds the Render **external** Postgres URL with `sslmode=require`,
  the DB user/password, Stripe **test** keys, the R2 keys, the OpenRouter key and models.
- The `proddb` overlay pins, as literals that an env value cannot flip: `app.email.enabled=false`,
  `app.scheduling.enabled=false` (no vote closing, reminders, digest, nightly cleanups, planner sweep
  from a laptop), `app.startup-repairs.enabled=false`, `spring.flyway.enabled=false`; root path on
  8080, local frontend URL, the shared Auth0 tenant, Cloudflare's always-pass Turnstile secret,
  DEBUG logging without bound parameters.
- **Every write lands on prod** (bookings, contacts, vote sessions, AI sessions, catalogue edits,
  CSV imports, R2 uploads) and the email that prod would send is skipped forever: do not close or
  start votes, book, submit the contact form or pay a deposit from here. AI sessions created here
  checkpoint in this branch's state shape; do not open their tokens on prod before the deploy.
- **Schema rule:** prod keeps `ddl-auto=validate`, so a branch whose entities are ahead of the
  prod schema does not start. This branch adds `activities.extras` and `booking_items.extras`
  (`V14__activity_extras.sql`, two additive nullable TEXT columns). Before the first hybrid run
  the migration has to be on prod: one start with Flyway switched back on applies exactly what the
  deploy would — `./gradlew bootRun -Penv=proddb --args='--spring.flyway.enabled=true --server.port=8080'` —
  and later starts go back to the default (Flyway off). The old prod code ignores the new columns.
- Local frontend: the running Next dev (`BACKEND_URL=http://localhost:8080`) and an admin login on
  the shared tenant work unchanged; the catalogue CSV can be imported for real through the local
  admin UI in this mode (images re-host to the real R2 bucket).

## Dev notes
- Windows dev: start the backend with `--spring.sql.init.encoding=UTF-8` (seed text garbles
  otherwise; pre-existing) and `CORS_ALLOWED_ORIGINS=http://localhost:<cra-port>` when the
  CRA runs on a port other than 3000. Port 8080 is often taken by Steam — use 8081.
- Jest from a worktree whose path contains `.claude` needs
  `"--testMatch=**/src/**/*.test.js"` (and `--maxWorkers=4` to avoid timeouts).

## Deferred (none block merge)
- Quote keys lines by `(activityId, packageId, codes)` — the base is counted twice only if two
  lines for one activity arrive (the CRA dedupes by id).
- `useTripLeadRestore` rebuilds items without `extras`/`selectedExtras` (the lead snapshot
  carries neither) — owner to decide whether restore should keep ticks.
- Email `PackageGroup.subtotal` still sums per-person prices (pre-existing design).
- Extras translations inside an extra are unbounded (no locale/length check);
  `ADD_EXTRA` on a retired activity answers with its already-chosen extras; test-hygiene nits
  (duplicated Thymeleaf setup in `ItineraryExtrasTemplateRenderTest`, exporter test derives
  its expected JSON from `canonicalJson`).

## CLAUDE.md bullet (CLAUDE.md is gitignored; paste under "Key Architectural Patterns")
- **Activity extras (optional add-ons)**: `Activity.extras` is a JSON TEXT column (list of `{code, name, description, price, pricing: PER_GROUP|PER_PERSON, translations}`) via `ActivityExtrasConverter`, Flyway `V14__activity_extras.sql`. Pricing: `line = max(price × N, minPrice) + Σ extras` — extras are never floored and never package-discounted (the package discount applies to the base only). Four synced copies: `BookingService.lineBase/lineExtras`, `PriceQuoteService`, `PlanAssembler` (via `PlanPricer.itemExtras`), `tripPricing.js`. Snapshot rule: the client sends extra codes only; the server resolves them against the catalog (unknown code -> `UNKNOWN_EXTRA` 400 under trusted pricing) and snapshots them on `BookingItem.extras`. CSV: optional `extras` column, last, canonical JSON (absent = untouched, blank cell = clear, malformed = `INVALID_EXTRAS`). AI chat ops `ADD_EXTRA`/`REMOVE_EXTRA` (applied in Java by `PackageEditor` after placements, no regeneration; the confirmation states the amount). Cart fields: `extras` (catalog snapshot at add-time) and `selectedExtras` (codes), reducer action `SET_ITEM_EXTRAS`. Nothing is ever pre-selected (EU CRD Art. 22).
