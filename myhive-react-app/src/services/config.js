export const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
export const SITE_URL = process.env.REACT_APP_SITE_URL || 'https://trivlu.com';

// Placeholder support links until the real WhatsApp number / FB page are provided
export const WHATSAPP_URL = 'https://wa.me/0000000000';
export const MESSENGER_URL = 'https://m.me/trivlu';

// Prague is the only destination on sale, so destination choice is hidden and the
// default destination is used. Flip to true when new destinations open.
export const DESTINATION_PICKER_ENABLED = false;

// Used wherever the UI needs a destination without asking the user; falls back to
// the first destination from the API if this slug is missing.
export const DEFAULT_DESTINATION_SLUG = 'prague';
