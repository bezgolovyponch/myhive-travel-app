# Planner UX kit

Part of the pre-merge audit: see `qa/PRE-MERGE-AUDIT.md` for when to run it.

A repeatable test of the two ways an organizer builds a trip: browsing the catalogue into the cart, and
the AI chat. It drives a real browser at phone size (390x844) against a running site and checks every
way an activity or an extra can be added, changed or removed, plus the chat's error messages.

## Run it

```bash
cd qa/planner-ux-kit && npm install      # once
node kit.mjs                             # against http://localhost:3000
KIT_BASE=http://192.168.0.216:3000 node kit.mjs
KIT_ONLY=browse node kit.mjs             # browse | ai | errors
KIT_TYPED=0 node kit.mjs                 # skip the ten typed messages (no model calls beyond the first plan)
```

It needs the backend and the frontend running, and a Chromium: the Playwright cache is used when there is
one, else installed Chrome; `KIT_CHROMIUM=<binary>` overrides. Results are printed and written to
`results/<time>.json`. Exit code 1 when anything is `FAIL` or `ERROR`.

Before a run after a catalogue change, clear the frontend's page cache
(`rm -rf myhive-next/.next/cache/fetch-cache` and restart it): pages are cached for an hour, see finding 3.

Do not point the AI part at production without a reason: it starts a real chat and makes about 15 model calls.

## What is deterministic and what is not

| Part | Checks | Model calls | Same result every run |
|---|---|---|---|
| Browse flow | B01-B33 | none | yes: the expected numbers are computed from the catalogue API |
| Error messages | E01-E05 | none: the answers are simulated in the browser | yes |
| AI flow, taps and cards | A01-A24 | one, to get a plan; every add, extra and removal after it is a tap | yes for the outcome; the plan's contents differ per run, so the kit picks its activities from what the plan does not hold |
| AI flow, typed | T01-T10 | one each | the outcome is asserted, never the wording; a failure here can be the model |

Statuses: `PASS`; `FAIL` (the product broke a rule it has); `FLAW` (works as built, falls short of the
standard named in the check); `INFO` (a measurement, or a typed scenario that passed, with what happened).

The typed messages are in `scenarios.json`. No scenario list had been stored before this kit; that file was
assembled from the staff test prompts, the examples the chat's own prompt gives, and the chat bugs reported on
2026-10-07..09. Add a scenario by adding an entry: the fields are the outcome to expect.

## The standards the checks are held to

| Standard | Used for |
|---|---|
| WCAG 2.2 SC 2.5.8 (targets 24x24, AA) and 2.5.5 (44x44, AAA) | checkbox, buttons, chat tags |
| WCAG 2.2 SC 1.4.3 (contrast 4.5:1), 1.4.10 (reflow) | small text in the card, sideways overflow |
| WAI-ARIA dialog (modal) pattern | `aria-modal`, focus into the card, Escape, focus back |
| Nielsen response-time limits (1 s, 10 s) and heuristics 1 and 9 | first feedback, time to the plan, status, error wording |
| Baymard: hard-to-scan list items; dismissible, relevant cross-sell | the extras hint on cards, the extras question in the chat |
| EU CRD Art. 22 and CMA 2025 guidance: no pre-ticked extras; CMA209 price transparency | checklist defaults, prices with units, cart vs plan price |
| Owner rules of 2026-10-12 | tick then confirm; one card everywhere; no extra price in the chat, which suggests only three kinds of extra; one "from" price for the plan, no price per activity, no total, no discount notice; a plain add just adds |
| Pricing rule of the code | plan "from" = sum of max(price x N, group minimum) x 0.90 + extras in full, whole euros |

Baymard and NN/g are cited from their public articles and from `docs/research/reports/Prague stag activity
listings UX.md`; their paid reports were not read.

## Verified run: 2026-10-12, local, `fix/open-items`

78 checks: **63 pass, 0 fail, 0 flaws, 15 informational** (`results/2026-10-12T15-31-50.json`).

The checks follow the owner's decisions of 2026-10-12:

- one "from" price for the plan; no price per activity, no exact total; a plain "Add to trip" just adds;
- the 10% margin stays in the background: nothing on any screen announces it;
- extras are never discounted: "from" = activities x 0.90 + extras in full; an extra's own price is on the card only;
- the chat suggests only a private show, a topless hostess or more bullets, and never a price;
- tick, then confirm, on one card that is the same everywhere.

Fixed since the first run of this kit, each now a passing check: undo lost on reload (A22); "X with an extra"
for an activity not in the plan (T05); the extras hint wrapping to five lines (B02); 3.85:1 small text on the
card (B06); no "Try again" on a refused first message (E04); cart and Trip Builder stating different prices (B21).

### Measurements

- First sign of work after sending: under 1 s. Plan on screen: 10 to 13 s, with a status shown meanwhile
  (see `docs/research/planner-speed/`).
- A typed message is answered in 4 to 7 s.
- Targets: extras checkbox 20 px inside a row of 118 px (the row is the target); "Edit extras" 85x36; chat tags
  37 px high. All pass AA (24 px); none reaches 44 px.
- With the chat open the plan rows are behind it; a card can only be opened from a row with the chat down.
- Taps never refuse an activity for lack of room: a day reached 5 activities and the page said nothing.

### Not covered

A real phone and iOS Safari (the browser is desktop Chromium at phone size), screen readers, German, desktop
widths, the group vote, booking and payment, and production data. Run it against production only for the
browse part unless the model calls are wanted.
