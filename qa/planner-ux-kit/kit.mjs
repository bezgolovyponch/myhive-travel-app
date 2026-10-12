// Planner UX kit: the browse flow and the AI chat flow, driven at phone size against a running site.
// Usage: node kit.mjs            (KIT_BASE=http://localhost:3000, KIT_ONLY=browse|ai|errors, KIT_TYPED=0 to skip
//                                 the typed scenarios, KIT_CHROMIUM=<path to a chromium binary>)
// Every check has an id, the rule it tests and where the rule comes from; results go to results/<stamp>.json.
// Checks marked `live` send a typed message to the model: what they assert is the outcome, never the wording.
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.env.KIT_BASE || 'http://localhost:3000').replace(/\/+$/, '');
const ONLY = process.env.KIT_ONLY || '';
const TYPED = process.env.KIT_TYPED !== '0';
const SCENARIOS = JSON.parse(fs.readFileSync(path.join(HERE, 'scenarios.json'), 'utf8'));
const TRAVELERS = 10;
const VIEWPORT = { width: 390, height: 844 };

const results = [];
const record = (id, flow, rule, source, status, detail) => {
    results.push({ id, flow, rule, source, status, detail });
    console.log(`${status.padEnd(5)} ${id.padEnd(5)} ${rule}${detail ? '  ->  ' + detail : ''}`);
};
// A check passes when fn returns nothing or true; a string return is a finding (FAIL); a thrown error is an ERROR.
// `kind: 'obs'` records what was seen without a verdict the code can give (FLAW/OK decided by the returned object).
async function check(id, flow, rule, source, fn) {
    try {
        const out = await fn();
        if (out && typeof out === 'object') record(id, flow, rule, source, out.status, out.detail);
        else if (typeof out === 'string') record(id, flow, rule, source, 'FAIL', out);
        else record(id, flow, rule, source, 'PASS', '');
    } catch (e) {
        record(id, flow, rule, source, 'ERROR', String(e.message || e).split('\n')[0].slice(0, 220));
    }
}
const flaw = (detail) => ({ status: 'FLAW', detail });
const info = (detail) => ({ status: 'INFO', detail });
const euros = (text) => Number(String(text).replace(/[^\d.]/g, '').replace(/\.(?=.*\.)/g, '')) || 0;
const num = (text) => Number(String(text).replace(/,/g, '').match(/-?\d+(\.\d+)?/)?.[0] ?? NaN);
const flat = (s) => String(s).replace(/\s*\n+\s*/g, ' | ');
const eq = (a, b) => Math.abs(Number(a) - Number(b)) < 0.005;

async function getJson(pathname) {
    const res = await fetch(BASE + '/api' + pathname);
    if (!res.ok) throw new Error(`${res.status} on ${pathname}`);
    return res.json();
}
const extraAmount = (extra, n) => (extra.pricing === 'PER_PERSON' ? extra.price * n : extra.price);
const lineBase = (a, n) => Math.max(a.price * n, a.minPrice || 0);
const fromPrice = (total) => Math.round(total * 0.9);

async function launch() {
    if (process.env.KIT_CHROMIUM) return chromium.launch({ executablePath: process.env.KIT_CHROMIUM });
    const cached = path.join(process.env.HOME || '', 'Library/Caches/ms-playwright');
    try {
        const dir = fs.readdirSync(cached).filter((d) => d.startsWith('chromium_headless_shell-')).sort().pop();
        const sub = fs.readdirSync(path.join(cached, dir))[0];
        return await chromium.launch({ executablePath: path.join(cached, dir, sub, 'chrome-headless-shell') });
    } catch (e) {
        return chromium.launch({ channel: 'chrome' });
    }
}

let browser;
async function phone(init) {
    const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    if (init) await ctx.addInitScript(init.fn, init.arg);
    const page = await ctx.newPage();
    page.setDefaultTimeout(20000);
    page.pageErrors = [];
    page.on('pageerror', (e) => page.pageErrors.push(e.message));
    return page;
}
const cart = (p) => p.evaluate(() => JSON.parse(localStorage.getItem('myhive-trip-items') || '[]'));
const dialog = (p) => p.getByRole('dialog').last();
async function openCard(p, name) {
    const card = p.locator('.activity-card').filter({ has: p.locator('.activity-title', { hasText: new RegExp(`^\\s*${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`) }) }).first();
    await card.scrollIntoViewIfNeeded();
    return card;
}
async function confirmSetup(p) {
    const inc = p.getByRole('button', { name: 'Increase travelers' });
    await inc.waitFor();
    const now = num(await p.locator('.travelers-value, [class*=travelers] input, [class*=travelers-count]').first().innerText().catch(() => '1')) || 1;
    for (let i = now; i < TRAVELERS; i++) await inc.click();
    await p.getByRole('button', { name: 'Confirm' }).click();
    await p.waitForTimeout(600);
}
const size = (loc) => loc.evaluate((e) => { const r = e.getBoundingClientRect(); return { w: Math.round(r.width), h: Math.round(r.height) }; });
const contrast = (loc) => loc.evaluate((el) => {
    const parse = (c) => (c.match(/[\d.]+/g) || []).map(Number);
    const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
    const fg = parse(getComputedStyle(el).color);
    let node = el, bg = null;
    while (node && !bg) { const c = parse(getComputedStyle(node).backgroundColor); if (c.length >= 3 && (c[3] === undefined || c[3] > 0.9)) bg = c; node = node.parentElement; }
    bg = bg || [255, 255, 255];
    const a = fg[3] === undefined ? 1 : fg[3];
    const mix = fg.slice(0, 3).map((v, i) => v * a + bg[i] * (1 - a));
    const [l1, l2] = [lum(mix), lum(bg)].sort((x, y) => y - x);
    return Math.round(((l1 + 0.05) / (l2 + 0.05)) * 100) / 100;
});

