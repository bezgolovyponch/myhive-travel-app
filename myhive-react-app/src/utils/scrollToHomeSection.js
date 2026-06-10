/**
 * Navigates to the homepage (if needed) and scrolls to a section anchor.
 * The timeout lets the homepage mount before the scroll target is queried;
 * if the section isn't rendered (e.g. no featured activities), it's a no-op.
 */
export function scrollToHomeSection(navigate, sectionId) {
    navigate('/');
    setTimeout(() => {
        const section = document.getElementById(sectionId);
        if (section) {
            section.scrollIntoView({behavior: 'smooth'});
        }
    }, 0);
}
