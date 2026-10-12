# Activity Extras (optional add-ons) — Design

**Date:** 2026-10-10
**Status:** Drafted autonomously; decisions below are assumptions the owner can overturn
**Research:** `docs/research/reports/Prague stag activity listings UX.md` (competitor catalogues,
add-on presentation, UX evidence, imagery licensing)
**Apps:** `myhive-backend`, `myhive-react-app` (UI source), `myhive-next` (no change beyond the
synced CRA source)
**Builds on:** `2026-07-21-activity-min-price-design.md` (per-line pricing, snapshot pattern,
CSV optional column), `2026-09-17-ai-planner-edits-design.md` (chat edits applied in Java)

## Problem

Competitors sell adult entertainment and other upgrades both as standalone products and as
*variants of a carrier activity*: dinner + show, cruise + show, limo + show, bar crawl + club
entry, boat + unlimited drinks. Trivlu's catalogue models only the standalone form ("Steak &
Private Show" next to the plain steak dinner), so:

- the organizer cannot say "the river cruise, with a private show" — the planner chat drops the
  request (`ChatTurnNode` split-wish guard) because no single activity is both;
- a boat party cannot be upgraded with a photographer, open bar or a show without a new catalogue
  row per combination;
- the catalogue copy and cards do not show the upgrade path at all, while every competitor shows
  one (LNOF "Add upgrades", Pissup "Add-Ons", Chillisauce "Add Ons", Prague Stag Fun "Hot
  add-ons" / "Meal upgrades").

## Intent (what the owner asked for, restated)

1. Analyse competitors' Prague listings and the proven UI for activity cards, descriptions,
   "what's included" and optional features such as private/strip shows. **Done:** the research
   report above.
2. Add optional "extras" to the product card, the Trip Builder item ("dashboard item") and the AI
   chat so the chat can accept and fulfil "add X to Y" requests; test with regression tests.
3. Gather Prague activities from competitors, rewrite the copy per the analysis, prepare a CSV
   with the catalogue (existing rows rewritten, new rows added) and a coherent, licence-safe
   image set.
4. Validate the CSV for import eligibility on a local run, check it renders well, then import.

This spec covers item 2 (the feature) and the data contract that items 3–4 depend on (the CSV
`extras` column). Content and import are an operations phase after the code ships.

**Assumptions made because the owner could not be asked mid-task** (each is cheap to reverse):

| # | Assumption | Alternative |
|---|---|---|
| A1 | Extras are modelled as a JSON list on the activity, not a new table | `activity_extras` table — more code, same behaviour |
| A2 | Extras are never floored by the group minimum and never discounted by a package | include extras in the discounted sum |
| A3 | Extras are opt-in on the detail page and the Trip Builder line; cards only *signal* them | toggles on the card itself (rejected by the research: cards must stay scannable, and the card shows no price by design) |
| A4 | Only two pricing units: `PER_GROUP` and `PER_PERSON` | add `PER_HOUR` / headcount caps later |
| A5 | Extras are not on the vote ballot; the organizer picks them after the vote | per-extra voting |
| A6 | Competitor photos are **not** copied (copyright); images come from Pexels/Unsplash | — |
| A7 | The planner model never picks extras at generation time; extras enter only via chat edits | add `extraCodes` to the planner JSON schema later |

## Semantics

> **2026-10-12, owner:** extras are never discounted, and that now holds for the plan's "from" price too:
> `FromPrice` takes its 10% margin from the activities only and adds the extras in full. The margin is
> illustrative and is announced nowhere. The chat suggests only a private show, a topless hostess or more
> bullets, and never a price; every other extra is chosen on the activity's card.

- An **extra** belongs to exactly one activity and has a stable `code` (slug, unique within the
  activity), a `name`, an optional one-line `description`, a `price` and a `pricing` unit:
  - `PER_GROUP`: one flat amount for the whole group ("Private show, €250 per group");
  - `PER_PERSON`: price × travelers ("Photographer, €15 per person").
- **Line price** (every copy of the formula):
  `base = max(price × N, minPrice)` (unchanged); `extras = Σ amount(extra, N)`;
  `line = base + extras`.
- **Packages** (catalogue bundles with `discount_pct`): `group = discount(Σ base) + Σ extras` —
  extras are added at list price after the discount (A2).
- Extras are **opt-in**: nothing is pre-selected anywhere (EU CRD Art. 22, UK CMA 2025).
- Extras are **not** on the vote ballot; a closed vote that replaces the cart keeps the extras
  already chosen on the activities that survive.
- Snapshot rule (SEC-1, same as `minPrice`): the server resolves extras by `code` against the
  catalogue when a booking is created and stores name/price/unit on the booking item; the
  client's names and prices are display-only.

## Data model

### `Activity.extras` (backend)

```java
@Convert(converter = ActivityExtrasConverter.class)
@Column(columnDefinition = "TEXT")
private List<ActivityExtra> extras;          // null or empty = no extras
```

`ActivityExtra` is a plain record (`entity/ActivityExtra.java`):

```java
public record ActivityExtra(String code, String name, String description, BigDecimal price,
                            ExtraPricing pricing, Map<String, Map<String, String>> translations) {}
public enum ExtraPricing { PER_GROUP, PER_PERSON;  BigDecimal amount(BigDecimal price, int travelers) }
```

Stored as JSON text through a Jackson `AttributeConverter` (same mechanism as `translations`,
works on H2 and Postgres). `translations` on an extra is `{"de": {"name": …, "description": …}}`
and localises in place with `Translations.pick`, like the activity's own fields.

Limits: ≤ 10 extras per activity; `code` ≤ 40 chars `[a-z0-9-]`, generated with `SlugUtils`
from the name when blank; `name` ≤ 80; `description` ≤ 300; `price` ≥ 0, scale 2.

### `BookingItem.extras` (snapshot)

```java
@Convert(converter = BookingItemExtrasConverter.class)
@Column(columnDefinition = "TEXT")
private List<BookingItemExtra> extras;        // record (code, name, price, pricing)
```

### Flyway V14 (prod only)

```sql
ALTER TABLE activities    ADD COLUMN IF NOT EXISTS extras TEXT;
ALTER TABLE booking_items ADD COLUMN IF NOT EXISTS extras TEXT;
```

Dev/test schemas come from the entity mapping (`create-drop`).

## Backend

### DTOs and mapping (`ActivityService`)

- `ActivityDTO.extras: List<ActivityExtraDTO>` (`code, name, description, price, pricing`,
  plus `translations` only on the admin view, i.e. when no locale is requested).
- Write semantics mirror `translations`: DTO `extras == null` → unchanged, `[]` → cleared.
  Codes are normalised and deduplicated; a duplicate code in one request is a 400
  (`DUPLICATE_EXTRA_CODE`).
- `convertToDTO(activity, locale)` localises each extra's `name`/`description`.
- `VotePoolActivityDTO` and `ResultActivityDTO` carry `extras` (available extras) so cart items
  built from a vote result or the AI selection can offer them; `VotePoolActivityDTO` also gains a
  nullable `selectedExtras` (codes) that only the AI selection fills.
- `BookingItemDTO.extras` for the admin booking detail.

### Pricing (keep every copy in sync)

Shared arithmetic lives in `ExtraPricing.amount` and `MoneyMath.extrasTotal(extras, travelers)`.

| Copy | Change |
|---|---|
| `BookingService.lineTotal` | split into `lineBase` (floored) and `lineExtras`; `getGroupTotal = applyDiscountPct(Σ base) + Σ extras` |
| `PriceQuoteService` | `PriceQuoteRequest.Item.extraCodes` (≤ 10); the `Line` key includes the sorted codes; unknown codes are ignored in the quote |
| `PlanPricer` / `PlanAssembler` | `PlanPricer.extrasTotal`; `ItemResult.lineTotal` includes extras; package totals follow |
| `tripPricing.js` | `extrasTotal(item, travelers)`, `lineTotal` includes it, `computeTripTotal` applies the package discount to floored bases only |
| `VoteSessionService.flooredLine` | **unchanged** (ballots carry no extras) |

### Booking creation

- `TripExportRequest.ActivityExport.extras: List<ExtraExport(code, name, price, pricing)>`
  (≤ 10). `createBookingEntity` resolves each `code` against the activity's extras: under
  trusted pricing an unknown code is a 400 (`UNKNOWN_EXTRA`); the lenient lead path drops it.
  The resolved catalogue values are snapshotted on the `BookingItem`.
- `toExportRequest(Booking)` copies the snapshot back so emails sent after the Stripe webhook
  render the extras.
- `verifyChargeablePricing` recomputes from snapshots (extras included), as for `minPrice`.

### Emails

`EmailService.ActivityLineView` gains `extras` (name + formatted amount + unit label).
`itinerary-confirmation.html` and `booking-notification.html` list them under the line price:
"+ Private show — €250 per group". Keys `email.itinerary.activity.extra.perGroup` /
`.perPerson` in `messages.properties` and `messages_de.properties`.

### CSV import / export

- New **optional** column `extras` after `includes`: a JSON array in the cell, e.g.
  `[{"code":"private-show","name":"Private show","description":"…","price":250,"pricing":"PER_GROUP","translations":{"de":{"name":"Private Show"}}}]`.
  JSON is the only encoding that survives descriptions with `;`, `|` and commas.
- Column absent → field untouched; blank cell → extras cleared; invalid JSON or an invalid
  field → row error `INVALID_EXTRAS` (message names the problem); > 10 → `INVALID_EXTRAS`.
- Exporter emits canonical compact JSON (fixed field order, `translations` only when present)
  so an export → import round trip produces no diff; the differ compares canonical strings.
- `ImportActivitiesModal.fieldLabel.extras = 'Extras'`.

### AI planner

- `CatalogActivity.extras: List<CatalogExtra(code, name, price, pricing)>` (localised names),
  filled by `CatalogSnapshotter`; `PackageEditor.fromPlanItem` carries them.
- `PlanDraft.ItemDraft.extraCodes` (default empty) and `ComposedPlan.ItemResult.extras:
  List<ItemExtra(code, name, price, pricing, total)>`; `lineTotal` includes extras.
  Back-compatible constructors keep the 28 test call sites compiling; `PlanDrafts.fromComposed`,
  `PlanTrimmer`, `FallbackPlanComposer`, `PlanTextWriter.withWhy` copy the field.
- Chat edits: `EditOp.ADD_EXTRA` / `EditOp.REMOVE_EXTRA`; `EditRequest.extra` (name).
  `LlmOutputParser` reads `extra`; an `ADD_EXTRA`/`REMOVE_EXTRA` without `extra` is dropped.
- Prompt (`PromptRenderer.PACKAGES_BLOCK`): the packages view prints selected extras after the
  item (`River Boat Cruise (+ Private show)`); a new line lists the available extras per
  catalogue activity (`River Boat Cruise → Private show (EUR 250 per group), Open bar (EUR 25
  per person)`); rules: "add a private show to the boat", "with a stripper on the cruise" →
  `{"op":"ADD_EXTRA","activity":"River Boat Cruise","extra":"Private show"}`; "river cruise with
  a private show" when the cruise is not in the package → `ADD` + `ADD_EXTRA` (two edits);
  "no photographer" → `REMOVE_EXTRA`.
- `ChatTurnNode`: the split-wish guard keeps an `ADD` when the same turn carries an `ADD_EXTRA`
  for the same activity; `ADD_EXTRA`/`REMOVE_EXTRA` are always carried out (they name a
  concrete activity).
- `PackageEditor.applyAddExtra` / `applyRemoveExtra`: targets = packages that contain the
  activity (`packageKey` null → all of them; none → `NOT_IN_PACKAGE`); the extra is resolved by
  name within that activity's extras (exact, then contains; miss → `UNKNOWN_EXTRA` with the
  activity's extra names as `alternatives`); `ALREADY_HAS_EXTRA`, `EXTRA_NOT_SELECTED`. No
  scheduling change (extras add no minutes); the assembler re-prices.
- `EditMessages` (EN/DE): "Added Private show to River Boat Cruise (+ €250 for the group)",
  "Removed Private show from River Boat Cruise", "River Boat Cruise has no 'Private show' —
  it offers: Open bar, Photographer." The summary already states the package total, which now
  includes the extra (research: read back unit price and new total; undo stays the existing
  quiet chip).
- API (`docs/api/ai-planner-api.md`, additive): `ItemDTO.extras`, `AppliedEditDTO.extra` /
  `RejectedEditDTO.extra`, new rejection reasons, the two ops; `select` returns `tripItems` with
  `extras` and `selectedExtras`.
- `AiPackageView` shows selected extras as small chips under the item name; the staff bench
  (`AdminAiPlanner`) shows them with amounts.

## Frontend (CRA)

### Cart (`TripContext`)

- Item fields: `extras` (available, copied from the activity when it enters the cart) and
  `selectedExtras` (codes, default `[]`). Legacy carts without the fields behave as before.
- New action `SET_ITEM_EXTRAS {id, selectedExtras}`.
- `SET_TRIP_ITEMS_FROM_VOTE` and `SET_TRIP_ITEMS` (AI pick) merge `extras`/`selectedExtras`
  from the previous entry with the same `id` unless the incoming item sets them.
- Locale change re-localises extra names along with name/description/includes.
- `usePlanFromPrice` key includes `extraCodes`; `buildBookingData` sends
  `extras: [{code, name, price, pricing}]` per activity.

### Surfaces

| Surface | Change |
|---|---|
| `ActivityCard` | one chip row when the activity has extras: "Optional extras · Private show, Photographer" (first two names, "+N" after); no price (card stays price-free) |
| `ActivityDetailPage` | "Optional extras" checklist above the add panel: name, one-line description, "+€250 per group" / "+€15 per person", unticked; the add button carries `selectedExtras`; the price panel shows a "+ extras" line when any is ticked |
| `ActivityPreviewModal` | read-only "Optional extras" list under "What's included" (keeps the user in the flow; toggling happens in the Trip Builder) |
| `TripBuilder` standalone row | under the name: chips of selected extras with ×, and an "Add extras" link that expands an inline checklist in the row; the plan "from €X" refreshes through the quote |
| `TripBuilderDropdown` | "+ €250 extras" sub-line when an item has selected extras |
| `AdminActivities` | an extras editor in the form (rows: name, price, unit, description; code generated server-side) and an "Extras" count column |
| `AdminBookingDetail` | extras listed under each item |

Copy keys are added to `en.json`/`de.json` (`extras.*`, `tripBuilder.extras.*`,
`activityDetail.extras.*`, `admin.activities.extras.*`).

## Error handling

- Catalogue writes: invalid extra → 400 with the field name; duplicates → 400.
- Booking: unknown code under trusted pricing → 400 `UNKNOWN_EXTRA` (the cart is stale: the
  admin removed the extra); the Trip Builder shows the existing error toast.
- Quote: unknown codes ignored (a stale cart still gets a price).
- AI edits: every rejection is a 200 with a reason, as today; an exception in the new editor
  paths is caught by the existing per-op rollback (`INTERNAL`).

## Testing

Backend (JUnit 5, H2): converter round trip; `ActivityService` mapping + localisation + null/
[]-semantics + duplicate codes; `BookingService.calculateTotal` (per-group, per-person, with
floor, inside a package → not discounted), snapshot on create, `UNKNOWN_EXTRA`,
`toExportRequest` round trip, `verifyChargeablePricing`; `PriceQuoteService` with codes;
`EmailService` line view + template render; CSV parser (optional column), validator (valid,
invalid JSON, > 10, bad unit), differ (no phantom diff), exporter header/canonical JSON, importer
apply (set / clear / untouched); AI: snapshotter, assembler pricing, parser (`extra` field,
dropped without it), prompt renderer (extras lines), `PackageEditor` (add/remove × every
rejection), `ChatTurnNode` guard, `EditMessages`, `AiDtoMapper`, controller integration (chat
"add a private show to the boat" → `edit.applied[0].op == ADD_EXTRA`, package total raised).

Frontend (Jest + RTL in `myhive-react-app`): `tripPricing` (same cases as backend),
`TripContext` reducer (set extras, vote merge, restore defaults), `TripBuilder` (chips,
inline checklist, booking payload, quote key), `ActivityCard` (chip present/absent, still no
price), `ActivityDetailPage` (checklist, add carries selection), `AdminActivities` form,
`AiPackageView` chips.

## Rollout

1. Backend deploy (V14 adds the columns; old frontends ignore `extras`).
2. Frontend deploy.
3. CSV import with the `extras` column (operations phase, owner-triggered).

## Out of scope (v1)

- Extras on catalogue-package lines in the UI (the backend accepts them; `ADD_PACKAGE_TO_TRIP`
  items carry no `extras` list, so no toggle is offered).
- Planner-model-chosen extras at generation time (A7), tap-to-toggle extras in `AiPackageView`
  (chat only), extras on the vote ballot, extras in trip-lead reminder emails, per-extra
  headcount caps or hourly pricing, the German catalogue corpus pipeline for extras (DE names
  travel inside the CSV JSON).