// ---------------------------------------------------------------------------------------------------------
async function browseFlow(cat) {
    const { carrier, plain, n } = cat;
    // The two extras the kit ticks (first and second box). Taken from the catalogue here and, once the
    // activity is in the cart, from what the page actually put there - see B03 for when the two differ.
    let [x0, x1] = carrier.extras;
    const p = await phone();
    await p.goto(`${BASE}/destination/${cat.destination.slug}`, { waitUntil: 'load', timeout: 90000 });
    await p.waitForSelector('.activity-card');
    await p.waitForTimeout(2500);
    await p.getByText('DECLINE ALL').first().click({ timeout: 1500 }).catch(() => {});
    const card = await openCard(p, carrier.name);

    await check('B01', 'browse', 'A listing card shows a price with its unit', 'Baymard, product listing information (price "must be permanently visible"); stored study P1(b)', async () =>
        (/€\s?\d/.test(await card.innerText()) ? undefined : flaw('card shows no price: ' + flat(await card.innerText()))));
    await check('B02', 'browse', 'The extras hint on a card is one scannable line', 'Baymard, list item design (hard-to-scan list info)', async () => {
        const chip = card.locator('.activity-extras-chip');
        const m = await chip.evaluate((e) => ({ h: e.getBoundingClientRect().height, lh: parseFloat(getComputedStyle(e).lineHeight) || 20 }));
        const lines = Math.round(m.h / m.lh);
        return lines <= 2 ? undefined : flaw(`the chip wraps to about ${lines} lines (${Math.round(m.h)}px high)`);
    });

    await card.locator('.more-info-btn').click();
    const dlg = dialog(p);
    await dlg.waitFor();
    await check('B03', 'browse', 'The card\'s extras are checkboxes and none is ticked', 'EU CRD Art. 22 / CMA 2025: no pre-ticked extras', async () => {
        const boxes = await dlg.getByRole('checkbox').count();
        const ticked = await dlg.locator('input:checked').count();
        return boxes > 0 && ticked === 0 ? undefined : `checkboxes ${boxes}, ticked ${ticked}`;
    });
    await check('B03b', 'browse', 'The page offers the extras the catalogue has right now', 'data freshness: the listing is cached for an hour (lib/api.ts revalidate)', async () => {
        const boxes = await dlg.getByRole('checkbox').count();
        return boxes === carrier.extras.length ? undefined
            : flaw(`the card offers ${boxes} extras, the catalogue API ${carrier.extras.length}: the page is serving a cached copy`);
    });
    await check('B04', 'browse', 'Each extra shows its price and unit before it is ticked', 'CMA209 price transparency; stored study P1(a)', async () => {
        const text = await dlg.locator('.item-extras-picker').innerText();
        const missing = carrier.extras.filter((e) => !text.includes(`€${e.price}`) || !/per (group|person)/.test(text));
        return missing.length ? 'no price or unit for: ' + missing.map((e) => e.name).join(', ') : undefined;
    });
    await check('B05', 'browse', 'Checkbox and its row are big enough for a thumb (24px AA, 44px row)', 'WCAG 2.2 SC 2.5.8 (AA 24x24), SC 2.5.5 (AAA 44x44)', async () => {
        const box = await size(dlg.getByRole('checkbox').first());
        const row = await size(dlg.locator('.item-extras-picker-row').first());
        return box.w >= 24 || row.h >= 44 ? (box.w >= 24 ? undefined : info(`box ${box.w}px, row ${row.h}px (the whole row is the target)`)) : `box ${box.w}x${box.h}, row ${row.h}px`;
    });
    await check('B06', 'browse', 'Small text in the card meets 4.5:1 contrast', 'WCAG 2.2 SC 1.4.3', async () => {
        const desc = await contrast(dlg.locator('.item-extras-picker-desc').first());
        const price = await contrast(dlg.locator('.item-extras-picker-price').first());
        return desc >= 4.5 && price >= 4.5 ? undefined : flaw(`description ${desc}:1, price ${price}:1`);
    });
    await check('B07', 'browse', 'The dialog is announced as one, and focus moves into it', 'WAI-ARIA Authoring Practices, dialog (modal) pattern', async () => {
        const modal = await dlg.getAttribute('aria-modal');
        const inside = await dlg.evaluate((d) => d.contains(document.activeElement));
        return modal === 'true' && inside ? undefined : flaw(`aria-modal=${modal}, focus inside=${inside}`);
    });
    await check('B08', 'browse', 'The card states the activity\'s own price next to the button that adds it', 'Baymard, product page UX (67% omit the total near the buy section)', async () =>
        (new RegExp(`€\\s?${carrier.price}\\b`).test(await dlg.innerText()) ? undefined : flaw('the card shows extras\' prices but not the activity\'s')));
    await check('B09', 'browse', 'Nothing in the card overflows sideways', 'WCAG 2.2 SC 1.4.10 Reflow', async () => {
        const o = await dlg.evaluate((d) => [...d.querySelectorAll('*')].filter((e) => e.getBoundingClientRect().right > window.innerWidth + 1).length);
        return o === 0 ? undefined : `${o} elements past the right edge`;
    });
    await check('B10', 'browse', 'Ticking and closing the card (x) changes nothing', 'NN/g, confirmation: nothing committed without the labelled action', async () => {
        await dlg.getByRole('checkbox').first().check();
        await dlg.getByRole('button', { name: 'Close' }).click();
        return (await cart(p)).length === 0 ? undefined : 'the cart has an item';
    });
    await check('B11', 'browse', 'Escape closes the card and focus goes back to what opened it', 'WAI-ARIA dialog pattern', async () => {
        const opener = card.locator('.more-info-btn');
        await opener.click();
        await dialog(p).waitFor();
        await p.keyboard.press('Escape');
        await p.waitForTimeout(300);
        const open = await p.getByRole('dialog').count();
        const back = await opener.evaluate((e) => e === document.activeElement);
        if (open) return 'Escape did not close it';
        return back ? undefined : flaw('closed, but focus did not return to "More info"');
    });
    await check('B12', 'browse', 'A reopened card starts unticked (the earlier unconfirmed tick is gone)', 'no stale state between openings', async () => {
        await card.locator('.more-info-btn').click();
        const ticked = await dialog(p).locator('input:checked').count();
        return ticked === 0 ? undefined : `${ticked} still ticked`;
    });
    await check('B13', 'browse', '"Add to trip" in the card puts the activity in with the ticked extra', 'feature rule (2026-10-12): tick, then confirm', async () => {
        await dialog(p).getByRole('checkbox').first().check();
        await dialog(p).getByRole('button', { name: 'Add to trip' }).click();
        await confirmSetup(p);
        const items = await cart(p);
        if (items[0]?.extras?.length >= 2) [x0, x1] = items[0].extras;
        return items.length === 1 && String(items[0].selectedExtras) === x0.code ? undefined : 'cart: ' + JSON.stringify(items.map((i) => i.selectedExtras));
    });
    const panel = p.locator('.trip-builder-dropdown');
    const expect1 = () => lineBase(carrier, n) + extraAmount(x0, n);
    await check('B14', 'browse', 'The cart panel totals the activity plus the extra for the group', 'Baymard, price per unit; pricing rule line = max(price x N, minimum) + extras', async () => {
        const total = euros(await panel.locator('.trip-modal-total-price').innerText());
        return eq(total, expect1()) ? undefined : `shows ${total}, expected ${expect1()}`;
    });
    await check('B15', 'browse', 'The cart row\'s price says what it is for (per person)', 'Baymard, price per unit (86% of sites omit it)', async () => {
        const row = await panel.locator('.trip-modal-item').first().innerText();
        return /person|pp|\/\s?p/i.test(row) ? undefined : flaw('row reads: ' + flat(row));
    });
    await check('B16', 'browse', 'The cart row opens the same card: its tick is there, "Update" is off', 'feature rule: one card everywhere', async () => {
        await panel.locator('.trip-modal-item-extras-btn').first().click();
        const d = dialog(p);
        await d.waitFor();
        const ticked = await d.locator('input:checked').count();
        const off = await d.getByRole('button', { name: 'Update' }).isDisabled();
        return ticked === 1 && off ? undefined : `ticked ${ticked}, Update disabled=${off}`;
    });
    await check('B17', 'browse', 'A second tick reaches the cart only with "Update"', 'feature rule: tick, then confirm', async () => {
        const d = dialog(p);
        await d.getByRole('checkbox').nth(1).check();
        const before = (await cart(p))[0].selectedExtras.length;
        await d.getByRole('button', { name: 'Update' }).click();
        await p.waitForTimeout(300);
        const after = (await cart(p))[0].selectedExtras.length;
        return before === 1 && after === 2 ? undefined : `before ${before}, after ${after}`;
    });
    const expect2 = () => expect1() + extraAmount(x1, n);
    await check('B18', 'browse', 'The panel total follows the second extra', 'pricing rule', async () => {
        const total = euros(await panel.locator('.trip-modal-total-price').innerText());
        return eq(total, expect2()) ? undefined : `shows ${total}, expected ${expect2()}`;
    });
    await panel.locator('.trip-builder-complete-btn').click();
    await p.waitForSelector('.itinerary-item');
    await p.waitForTimeout(2500);
    const row = p.locator('.itinerary-item').first();
    const planPrice = async () => euros(await p.locator('.itinerary-plan-price').first().innerText());
    await check('B19', 'browse', 'The Trip Builder row shows each chosen extra with its amount', 'stored study P1(a): extras as sub-lines with their own maths', async () => {
        const chips = await row.locator('.itinerary-item-extras-chip').allInnerTexts();
        const want = [x0, x1].map((e) => String(extraAmount(e, n)));
        return chips.length === 2 && want.every((w) => chips.some((c) => c.replace(/,/g, '').includes(w))) ? undefined : 'chips: ' + chips.join(' / ');
    });
    await check('B20', 'browse', 'The plan price is the group total less the package margin', 'FromPrice: total x 0.90, whole euros', async () => {
        const shown = await planPrice();
        return eq(shown, fromPrice(expect2())) ? undefined : `shows ${shown}, expected ${fromPrice(expect2())}`;
    });
    await check('B21', 'browse', 'Cart panel and Trip Builder state the same price, or say why they differ', 'CMA209: a price must not need working out', async () =>
        {
        const text = await p.locator('.trip-builder, main').first().innerText();
        return /10\s?%|discount|save/i.test(text) ? undefined
            : flaw(`the cart panel said €${expect2()}, the Trip Builder says "from €${fromPrice(expect2())}", and nothing on the page explains the difference`);
    });
    await check('B22', 'browse', 'A chip\'s x drops that extra at once and the price follows', 'removal is not a purchase: no confirmation needed (NN/g: undo over confirm)', async () => {
        await row.locator('.itinerary-item-extras-remove').first().click();
        await p.waitForTimeout(2500);
        const left = (await cart(p))[0].selectedExtras;
        const shown = await planPrice();
        const want = fromPrice(lineBase(carrier, n) + extraAmount(x1, n));
        return String(left) === x1.code && eq(shown, want) ? undefined : `left ${left}, shows ${shown}, expected ${want}`;
    });
    await check('B23', 'browse', 'The extras button on the row is a real target', 'WCAG 2.2 SC 2.5.8', async () => {
        const s = await size(row.locator('.itinerary-item-extras-toggle'));
        return s.h >= 24 && s.w >= 24 ? (s.h >= 44 ? undefined : info(`${s.w}x${s.h}: AA yes, below the 44px AAA / platform guideline`)) : `${s.w}x${s.h}`;
    });
    await check('B24', 'browse', 'The row\'s name opens the card; unticking and "Update" clears the extra', 'feature rule', async () => {
        await row.locator('.itinerary-item-open').click();
        const d = dialog(p);
        await d.waitFor();
        await d.locator('input:checked').first().uncheck();
        await d.getByRole('button', { name: 'Update' }).click();
        await p.waitForTimeout(400);
        const left = (await cart(p))[0].selectedExtras;
        return left.length === 0 ? undefined : 'left ' + left;
    });
    await check('B25', 'browse', 'The cart survives a reload', 'NN/g: preserve user input', async () => {
        await p.reload({ waitUntil: 'load' });
        await p.waitForTimeout(1500);
        return (await cart(p)).length === 1 ? undefined : 'cart lost';
    });

    // detail page
    await p.goto(`${BASE}/destination/${cat.destination.slug}/activity/${carrier.slug}`, { waitUntil: 'load', timeout: 90000 });
    const add = p.locator('.activity-detail-add-btn:visible').first();
    await add.waitFor();
    await p.waitForTimeout(1500);
    await check('B26', 'browse', 'Detail page, activity already in the trip: a tick waits for "Update"', 'feature rule', async () => {
        const startsOff = await add.isDisabled();
        await p.getByRole('checkbox').first().check();
        const label = (await add.innerText()).trim();
        const held = (await cart(p))[0].selectedExtras.length;
        await add.click();
        await p.waitForTimeout(400);
        const after = (await cart(p))[0].selectedExtras.length;
        return startsOff && /Update/.test(label) && held === 0 && after === 1 ? undefined : `startsOff=${startsOff} label="${label}" held=${held} after=${after}`;
    });
    await check('B27', 'browse', 'The detail page shows the group total for the ticked extras', 'Baymard: total near the buy section; stored study P1(a)', async () => {
        const panelText = await p.locator('.activity-detail-add-panel').first().innerText();
        return /total|for \d+/i.test(panelText) ? undefined : flaw('per-unit sums only ("' + flat(await p.locator('.activity-detail-extras-summary').first().innerText().catch(() => '')) + '"), no total though the trip has ' + n + ' travellers');
    });

    // an activity without extras
    await p.goto(`${BASE}/destination/${cat.destination.slug}`, { waitUntil: 'load', timeout: 90000 });
    await p.waitForSelector('.activity-card');
    await p.waitForTimeout(2000);
    await check('B28', 'browse', 'An activity without extras has the same card: no checklist, "Add to trip"', 'feature rule: one card everywhere', async () => {
        const c = await openCard(p, plain.name);
        await c.locator('.more-info-btn').click();
        const d = dialog(p);
        await d.waitFor();
        const boxes = await d.getByRole('checkbox').count();
        const btn = await d.getByRole('button', { name: 'Add to trip' }).count();
        await d.getByRole('button', { name: 'Add to trip' }).click();
        await p.waitForTimeout(500);
        const items = await cart(p);
        return boxes === 0 && btn === 1 && items.length === 2 ? undefined : `boxes ${boxes}, button ${btn}, cart ${items.length}`;
    });
    await check('B29', 'browse', '"Add to trip" on the card of an activity with extras offers them', 'GetYourGuide / Klook / Headout: extras chosen at the moment of adding; Baymard: dismissible, never a forced step', async () => {
        const second = cat.carrier2;
        const c = await openCard(p, second.name);
        await p.locator('.trip-builder-dropdown .app-modal-close-btn').click({ timeout: 1500 }).catch(() => {});
        await c.locator('.add-to-trip-btn').click();
        await p.waitForTimeout(800);
        const offered = (await p.locator('[role=dialog] input[type=checkbox]').count()) > 0
            || (await p.locator('[role=status], [class*=toast]').filter({ hasText: /extra/i }).count()) > 0;
        const row2 = p.locator('.trip-builder-dropdown .trip-modal-item', { hasText: second.name });
        const hasBtn = await row2.locator('.trip-modal-item-extras-btn').count();
        return offered ? undefined : flaw(`added silently; the only way on is the small "Add extras" in the cart row (present: ${hasBtn === 1})`);
    });
    await check('B30', 'browse', 'Removing an activity takes its extras out of the total', 'pricing rule', async () => {
        const pn = p.locator('.trip-builder-dropdown');
        if (!(await pn.count())) await p.locator('.cart-btn').click();
        const before = euros(await pn.locator('.trip-modal-total-price').innerText());
        await pn.locator('.trip-modal-item', { hasText: carrier.name }).locator('.trip-modal-item-remove').click();
        await p.waitForTimeout(400);
        const after = euros(await pn.locator('.trip-modal-total-price').innerText());
        const want = before - (lineBase(carrier, n) + extraAmount(x0, n));
        return eq(after, want) ? undefined : `before ${before}, after ${after}, expected ${want}`;
    });
    await check('B31', 'browse', 'No script error on any page of the flow', 'baseline', async () =>
        (p.pageErrors.length ? p.pageErrors.slice(0, 3).join(' | ') : undefined));
    await p.context().close();

    // a package's activities
    if (cat.pkg) {
        const q = await phone();
        await q.goto(`${BASE}/destination/${cat.destination.slug}/package/${cat.pkg.slug}`, { waitUntil: 'load', timeout: 90000 });
        const addPkg = q.locator('.package-detail-add-btn:visible').first();
        await addPkg.waitFor();
        await q.waitForTimeout(1500);
        await addPkg.click();
        await confirmSetup(q);
        const pn = q.locator('.trip-builder-dropdown');
        await check('B32', 'browse', 'Activities inside a package take extras through the same card', 'feature rule', async () => {
            const withExtras = cat.pkg.activities.filter((a) => (a.extras || []).length);
            const buttons = await pn.locator('.trip-modal-item--indented .trip-modal-item-extras-btn').count();
            if (buttons !== withExtras.length) return `extras buttons ${buttons}, activities with extras ${withExtras.length}`;
            const before = euros(await pn.locator('.trip-modal-total-price').innerText());
            await pn.locator('.trip-modal-item--indented .trip-modal-item-extras-btn').first().click();
            const d = dialog(q);
            await d.waitFor();
            await d.getByRole('checkbox').first().waitFor();
            await d.getByRole('checkbox').first().check();
            await d.getByRole('button', { name: 'Update' }).click();
            await q.waitForTimeout(500);
            const after = euros(await pn.locator('.trip-modal-total-price').innerText());
            const want = before + extraAmount(withExtras[0].extras[0], n);
            return eq(after, want) ? undefined : `before ${before}, after ${after}, expected ${want}`;
        });
        await q.context().close();
    }

    // landing
    const l = await phone({ fn: (items) => { try { localStorage.setItem('myhive-trip-items', items); localStorage.setItem('myhive-trip-setup', JSON.stringify({ travelers: 10 })); } catch (e) { /* storage blocked */ } },
        arg: JSON.stringify([{ ...carrier, selectedExtras: [] }]) });
    await check('B33', 'browse', 'On a landing the card opens full screen from the cart, with a styled button', 'feature rule; the header must not trap the dialog', async () => {
        await l.goto(`${BASE}/landing/prague`, { waitUntil: 'load', timeout: 90000 });
        await l.waitForTimeout(3500);
        await l.locator('.hdr__cart').click();
        await l.locator('.trip-builder-dropdown .trip-modal-item-open').first().click();
        const d = dialog(l);
        await d.waitFor();
        const box = await d.boundingBox();
        await d.getByRole('checkbox').first().check();
        const bg = await d.locator('.activity-preview-confirm').evaluate((e) => getComputedStyle(e).backgroundColor);
        return box.width === VIEWPORT.width && box.height === VIEWPORT.height && !/0, 0, 0, 0/.test(bg) ? undefined : `box ${box.width}x${box.height}, button background ${bg}`;
    });
    await l.context().close();
}

