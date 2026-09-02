// Pure field validators for admin forms. Each returns an error message string
// when invalid, or undefined when valid.

export function required(value, message = 'This field is required.') {
    if (value === null || value === undefined) return message;
    if (typeof value === 'string' && value.trim() === '') return message;
    return undefined;
}

export function slugFormat(value, message = 'Use lowercase letters, numbers and hyphens only.') {
    // Slug is optional (auto-generated when blank); only validate when present.
    if (value === null || value === undefined || value === '') return undefined;
    return /^[a-z0-9-]+$/.test(value) ? undefined : message;
}

export function discountRange(value, message = 'Discount must be between 0 and 100.') {
    const n = Number(value);
    if (!Number.isFinite(n) || n < 0 || n > 100) return message;
    return undefined;
}

// Deliberately permissive (anything@anything.tld): the server's @Email is the
// authority; this only stops obvious typos before a request is made.
const EMAIL_RE = /\S+@\S+\.\S+/;

export function emailFormat(value, message = 'Please check the email address.') {
    const trimmed = typeof value === 'string' ? value.trim() : '';
    return EMAIL_RE.test(trimmed) ? undefined : message;
}
