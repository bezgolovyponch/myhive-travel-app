// Typed bridge to the app's one analytics module (legacy-src/utils/analytics).
// Same dataLayer contract as every other page: cta_click with a human label
// and the block the button sits in.
import { pushEvent, navigateAfterEvents } from '../../legacy-src/utils/analytics';

export function trackCta(label: string, block: string): void {
  pushEvent('cta_click', { cta_label: label, block });
}

// For CTAs that leave the page: push the event, then navigate after a bounded
// flush delay so GTM's async container can dispatch it.
export function trackCtaAndGo(label: string, block: string, href: string): void {
  trackCta(label, block);
  navigateAfterEvents(href);
}