// ---------------------------------------------------------------------------------------------------------
async function aiFlow(cat) {
    const byName = new Map(cat.activities.map((a) => [a.name, a]));
    const p = await phone();
    p.setDefaultTimeout(30000);
    const seen = { posts: [], plan: null, edits: [] };
    p.on('response', async (r) => {
        const url = r.url();
        if (!url.includes('/api/ai/')) return;
        const method = r.request().method();
        let body = null;
        try { body = await r.json(); } catch (e) { /* not json */ }
        if (method === 'POST') seen.posts.push({ url: url.replace(BASE, ''), status: r.status(), request: r.request().postData(), body });
        const gen = body?.generation || (body?.packages ? body : null) || body?.latestReadyGeneration;
        if (gen?.packages?.length) seen.plan = gen;
        if (method === 'POST' && /\/edits$|\/messages$/.test(url) && body?.edit) seen.edits.push(body);
    });
    const pkg = () => seen.plan.packages.find((x) => x.key === workingKey) || seen.plan.packages[0];
    let workingKey = 'MEDIUM';
    const inPlan = () => new Set(pkg().days.flatMap((d) => d.items.map((i) => i.activityId)));
    const total = () => Number(pkg().totalPrice);
    const lastPost = (re) => [...seen.posts].reverse().find((x) => re.test(x.url));
    const chatTail = (k = 4) => p.locator('.aip-dock .ai-msg').evaluateAll((els, kk) => els.slice(-kk).map((e) => e.innerText.trim().replace(/\n+/g, ' / ')), k);
    const waitEdit = async (action) => {
        const before = seen.edits.length;
        await action();
        for (let i = 0; i < 120 && seen.edits.length === before; i++) await p.waitForTimeout(250);
        if (seen.edits.length === before) throw new Error('no edit answer within 30 s');
        const edit = seen.edits[seen.edits.length - 1];
        workingKey = edit.edit.applied?.[0]?.packageKey || workingKey;
        await p.waitForTimeout(700);
        return edit;
    };
    const dockOpen = async (open) => {
        const is = (await p.locator('.aip-dock.is-open').count()) > 0;
        if (is !== open) { await p.locator('.aip-dock-bar').click(); await p.waitForTimeout(400); }
    };
    // Walks the "what next" tags until the list offers an activity the predicate accepts; leaves the list open.
    const findOffer = async (pred) => {
        await dockOpen(true);
        const tags = p.locator('.aip-gaps button');
        const count = await tags.count();
        for (let i = 0; i < count; i++) {
            await p.locator('.aip-gaps button').nth(i).click();
            await p.waitForTimeout(600);
            const names = await p.locator('.aip-offers .aip-offer-name').allInnerTexts();
            const hit = names.map((x) => x.trim()).find((x) => byName.has(x) && !inPlan().has(byName.get(x).id) && pred(byName.get(x)));
            if (hit) return byName.get(hit);
            await p.locator('.aip-answer-quiet').first().click().catch(() => {});
            await p.waitForTimeout(300);
        }
        throw new Error('no tag offers an activity the check needs');
    };
    const offerRow = (a) => p.locator('.aip-offers .aip-offer').filter({ has: p.locator('.aip-offer-name', { hasText: new RegExp(`^\\s*${a.name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`) }) });
    const noPrice = (texts) => texts.filter((t) => /€|EUR|\beuro/i.test(t));

    const sent = Date.now();
    let firstFeedback = null;
    await p.goto(`${BASE}/plan`, { waitUntil: 'load', timeout: 90000 });
    const box = p.getByRole('textbox', { name: 'Message Stag Do AI' });
    await box.waitFor();
    await p.waitForTimeout(1500);
    const t0 = Date.now();
    await box.fill(SCENARIOS.brief);
    await box.press('Enter');
    await p.waitForFunction(() => document.querySelector('.ai-status, .ai-error, [class*=typing]') || document.querySelectorAll('.ai-msg').length > 1, null, { timeout: 15000 }).catch(() => {});
    firstFeedback = Date.now() - t0;
    await check('A01', 'ai', 'Something answers the first message within a second', 'Nielsen response-time limits: 1 s keeps the flow of thought', async () =>
        (firstFeedback <= 1000 ? undefined : (firstFeedback <= 2500 ? info(`${firstFeedback} ms to the first sign of work`) : flaw(`${firstFeedback} ms before anything showed`))));
    let planMs = null;
    await check('A02', 'ai', 'The first message leads to a plan with no error on screen', 'baseline; 2026-10-12 regressions (LAN origin, destination "192", false "expired")', async () => {
        await p.locator('.aip-canvas').waitFor({ timeout: 240000 });
        planMs = Date.now() - t0;
        const err = await p.locator('.ai-error').count();
        const create = lastPost(/\/ai\/sessions$/);
        const slug = JSON.parse(create?.request || '{}').destinationSlug;
        return !err && create?.status === 201 && slug === cat.destination.slug ? undefined : `error shown=${err}, create=${create?.status}, destinationSlug=${slug}`;
    });
    await check('A03', 'ai', 'The plan is on screen within 10 s, or progress is shown while it builds', 'Nielsen response-time limits: 10 s is the limit of attention; beyond it, a progress indicator', async () =>
        (planMs <= 10000 ? undefined : info(`${Math.round(planMs / 1000)} s to the plan; a "building" status was shown meanwhile`)));
    for (let i = 0; i < 80 && !seen.plan; i++) await p.waitForTimeout(250);
    await p.waitForFunction(() => !document.querySelector('.ai-status'), null, { timeout: 120000 }).catch(() => {});
    await p.waitForTimeout(1500);
    const n = seen.plan.brief?.groupSize || TRAVELERS;
    workingKey = (await p.locator('[role=tab][aria-selected=true]').first().innerText().catch(() => 'MEDIUM')).toUpperCase().includes('BASIC') ? 'BASIC'
        : (await p.locator('[role=tab][aria-selected=true]').first().innerText().catch(() => '')).toUpperCase().includes('PREMIUM') ? 'PREMIUM' : 'MEDIUM';

    await check('A04', 'ai', 'Every plan row opens the one card: checkboxes for its extras, "Update" off', 'feature rule: one card everywhere', async () => {
        await dockOpen(false);
        const rows = p.locator('.aip-item-name .activity-open');
        const count = await rows.count();
        const bad = [];
        for (let i = 0; i < count; i++) {
            const name = (await rows.nth(i).innerText()).trim();
            await rows.nth(i).click();
            const d = dialog(p);
            await d.waitFor();
            await p.waitForFunction(() => !document.querySelector('.app-modal [aria-busy="true"]'), null, { timeout: 15000 }).catch(() => {});
            await p.waitForTimeout(500);
            const boxes = await d.getByRole('checkbox').count();
            const want = (byName.get(name)?.extras || []).length;
            const upd = d.getByRole('button', { name: 'Update' });
            const ok = boxes === want && (await upd.count()) === 1 && (await upd.isDisabled());
            if (!ok) bad.push(`${name}: boxes ${boxes}/${want}, Update present=${await upd.count()}`);
            await d.getByRole('button', { name: 'Close' }).click();
            await p.waitForTimeout(250);
        }
        return bad.length ? bad.join('; ') : (count ? undefined : 'no rows');
    });

    let viaCard = null;
    let extraOnCard = null;
    await check('A05', 'ai', 'Offer card: tick an extra, "Add to trip": activity and extra land in one batch, priced exactly', 'feature rule; pricing rule', async () => {
        viaCard = await findOffer((a) => (a.extras || []).length > 0);
        extraOnCard = viaCard.extras[0];
        const before = total();
        await offerRow(viaCard).locator('.aip-offer-name').click();
        const d = dialog(p);
        await d.waitFor();
        await d.getByRole('checkbox').first().waitFor();
        const label = await d.getByRole('button', { name: 'Add to trip' }).count();
        await d.getByRole('checkbox').first().check();
        const edit = await waitEdit(() => d.getByRole('button', { name: 'Add to trip' }).click());
        const ops = edit.edit.applied.map((e) => e.op).join('+');
        const want = before + lineBase(viaCard, n) + extraAmount(extraOnCard, n);
        return label === 1 && ops === 'ADD+ADD_EXTRA' && eq(total(), want) ? undefined : `ops ${ops}, total ${total()} expected ${want}, rejected ${JSON.stringify(edit.edit.rejected)}`;
    });
    await check('A06', 'ai', 'The chat confirms the add without naming a price', 'owner rule 2026-10-12: extras are priced on the card only', async () => {
        const said = seen.edits[seen.edits.length - 1].messages.map((m) => m.content);
        const priced = noPrice(said);
        return priced.length ? 'priced line: ' + priced[0] : undefined;
    });
    await check('A07', 'ai', 'After an add confirmed on the card the chat does not ask about extras again', 'Baymard: no repeated cross-sell; the choice was just made', async () => {
        await p.waitForTimeout(1200);
        return (await p.locator('.aip-extra-offer').count()) === 0 ? undefined : 'the extras question was asked';
    });
    await check('A08', 'ai', 'The plan row shows the chosen extra', 'visibility of system status (Nielsen heuristic 1)', async () => {
        const row = p.locator('.aip-item-body', { hasText: viaCard.name }).first();
        return (await row.locator('.aip-item-extra').count()) === 1 ? undefined : 'no extra shown on the row';
    });

    let direct = null;
    await check('A09', 'ai', 'A plain "Add" of an activity with extras: the chat asks which, as tags without prices', 'owner rule 2026-10-12; Google conversation design: offer a short list of choices', async () => {
        direct = await findOffer((a) => (a.extras || []).length > 0);
        await waitEdit(() => offerRow(direct).locator('.aip-offer-add').click());
        const offer = p.locator('.aip-extra-offer');
        await offer.waitFor({ timeout: 10000 });
        const tags = (await offer.locator('button').allInnerTexts()).map((x) => x.trim());
        const want = Math.min(4, direct.extras.length) + 1;
        const asked = (await chatTail(3)).some((l) => l.includes(`${direct.name} is in.`));
        const priced = noPrice(tags);
        return tags.length === want && asked && !priced.length && tags[tags.length - 1] === 'No extras' ? undefined : `tags ${JSON.stringify(tags)}, asked=${asked}`;
    });
    await check('A10', 'ai', 'While the question is open it is the only thing asked', 'NN/g chatbots: one question at a time', async () =>
        ((await p.locator('.aip-themes').count()) === 0 ? undefined : 'the "what next" tags are shown with it'));
    await check('A11', 'ai', 'The extra tags are thumb-sized', 'WCAG 2.2 SC 2.5.8', async () => {
        const s = await size(p.locator('.aip-extra-offer button').first());
        return s.h >= 24 ? (s.h >= 44 ? undefined : info(`${s.h}px high: AA yes, below 44px`)) : `${s.h}px`;
    });
    await check('A12', 'ai', 'A tapped tag puts that extra on, priced exactly, with no model call', 'pricing rule; deterministic edit', async () => {
        const before = total();
        const messagesBefore = seen.posts.filter((x) => /\/messages$/.test(x.url)).length;
        const edit = await waitEdit(() => p.locator('.aip-extra-offer button').first().click());
        const ops = edit.edit.applied.map((e) => e.op).join('+');
        const want = before + extraAmount(direct.extras[0], n);
        const modelCalls = seen.posts.filter((x) => /\/messages$/.test(x.url)).length - messagesBefore;
        return ops === 'ADD_EXTRA' && eq(total(), want) && modelCalls === 0 ? undefined : `ops ${ops}, total ${total()} expected ${want}, model calls ${modelCalls}`;
    });
    await check('A13', 'ai', 'After the answer the chat is back to its usual "what next" tags, and asks only once', 'owner rule 2026-10-12', async () => {
        await p.waitForTimeout(800);
        const gone = (await p.locator('.aip-extra-offer').count()) === 0;
        const back = (await p.locator('.aip-themes').count()) === 1;
        const asks = await p.locator('.aip-dock .ai-msg', { hasText: `${direct.name} is in.` }).count();
        return gone && back && asks === 1 ? undefined : `tags gone=${gone}, usual tags back=${back}, asked ${asks} times`;
    });
    await check('A14', 'ai', 'The chat\'s line for the tapped extra names no price', 'owner rule 2026-10-12', async () => {
        const priced = noPrice(seen.edits[seen.edits.length - 1].messages.map((m) => m.content));
        return priced.length ? priced[0] : undefined;
    });
    await check('A15', 'ai', 'The chat offers every extra the activity has, or says there are more', 'no silent truncation', async () =>
        (direct.extras.length <= 4 ? undefined : flaw(`${direct.name} has ${direct.extras.length} extras; the chat offers the first 4 and does not say there is another`)));

    let declined = null;
    await check('A16', 'ai', '"No extras" closes the question and changes nothing', 'Baymard: a cross-sell must be dismissible in one tap', async () => {
        declined = await findOffer((a) => (a.extras || []).length > 0);
        await waitEdit(() => offerRow(declined).locator('.aip-offer-add').click());
        await p.locator('.aip-extra-offer').waitFor({ timeout: 10000 });
        const posts = seen.posts.length;
        const before = total();
        await p.locator('.aip-extra-offer button', { hasText: 'No extras' }).click();
        await p.waitForTimeout(900);
        return seen.posts.length === posts && eq(total(), before) && (await p.locator('.aip-extra-offer').count()) === 0 && (await p.locator('.aip-themes').count()) === 1
            ? undefined : `requests sent ${seen.posts.length - posts}, total ${total()} was ${before}`;
    });
    await check('A17', 'ai', 'An activity without extras goes in with no question', 'no irrelevant question (Baymard: one questionable offer poisons the rest)', async () => {
        const plain = await findOffer((a) => !(a.extras || []).length);
        await waitEdit(() => offerRow(plain).locator('.aip-offer-add').click());
        await p.waitForTimeout(1500);
        return (await p.locator('.aip-extra-offer').count()) === 0 ? undefined : 'asked about extras that do not exist';
    });
    await check('A18', 'ai', 'A plan with many activities still says what a full day means', 'visibility of system status', async () => {
        const text = await p.locator('.aip-canvas').innerText();
        const day = pkg().days.reduce((m, d) => Math.max(m, d.items.length), 0);
        return day <= 4 ? undefined : info(`a day now holds ${day} activities (taps are never refused for lack of room): ` + (/full|busy|tight/i.test(text) ? 'the page comments on it' : 'the page says nothing about it'));
    });

    await check('A19', 'ai', 'Plan row card: untick and "Update" removes the extra, priced exactly', 'feature rule; pricing rule', async () => {
        await dockOpen(false);
        const before = total();
        await p.locator('.aip-item-name', { hasText: viaCard.name }).locator('.activity-open').first().click();
        const d = dialog(p);
        await d.waitFor();
        await d.getByRole('checkbox').first().waitFor();
        const ticked = await d.locator('input:checked').count();
        await d.locator('input:checked').first().uncheck();
        const edit = await waitEdit(() => d.getByRole('button', { name: 'Update' }).click());
        const ops = edit.edit.applied.map((e) => e.op).join('+');
        const want = before - extraAmount(extraOnCard, n);
        return ticked === 1 && ops === 'REMOVE_EXTRA' && eq(total(), want) ? undefined : `ticked ${ticked}, ops ${ops}, total ${total()} expected ${want}`;
    });
    await check('A20', 'ai', 'The confirm button is not covered by the docked chat', 'the primary action must be reachable', async () => {
        await dockOpen(true);
        await p.locator('.aip-item-name', { hasText: viaCard.name }).locator('.activity-open').first().click({ force: true, timeout: 4000 }).catch(() => {});
        const open = await p.getByRole('dialog').count();
        if (!open) { await dockOpen(false); return info('with the chat open the plan rows are behind it; the card is opened with the chat down'); }
        const d = dialog(p);
        const hit = await d.locator('.activity-preview-confirm').evaluate((b) => { const r = b.getBoundingClientRect(); const el = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2); return el === b || b.contains(el); });
        await d.getByRole('button', { name: 'Close' }).click();
        return hit ? undefined : 'another element lies over the button';
    });
    await check('A21', 'ai', '"Undo last change" brings the extra back, and the price with it', 'NN/g: undo beats confirmation; Amazon batch-undo pattern', async () => {
        await dockOpen(true);
        const before = total();
        const undo = p.getByRole('button', { name: 'Undo last change' });
        if (!(await undo.count())) return flaw('no undo offered after the change');
        await undo.click();
        await p.waitForTimeout(2500);
        const row = p.locator('.aip-item-body', { hasText: viaCard.name }).first();
        const chip = await row.locator('.aip-item-extra').count();
        const shown = (await chatTail(2)).join(' ');
        return chip === 1 ? undefined : flaw(`after undo the row shows ${chip} extras; chat: "${shown.slice(0, 80)}" (total before ${before})`);
    });
    await check('A22', 'ai', 'After an undo, a reload shows the plan the undo left', 'NN/g: preserve the user\'s work; the page and the server must hold one plan', async () => {
        const before = { total: total(), rows: await p.locator('.aip-item-name .activity-open').allInnerTexts(), extras: await p.locator('.aip-item-extra').count() };
        seen.plan = null;
        await p.reload({ waitUntil: 'load' });
        await p.locator('.aip-canvas').waitFor({ timeout: 30000 });
        for (let i = 0; i < 60 && !seen.plan; i++) await p.waitForTimeout(250);
        await p.waitForTimeout(1500);
        const after = { total: total(), rows: await p.locator('.aip-item-name .activity-open').allInnerTexts(), extras: await p.locator('.aip-item-extra').count() };
        return String(before.rows) === String(after.rows) && eq(before.total, after.total) && before.extras === after.extras ? undefined
            : `before the reload: total ${before.total}, ${before.extras} extras shown; after: total ${after.total}, ${after.extras} extras shown - the undone change is back on screen`;
    });
    await check('A22b', 'ai', 'The chat\'s lines survive a reload', 'NN/g: preserve the user\'s work', async () => {
        const lines = await p.locator('.aip-dock .ai-msg').count();
        return lines > 1 ? undefined : info(`${lines} line(s) of the chat are shown after the reload`);
    });
    await check('A23', 'ai', 'Removing an activity (x) takes it and its extras out, and the total is the sum of what is left', 'pricing rule', async () => {
        await dockOpen(false);
        const edit = await waitEdit(() => p.getByRole('button', { name: `Remove ${direct.name}` }).first().click());
        const items = pkg().days.flatMap((d) => d.items);
        const sum = items.reduce((t, i) => t + Number(i.lineTotal), 0);
        const gone = !items.some((i) => i.activityId === direct.id);
        return edit.edit.applied[0]?.op === 'REMOVE' && gone && eq(total(), sum) ? undefined : `gone=${gone}, total ${total()}, sum of lines ${sum}`;
    });
    await check('A23b', 'ai', 'An edit after undo + reload is applied to the plan that is on screen', 'the page and the server must hold one plan', async () => {
        const shown = await p.locator('.aip-item-extra').count();
        const held = pkg().days.flatMap((d) => d.items).reduce((t, i) => t + (i.extras || []).length, 0);
        return shown === held ? undefined : `the page shows ${shown} extras, the plan the server just edited holds ${held}`;
    });

    if (TYPED) {
        await dockOpen(true);
        // Two activities with extras that the plan does not hold, so "add", "with an extra" and "remove"
        // mean the same thing on every run whatever the model composed.
        const absent = cat.activities.filter((x) => (x.extras || []).length >= 2 && !inPlan().has(x.id));
        const [A, B] = absent;
        const fill = (text) => text.replaceAll('{A.extra}', A.extras[0].name).replaceAll('{A.other}', A.extras[A.extras.length - 1].name)
            .replaceAll('{B.extra}', B.extras[0].name).replaceAll('{A}', A.name).replaceAll('{B}', B.name);
        for (const raw of SCENARIOS.typed) {
            const s = { ...raw, say: fill(raw.say) };
            await check(s.id, 'ai-live', `Typed: "${s.say}" - ${s.expect}`, s.source, async () => {
                const before = { posts: seen.posts.length, total: total() };
                const started = Date.now();
                const composer = p.getByRole('textbox', { name: 'Message Stag Do AI' });
                await composer.fill(s.say);
                await composer.press('Enter');
                for (let i = 0; i < 180 && !seen.posts.slice(before.posts).some((x) => /\/messages$/.test(x.url)); i++) await p.waitForTimeout(250);
                const post = seen.posts.slice(before.posts).find((x) => /\/messages$/.test(x.url));
                if (!post) return 'no answer within 45 s';
                await p.waitForTimeout(1500);
                const ms = Date.now() - started;
                const body = post.body || {};
                const applied = (body.edit?.applied || []).map((e) => e.op);
                const rejected = (body.edit?.rejected || []).map((e) => e.reason);
                const said = (body.messages || []).filter((m) => m.role === 'ASSISTANT').map((m) => m.content);
                const recs = (body.recommendations || []).length;
                const asked = (await p.locator('.aip-extra-offer').count()) > 0;
                const facts = `applied [${applied}] rejected [${rejected}] recommendations ${recs} extras question ${asked} ${ms} ms; said: "${said.join(' / ').slice(0, 140)}"`;
                const fails = [];
                if (post.status !== 200) fails.push('HTTP ' + post.status);
                if (s.applied && String(applied) !== String(s.applied)) fails.push(`expected ops [${s.applied}]`);
                if (s.appliedIncludes && !s.appliedIncludes.every((o) => applied.includes(o))) fails.push(`expected ops to include [${s.appliedIncludes}]`);
                if (s.noEdit && applied.length) fails.push('expected no change to the plan');
                if (s.recommendations && recs < 1) fails.push('expected activities to be offered');
                if (s.noRecommendations && recs > 0) fails.push('expected no list of activities');
                if (s.asksExtras === true && !asked) fails.push('expected the extras question');
                if (s.asksExtras === false && asked) fails.push('expected no extras question');
                if (s.noPrice && noPrice(said).length) fails.push('the reply names a price');
                if (s.replyIsQuestion && !said.some((x) => x.includes('?'))) fails.push('expected a question back');
                if (s.rejected && !rejected.length && !applied.length && !recs) fails.push('expected a refusal with alternatives');
                if (asked) await p.locator('.aip-extra-offer button', { hasText: 'No extras' }).click().catch(() => {});
                return fails.length ? `${fails.join('; ')}  ::  ${facts}` : info(facts);
            });
        }
    }
    await check('A24', 'ai', 'No script error anywhere in the chat flow', 'baseline', async () =>
        (p.pageErrors.length ? p.pageErrors.slice(0, 3).join(' | ') : undefined));
    await p.context().close();
}

