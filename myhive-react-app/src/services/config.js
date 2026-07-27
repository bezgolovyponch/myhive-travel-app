export const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
export const SITE_URL = process.env.REACT_APP_SITE_URL || 'https://trivlu.com';

// Placeholder FB page until the real one is provided
export const WHATSAPP_URL = 'https://wa.me/420795518597';
export const MESSENGER_URL = 'https://m.me/trivlu';

// Prague is the only destination on sale, so destination choice is hidden and the
// default destination is used. Flip to true when new destinations open.
export const DESTINATION_PICKER_ENABLED = false;

// Sticky mobile "Start Group Vote" bar on the homepage. Ships dark — visual
// risk; flip to true to trial it.
export const STICKY_VOTE_CTA_ENABLED = false;

// Kill-switch for customer-facing online payment — gates the deposit CTAs
// (Trip Builder success screen + vote result page) and the success-screen
// Turnstile widget. Backend /payments endpoints stay live either way.
export const PAYMENTS_ENABLED = true;

// Hard fallback when the host carries no destination subdomain (apex, www,
// localhost, *.onrender.com).
export const FALLBACK_DESTINATION_SLUG = 'prague';

// Hosts that are NOT a destination subdomain — their first label must not be
// treated as a slug.
const NON_DESTINATION_HOSTS = new Set(['trivlu', 'www', 'localhost', '127']);

// Derives the destination slug from the first hostname label so that
// prague.trivlu.com serves Prague, barcelona.trivlu.com serves Barcelona, etc.
// Apex/www/localhost/onrender hosts fall back to FALLBACK_DESTINATION_SLUG.
export function resolveDestinationSlugFromHost(hostname) {
    const host = (hostname || '').toLowerCase();
    if (host.endsWith('.onrender.com')) {
        return FALLBACK_DESTINATION_SLUG;
    }
    const first = host.split('.')[0];
    if (!first || NON_DESTINATION_HOSTS.has(first)) {
        return FALLBACK_DESTINATION_SLUG;
    }
    return first;
}

// Used wherever the UI needs a destination without asking the user; falls back to
// the first destination from the API if this slug is missing.
export const DEFAULT_DESTINATION_SLUG =
    typeof window !== 'undefined' && window.location
        ? resolveDestinationSlugFromHost(window.location.hostname)
        : FALLBACK_DESTINATION_SLUG;

// TODO(multidomain): cross-domain nav was removed when the header/hero switched to
// scrolling to the homepage #activities section. To reimplement multi-destination
// support later, restore:
//   export const DESTINATIONS_URL = 'https://trivlu.com';            // apex marketing site
//   export function activitiesUrl(slug) { return `https://${slug}.trivlu.com`; }
// and point the Header "Destinations"/"Activities" links + the hero "Explore
// activities" CTA at them (see git history of Header.js / HomePage.js).
// resolveDestinationSlugFromHost above still resolves the per-subdomain slug.
