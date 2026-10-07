# Activity popularity index

One number per activity, 0 to 100, stored in `activities.featured_weight`. Higher sorts first. It
orders the activities catalog, the planner's catalog and recommendations, the vote pool and the
homepage's featured row. An activity nobody has scored is 0 and sorts after the scored ones, by name.

## How a number is made

1. Activities are grouped into kinds (shooting, bar crawl, karting ...).
2. Each of six sellers gives a kind 0 to 3 points: 3 for a best seller or a top-ranked activity,
   2 for one marked popular or recommended, 1 for one that is only on offer, 0 for one that is absent.
3. A kind's score is its points as a share of the highest-scoring kind, times 100.
4. The first activity of a kind takes the score; its variants take 5 less.
5. An activity that is part of a ready-made package (a preset) gets 10 more, capped at 100.

The index is a snapshot from October 2026. It does not update itself: changing a number means a new
migration, the admin CSV import (`featured_weight` column), or re-running the scoring.

## Sources

| # | Source | What was read |
|---|---|---|
| 1 | Last Night of Freedom, Prague stag activities | "Best seller" and "Recommended" badges, review counts |
| 2 | Chillisauce, Prague stag do | The "Top 10" list and "Popular" labels |
| 3 | Maximise, Prague stag do | Page order and labels ("Best seller") |
| 4 | Pissup Reisen (DE), JGA Prag | "Beliebteste Aktivitäten", "Favoriten unseres Teams" |
| 5 | StagWeb, Prague stag do | "Top stag activities" ranking and "most booked" |
| 6 | Hello Prague stag guide | Its ranking of what stag groups book |

## Scores by kind

Columns 1 to 6 are the sources above. The last column is the stored number per activity (with the
preset bonus where it applies).

| Kind | 1 | 2 | 3 | 4 | 5 | 6 | Score | Activities |
|---|---|---|---|---|---|---|---|---|
| bar-crawl | 3 | 3 | 3 | 3 | 3 | 3 | 100 | `nightlife-tour` 100 |
| shooting | 3 | 1 | 3 | 3 | 3 | 3 | 89 | `ak-47-glock-17-shooting` 99, `kalashnikov-shooting` 84, `special-forces-shooting` 84 |
| river-cruise | 3 | 3 | 2 | 2 | 1 | 2 | 72 | `booze-cruise-party-boat` 72, `river-boat-cruise` 67 |
| karting | 3 | 3 | 2 | 0 | 2 | 0 | 56 | `go-karting-experience` 66, `indoor-go-karting` 51 |
| steak-strip | 3 | 0 | 0 | 3 | 3 | 0 | 50 | `steak-and-private-show` 60, `steak-tits` 45 |
| tank | 2 | 0 | 2 | 2 | 0 | 3 | 50 | `army-tank-experience` 60 |
| stag-arrest | 3 | 3 | 2 | 0 | 0 | 0 | 44 | `stag-arrest` 44 |
| paintball | 1 | 1 | 0 | 3 | 2 | 0 | 39 | `paintball` 49, `paintball-assault` 34, `unlimited-paintball-experience` 34 |
| beer-spa | 0 | 1 | 0 | 0 | 3 | 2 | 33 | `beer-spa` 43 |
| brewery | 1 | 2 | 0 | 2 | 0 | 1 | 33 | `brewery-beer-tasting` 33, `beer-tasting-experience` 38 |
| xxl-stripper | 3 | 1 | 0 | 2 | 0 | 0 | 33 | `rolly-polly-stripper-prank` 33 |
| beer-bike | 1 | 1 | 0 | 0 | 3 | 0 | 28 | `swimming-beer-bike` 38 |
| strip-club | 1 | 1 | 1 | 2 | 0 | 0 | 28 | `guided-cabaret-club-tour` 38, `tottie-strip-tour` 23, `gentlemens-club-night-bar-tab` 33, `all-you-can-drink-strip-club` 23, `vip-cabaret-night-package` 23 |
| nightclub | 0 | 3 | 2 | 0 | 0 | 0 | 28 | `night-club-entry` 28, `vip-club-entrance` 23, `soho-garden-stag-night` 23 |
| medieval | 0 | 3 | 2 | 0 | 0 | 0 | 28 | `medieval-dinner` 28 |
| strip-transfer | 1 | 3 | 1 | 0 | 0 | 0 | 28 | `strip-hummer` 28 |
| transfer | 2 | 3 | 0 | 0 | 0 | 0 | 28 | `one-way-airport-transfer-prague` 28 |
| rafting | 0 | 3 | 0 | 0 | 1 | 0 | 22 | `white-water-rafting` 22, `rafting-extreme` 17 |
| bubble-football | 0 | 1 | 1 | 0 | 2 | 0 | 22 | `bubble-football` 22, `bubble-football-2` 17 |
| escape-room | 0 | 1 | 0 | 0 | 1 | 1 | 17 | `escape-room` 17 |
| boat-party | 0 | 1 | 2 | 0 | 0 | 0 | 17 | `sunset-boat-party` 17, `tiki-boat` 12 |
| wrestling | 2 | 0 | 1 | 0 | 0 | 0 | 17 | `jelly-wrestling` 17, `babes-in-oil` 12 |
| quad | 0 | 0 | 2 | 0 | 0 | 1 | 17 | `quad-bikes` 27, `quad-safari-tour` 12, `buggy-and-quad-bikes-tour` 12, `buggy-safari` 12 |
| limo | 0 | 1 | 1 | 0 | 0 | 0 | 11 | `hummer-limo` 11, `party-limo-ride` 6, `vip-strip-limo` 6, `strip-bus` 6 |
| strip-boat | 1 | 0 | 1 | 0 | 0 | 0 | 11 | `private-boat-lunch-strip` 11 |
| self-tap-pub | 0 | 0 | 0 | 2 | 0 | 0 | 11 | `stag-challenge` 11 |
| footgolf | 0 | 2 | 0 | 0 | 0 | 0 | 11 | `footgolf` 11 |
| czech-dinner | 0 | 2 | 0 | 0 | 0 | 0 | 11 | `czech-lunch-or-dinner` 21, `czech-dinner-with-national-show` 6 |
| beer-games | 0 | 1 | 0 | 1 | 0 | 0 | 11 | `beer-olympics` 11, `sight-beering` 16, `sightbeering-tour` 6 |
| five-a-side | 0 | 1 | 0 | 0 | 1 | 0 | 11 | `football-match` 11 |
| sexy-spa | 0 | 1 | 1 | 0 | 0 | 0 | 11 | `playboy-party-spa` 11 |
| burger-strip | 0 | 1 | 1 | 0 | 0 | 0 | 11 | `burger-and-private-show` 11 |
| listed | 0 | 1 | 0 | 0 | 0 | 0 | 6 | `casino-night` 6, `casino-vip` 1, `laser-tag` 1, `laser-tag-2` 1, `axe-throwing` 1, `axe-throwing-2` 1, `airsoft-combat` 1, `bowling-beers` 1, `bowling-and-beers` 11, `car-football` 1, `wine-tasting` 1, `stand-up-paddle` 1, `foot-darts` 1, `full-irish-breakfast` 1, `extreme-archery` 1, `thermal-spa` 1, `wellness-spa` 1, `luxury-spa-session` 1, `meet-greet-in-prague` 1 |

Preset activities that no seller ranks (karaoke night, tastings, the grill dinners) carry the preset
bonus alone: 10.