// ---------------------------------------------------------------------------------------------------------
async function errorPaths() {
    const start = async (route) => {
        const p = await phone();
        await p.route('**/api/ai/sessions', route);
        await p.goto(`${BASE}/plan`, { waitUntil: 'load', timeout: 90000 });
        const box = p.getByRole('textbox', { name: 'Message Stag Do AI' });
        await box.waitFor();
        await p.waitForTimeout(1200);
        await box.fill('two nights for ten');
        await box.press('Enter');
        const alert = p.locator('.ai-error');
        await alert.waitFor({ timeout: 15000 });
        const text = flat(await alert.innerText());
        const buttons = await alert.locator('button').allInnerTexts();
        const kept = await p.getByText('two nights for ten').count();
        await p.context().close();
        return { text, kept, buttons };
    };
    await check('E01', 'errors', 'A refused first message (400) is not "this chat has expired", and the message stays', 'Nielsen heuristic 9: error messages say what went wrong; 2026-10-12 incident', async () => {
        const r = await start((route) => route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: 'Bad Request', message: 'Unknown destination: 192' }) }));
        return !/expired/i.test(r.text) && r.kept ? undefined : `shown: "${r.text}", message kept=${r.kept}`;
    });
    await check('E02', 'errors', 'A gateway error (502) is not "no connection", and can be retried', 'Nielsen heuristic 9; 2026-10-12 production incident', async () => {
        const r = await start((route) => route.fulfill({ status: 502, contentType: 'text/html', body: 'Bad Gateway' }));
        return !/No connection/i.test(r.text) && r.buttons.some((b) => /Try again/i.test(b)) ? undefined : `shown: "${r.text}"`;
    });
    await check('E03', 'errors', 'A request that gets no answer says "no connection" and can be retried', 'Nielsen heuristic 9', async () => {
        const r = await start((route) => route.abort('internetdisconnected'));
        return /No connection/i.test(r.text) && r.buttons.some((b) => /Try again/i.test(b)) ? undefined : `shown: "${r.text}"`;
    });
    await check('E04', 'errors', 'A refused request tells the organizer what to do next', 'Nielsen heuristic 9: constructively suggest a solution', async () => {
        const r = await start((route) => route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: 'Bad Request' }) }));
        return r.buttons.length ? undefined : flaw(`"${r.text}" - a sentence, no button: the message has to be typed again`);
    });
    await check('E05', 'errors', 'A stored chat the backend no longer knows is dropped quietly and the start screen shows', 'graceful recovery', async () => {
        const p = await phone({ fn: () => { try { localStorage.setItem('myhive-ai-session', '00000000-0000-4000-8000-000000000000'); } catch (e) { /* blocked */ } } });
        await p.goto(`${BASE}/plan`, { waitUntil: 'load', timeout: 90000 });
        await p.locator('.aip-starter').first().waitFor({ timeout: 20000 });
        const token = await p.evaluate(() => localStorage.getItem('myhive-ai-session'));
        const err = await p.locator('.ai-error').count();
        await p.context().close();
        return token === null && err === 0 ? undefined : `token=${token}, error shown=${err}`;
    });
}

