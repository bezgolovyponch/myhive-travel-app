// Legal entity details for Trivlu, shared across the policy pages (Terms,
// Refund Policy, Privacy Policy) so the company facts live in one place.
// Source: Czech business registry (rejstrik-firem.kurzy.cz/11692111).
export const COMPANY = {
    tradeName: 'Trivlu',
    legalName: 'PRAGOUT GROUP s.r.o.',
    address: 'Na Folimance 2155/15, Vinohrady, 120 00 Prague 2, Czech Republic',
    companyId: '11692111', // IČO
    registration: 'Municipal Court in Prague, file C 352982 (registered 27 July 2021)',
    contactEmail: 'info@trivlu.com',
    bookingsEmail: 'bookings@trivlu.com',
    website: 'https://trivlu.com',
    governingLaw: 'Czech Republic',
};

// Kept in one place so every policy page shows the same effective date and it is
// trivial to bump when the wording changes.
export const POLICY_EFFECTIVE_DATE = 'July 15, 2026';
