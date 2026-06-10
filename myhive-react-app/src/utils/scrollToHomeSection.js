const RETRY_INTERVAL_MS = 100;
const RETRY_DEADLINE_MS = 2000;

/**
 * Navigates to the homepage (if needed) and scrolls to a section anchor.
 * Retries briefly because some sections render only after their data loads;
 * gives up silently if the section never appears (e.g. nothing featured).
 */
export function scrollToHomeSection(navigate, sectionId) {
    navigate('/');
    const deadline = Date.now() + RETRY_DEADLINE_MS;
    const tryScroll = () => {
        const section = document.getElementById(sectionId);
        if (section) {
            section.scrollIntoView({behavior: 'smooth'});
        } else if (Date.now() < deadline) {
            setTimeout(tryScroll, RETRY_INTERVAL_MS);
        }
    };
    setTimeout(tryScroll, 0);
}
