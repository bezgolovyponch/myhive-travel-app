-- Popularity index for the Prague catalog, kept in activities.featured_weight (0-100, higher first).
-- It is what the catalog, the planner's recommendations and the vote pool sort by. The numbers come
-- from how six stag-trip sellers rank each kind of activity, plus 10 for an activity that is part of a
-- ready-made package; the method and the sources are in docs/activity-popularity-index.md.
-- By slug, so it is safe on a database that lacks some of these activities.
UPDATE activities SET featured_weight = 100 WHERE slug IN ('nightlife-tour');
UPDATE activities SET featured_weight = 99 WHERE slug IN ('ak-47-glock-17-shooting');
UPDATE activities SET featured_weight = 84 WHERE slug IN ('kalashnikov-shooting', 'special-forces-shooting');
UPDATE activities SET featured_weight = 72 WHERE slug IN ('booze-cruise-party-boat');
UPDATE activities SET featured_weight = 67 WHERE slug IN ('river-boat-cruise');
UPDATE activities SET featured_weight = 66 WHERE slug IN ('go-karting-experience');
UPDATE activities SET featured_weight = 60 WHERE slug IN ('army-tank-experience', 'steak-and-private-show');
UPDATE activities SET featured_weight = 51 WHERE slug IN ('indoor-go-karting');
UPDATE activities SET featured_weight = 49 WHERE slug IN ('paintball');
UPDATE activities SET featured_weight = 45 WHERE slug IN ('steak-tits');
UPDATE activities SET featured_weight = 44 WHERE slug IN ('stag-arrest');
UPDATE activities SET featured_weight = 43 WHERE slug IN ('beer-spa');
UPDATE activities SET featured_weight = 38 WHERE slug IN ('beer-tasting-experience', 'guided-cabaret-club-tour', 'swimming-beer-bike');
UPDATE activities SET featured_weight = 34 WHERE slug IN ('paintball-assault', 'unlimited-paintball-experience');
UPDATE activities SET featured_weight = 33 WHERE slug IN ('brewery-beer-tasting', 'gentlemens-club-night-bar-tab', 'rolly-polly-stripper-prank');
UPDATE activities SET featured_weight = 28 WHERE slug IN ('medieval-dinner', 'night-club-entry', 'one-way-airport-transfer-prague', 'strip-hummer');
UPDATE activities SET featured_weight = 27 WHERE slug IN ('quad-bikes');
UPDATE activities SET featured_weight = 23 WHERE slug IN ('all-you-can-drink-strip-club', 'soho-garden-stag-night', 'tottie-strip-tour', 'vip-cabaret-night-package', 'vip-club-entrance');
UPDATE activities SET featured_weight = 22 WHERE slug IN ('bubble-football', 'white-water-rafting');
UPDATE activities SET featured_weight = 21 WHERE slug IN ('czech-lunch-or-dinner');
UPDATE activities SET featured_weight = 17 WHERE slug IN ('bubble-football-2', 'escape-room', 'jelly-wrestling', 'rafting-extreme', 'sunset-boat-party');
UPDATE activities SET featured_weight = 16 WHERE slug IN ('sight-beering');
UPDATE activities SET featured_weight = 12 WHERE slug IN ('babes-in-oil', 'buggy-and-quad-bikes-tour', 'buggy-safari', 'quad-safari-tour', 'tiki-boat');
UPDATE activities SET featured_weight = 11 WHERE slug IN ('beer-olympics', 'bowling-and-beers', 'burger-and-private-show', 'football-match', 'footgolf', 'hummer-limo', 'playboy-party-spa', 'private-boat-lunch-strip', 'stag-challenge');
UPDATE activities SET featured_weight = 10 WHERE slug IN ('cocktails-karaoke-night', 'gin-tasting', 'grilled-suckling-pig', 'succulent-bbq-ribs', 'whiskey-tasting');
UPDATE activities SET featured_weight = 6 WHERE slug IN ('casino-night', 'czech-dinner-with-national-show', 'party-limo-ride', 'sightbeering-tour', 'strip-bus', 'vip-strip-limo');
UPDATE activities SET featured_weight = 1 WHERE slug IN ('airsoft-combat', 'axe-throwing', 'axe-throwing-2', 'bowling-beers', 'car-football', 'casino-vip', 'extreme-archery', 'foot-darts', 'full-irish-breakfast', 'laser-tag', 'laser-tag-2', 'luxury-spa-session', 'meet-greet-in-prague', 'stand-up-paddle', 'thermal-spa', 'wellness-spa', 'wine-tasting');
