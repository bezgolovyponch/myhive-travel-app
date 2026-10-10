# Prague catalogue rewrite, 2026-10 — notes

Files: `prague-catalogue-2026-10.csv` (82 rows: 72 existing rewritten + 10 new), `prague-images-manifest.json` (10 images).
Source of truth for existing rows: prod DB snapshot taken read-only on 2026-10-10. Nothing has been imported.

## What changed and why

- **Untouched on every existing row:** `id`, `slug`, `destination_slug`, `name`, `price`, `min_price`, `duration`, `image_url`, `featured_weight`. A script compared all of these with the prod snapshot and found no differences.
- **Categories:** one slug added where a show or prank was clearly missing from the filter. `stag-hot-babies-and-pranks` was added to `breakfast-with-tiffany`, `czech-lunch-or-dinner` and `slivovice-tour-and-tasting` (each includes a show) and to `dog-chase` and `dwarf-hire` (both pranks, as the report classifies them).
- **Descriptions (report part b):** each is a one-line hook, then one or two short paragraphs separated by a blank line. They run 259–516 characters (the old ones were about 700–800). No line ends with `:`, because `parseDescriptionBlocks` would render it as a heading. Hype words are gone (epic, legendary, unforgettable), and so are vague closers like "ask us for details". Facts come only from the old copy and includes; no venues or meeting points were added. Where the report gives a "what to bring" fact, it is stated: a passport and coming sober for shooting and the casino, swimwear for the spas, old clothes for quads.
- **Adult register:** the copy is honest and neutral. "Strip Show" became "15-minute private show by a professional performer", and "Lesbian show" became "duo show by two professional performers". Each adult row carries the same facts: 15-minute show, private room, English-speaking Trivlu guide, the stag as guest of honour, 18+, and house rules where a club is involved.
- **Includes:** `;`-separated, 5–9 bullets on 80 of 82 rows, with drink counts and portions inside the bullets and a `Duration:` bullet last. Long ingredient lists were merged (for example, the full English became one bullet). `dwarf-hire` and `unlimited-paintball-experience` have 4 bullets because the source has no more facts to add. Some rows have no Duration bullet because the catalogue value contradicts their own content (see open questions).
- **Importer compatibility:** the `extras` column is optional in the spec. Until the extras feature is merged, today's `ActivityCsvParser` reports `extras` as an `UNKNOWN_COLUMNS` warning and imports everything else.

## Extras catalogue (26 carriers, 69 extras, all with German `translations`)

Rules for every extra: opt-in only, at most 4 per activity, and the `code` is set explicitly. A show is never offered on a row that already includes one. Every Private show variant uses the same description: "15-minute show by a professional performer…, English-speaking host present. 18+ only; tips not included".

| Extra | Price | Unit | Price basis |
|---|---|---|---|
| Private show (on board / in the limo / after dinner) | 160 | PER_GROUP | Standalone `private-hotel-show` is €20 pp with a €120 minimum, so €160 at the typical 8-person group. Report band: PSF 159 €/group. |
| Photographer | 100 | PER_GROUP | No market price was found; owner to confirm with a supplier. |
| Welcome shots (2 each) | 6 | PER_PERSON | Local shot prices. |
| Unlimited beer and soft drinks (river cruise only) | 20 | PER_PERSON | Gap to the boats that include beer (Tiki €65 vs cruise €37). |
| Open bar upgrade, spirits added (Tiki, swimming bike) | 15 | PER_PERSON | Report: Funktion cruise + drinks delta. |
| Unlimited beer, 1 h (dinners) | 25 | PER_PERSON | Report: PSF "unlimited beer 29 € p.p.". |
| Celebration cake for the stag | 45 | PER_GROUP | Assumed. |
| Bottle of champagne (limos, transfer) | 90 | PER_GROUP | Assumed. |
| Extra hour: Hummer 350 / party limo 280 / transfer 60 | as listed | PER_GROUP | Below the first-hour group minimum (€470 / €350); the transfer has no minimum. |
| Extra bottle for the table | 120 | PER_GROUP | Assumed. |
| Extra bar tab (+€20 each) | 20 | PER_PERSON | Face value. |
| VIP table and bottle at a club afterwards | 150 | PER_GROUP | Report: PSF tables 54–179 €. |
| Extra round of shots (4 each) | 8 | PER_PERSON | Assumed. |
| Extra hour in the game room | 60 | PER_GROUP | Assumed. |
| Extra 10-min race 20 · Extra 100 paintballs 10 · Extra 10 AK-47 rounds 20 · Extra 12 clays 35 · Extra 30 min bubbles 12 | as listed | PER_PERSON | Pro rata of the base product; shooting extras checked against Prague Ranger pay-on-site prices. |
| Transfer from your hotel | 90 | PER_GROUP | Only where transport is not already included. |
| 30-minute massage 45 · Extra hour in the spa 15 | as listed | PER_PERSON | Report: GYG beer-spa reviews ask for the massage price. |