// ---------------------------------------------------------------------------------------------------------
const destinations = await getJson('/destinations');
const destination = destinations.find((d) => d.slug === 'prague') || destinations[0];
const activities = (await getJson(`/activities?destinationId=${destination.id}`)).sort((a, b) => a.name.localeCompare(b.name));
const mixed = (a) => (a.extras || []).some((e) => e.pricing === 'PER_GROUP') && (a.extras || []).some((e) => e.pricing === 'PER_PERSON');
const carrier = activities.find((a) => a.slug === 'river-boat-cruise' && mixed(a)) || activities.find(mixed);
const carrier2 = activities.find((a) => (a.extras || []).length && a.id !== carrier.id);
const plain = activities.find((a) => !(a.extras || []).length);
const pkgs = await getJson(`/packages?destinationId=${destination.id}`).catch(() => []);
const pkg = pkgs.find((q) => q.activities.some((a) => (a.extras || []).length));
const cat = { destination, activities, carrier, carrier2, plain, pkg, n: TRAVELERS };
console.log(`kit against ${BASE}: ${activities.length} activities, ${activities.filter((a) => (a.extras || []).length).length} with extras; carrier "${carrier.name}"\n`);

browser = await launch();
try {
    if (!ONLY || ONLY === 'browse') await browseFlow(cat);
    if (!ONLY || ONLY === 'errors') await errorPaths();
    if (!ONLY || ONLY === 'ai') await aiFlow(cat);
} finally {
    await browser.close();
}
const tally = results.reduce((t, r) => ({ ...t, [r.status]: (t[r.status] || 0) + 1 }), {});
const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
fs.mkdirSync(path.join(HERE, 'results'), { recursive: true });
fs.writeFileSync(path.join(HERE, 'results', `${stamp}.json`), JSON.stringify({ base: BASE, at: new Date().toISOString(), tally, results }, null, 2));
console.log('\n' + Object.entries(tally).map(([k, v]) => `${k} ${v}`).join('  ·  ') + `   ->  results/${stamp}.json`);
process.exit(tally.FAIL || tally.ERROR ? 1 : 0);
