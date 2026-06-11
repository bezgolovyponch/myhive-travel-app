export const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';
export const SITE_URL = process.env.REACT_APP_SITE_URL || 'https://trivlu.com';

// Placeholder support links until the real WhatsApp number / FB page are provided
export const WHATSAPP_URL = 'https://wa.me/0000000000';
export const MESSENGER_URL = 'https://m.me/trivlu';

// Prague is the only destination on sale, so destination choice is hidden and the
// first destination from the API is used. Flip to true when new destinations open.
export const DESTINATION_PICKER_ENABLED = false;