Carriers:
- **Boats:** `river-boat-cruise`, `tiki-boat` and `swimming-beer-bike` get show, drinks upgrade, photographer and welcome shots.
- **Limos and transfer:** `hummer-limo` gets show, champagne and extra hour. `party-limo-ride` gets champagne and extra hour (it already includes a show). `one-way-airport-transfer-prague` gets champagne and extra hour.
- **Dinners:** `czech-dinner-with-national-show`, `grilled-suckling-pig`, `live-music-irish-dinner`, `middle-east-dinner` and `succulent-bbq-ribs` get show, unlimited beer and cake. `medieval-dinner` gets show and cake (beer is already unlimited). `czech-lunch-or-dinner` gets unlimited beer and cake (show already included).
- **Nightlife:**
  - `vip-club-entrance`: extra bottle, bar tab and photographer.
  - `gentlemens-club-night-bar-tab`: extra bottle and bar tab (no photographer in an adult venue).
  - `casino-night`: VIP table afterwards and bar tab.
  - `cocktails-karaoke-night`: bar tab, VIP table and photographer.
  - `party-starter-on-terrace`: shots, VIP table and photographer.
  - `private-game-room-session`: bar tab and extra hour.
- **Daytime:**
  - `go-karting-experience`, `paintball` and `clay-pigeon-shooting`: more rounds and photographer (transport is already included).
  - `ak-47-glock-17-shooting` and `bubble-football`: more rounds, transfer and photographer.
  - `beer-spa`: massage and transfer.
  - `wellness-spa`: massage and extra hour.
- **No extras:** products that are themselves add-ons, such as private-hotel-show, stag-arrest, maid-service-prank-show, dwarf-hire and the other show or prank rows.

## New rows (blank `id`)

| slug | €/person | min_price | min | categories | Report band |
|---|---|---|---|---|---|
| guided-bar-crawl | 35 | – | 240 | czech-beer;nightlife | bar crawl €18–45, 4–6 h, ~19:00, welcome shots |
| classic-beer-bike | 39 | 390 | 60 | czech-beer;food-and-drink | beer bike 30–45 € pp; PSF 571 €/group |
| escape-room-challenge | 30 | 150 | 60 | extreme | escape rooms 41–85 €; Funktion per room of 5 |
| medieval-sword-fighting | 45 | – | 90 | extreme | escape/axe/bobsled band 41–85 € |
| segway-tour | 45 | – | 90 | wellness | same band |
| brewery-tour | 45 | – | 120 | czech-beer;food-and-drink | brewery 36–69 € |
| ice-bar | 25 | – | 45 | nightlife | no band; Chillisauce ice bar + club is high-demand |
| rage-room | 45 | – | 60 | extreme | band 41–85 € |
| summer-bobsled | 49 | – | 180 | extreme | band 41–85 € (incl. transport) |
| football-match-tickets | 49 | – | 180 | czech-beer;wellness | no band in the report |

`min_price` is set only where the product is sold per group: the beer bike is 10 riders × €39, and an escape room is 5 players × €30. Every new row has `featured_weight` 0 and no extras.

## Image conventions (10 new images, all Pexels, free licence)

