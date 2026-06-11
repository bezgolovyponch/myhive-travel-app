import {DEFAULT_DESTINATION_SLUG} from '../services/config';

// Destination to use when the user has not (or cannot have) chosen one:
// the configured default, or the first one the API returned as a fallback.
export function getDefaultDestination(destinations) {
    return destinations.find(d => d.slug === DEFAULT_DESTINATION_SLUG)
        || destinations[0]
        || null;
}