- **Sources:** every candidate came from a Pexels or Unsplash search page. Each chosen photo's page was confirmed with WebFetch (title, photographer, "Free" licence, not sponsored). Each image was then downloaded at the final crop and checked by eye.
- **Format:** the URL pattern serves 1200×900 (4:3) crops. There is no text overlay or watermark.
- **Style:** activity in progress where stock allows (Segway, bobsled, sword fight, beer bike, toast at the bar). Props or venue only otherwise (keys for the escape room, sledgehammer and debris for the rage room, ice hall, brewery vats). Daylight for sport, warm or night palettes for the bar crawl, stadium and ice bar.
- **Rejected:**
  - Pedal-pub photos with an operator name and URL across the canopy.
  - An ice-bar shelf of branded bottles.
  - A Segway frame with a Segway logo, and one with a Rome street sign.
  - Unsplash+ items.
- **Re-hosting:** stock URLs are re-hosted to R2 on import by `RemoteImageFetcher`, so nothing is hotlinked. No adult rows got new images: existing `image_url`s are unchanged by design.

## Check (run from the worktree)

```
node <scratchpad>/check.js docs/research/prague-catalogue-2026-10.csv
BOM: true; header exact: true; rows: 82 (existing 72, new 10); columns per row: 13
rows with extras: 26; extras total: 69; description length min/max: 259/516
OK: no problems
```

The script is a hand-written RFC 4180 parser (quoted fields, `""` escapes, CRLF). For every row it checks:
- column count, the exact header and the BOM;
- UUID and slug syntax, duplicate slugs, `destination_slug = prague`;
- numbers (`^\d+(\.\d{1,2})?$`), that category slugs exist in prod, the image URL scheme;
- description ≤ 800 characters, hype words, lines ending in `:`, 4–9 include bullets;
- `extras`: JSON parses, ≤ 10 items, code `[a-z0-9-]{1,40}` unique per row, name ≤ 80, description ≤ 300, price ≥ 0 with ≤ 2 decimals, pricing enum, DE name and description present.

A second script confirmed the unchanged fields against the prod snapshot: `invariant diffs: 0`.

## Open questions for the owner

1. **Show on the river cruise.** `river-boat-cruise` reads like a shared sightseeing boat (audio guide, indoor or deck seating). A private show only works on a private charter. Confirm, or drop `private-show-on-board` from that row.
2. **Venues for "Private show after dinner".** Confirm the medieval tavern, the brewery restaurant (suckling pig), the Irish dinner and the Middle Eastern restaurant can host a private show. Otherwise remove the extra on those rows.
3. **Assumed prices.** These need a supplier quote: photographer, cake, champagne, extra bottle, extra hours, massage, transfer. The transfer at €90 per group assumes one minibus, so larger groups need two.
4. **Segway tour.** Prague has restricted Segways in the historic centre. The copy says the route runs outside those zones; confirm the supplier's route and check the current rules before listing.
5. **New rows need supplier confirmation** of their inclusions: ice bar drinks and capes, bobsled runs and transport, the football ticket section, the escape-room team size and the beer-bike keg. The football match also has no sport category: `czech-beer;wellness` is a stopgap.
6. **Duration conflicts in the catalogue.** These rows omit a Duration bullet:
   - `wellness-spa`: 120 min vs a 3-hour pass;
   - `pitbikes-racing`: 60 min vs 30 + 30 min plus 4 rides;
   - `unlimited-paintball-experience`: 180 min vs 1 h of play;
   - `dwarf-hire`: 90 min vs 1 h.
   `medieval-dinner` listed "180 min show" inside a 120-minute dinner; the copy now just says "medieval show".
7. **Overlaps.** `guided-bar-crawl` sits close to `sight-beering` and `stag-challenge-2`. `escape-room-challenge` sits close to `dungeon-of-horror-experience`, and `rage-room` close to `car-demolition-in-prague`. Keep them, or merge?
8. **Beer-bike image.** Every free beer-bike photo found carries an operator's branding on the canopy, and the chosen one (Pexels 1102967) does too. A supplier photo is preferred before launch.
9. **German text.** These English rewrites make the existing German translations of these activities stale. The DE corpus (`content/translations-de/*.json`) needs a pass before the next SQL generation.
