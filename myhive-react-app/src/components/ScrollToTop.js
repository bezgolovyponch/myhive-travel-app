import {useEffect} from 'react';
import {useLocation} from 'react-router-dom';

// React Router keeps the previous scroll position on navigation, so a page
// opened from a scrolled-down list would start mid-page. Reset to the top on
// every pathname change (query-string changes like ?tab= keep the position).
function ScrollToTop() {
    const {pathname} = useLocation();

    useEffect(() => {
        window.scrollTo(0, 0);
    }, [pathname]);

    return null;
}

export default ScrollToTop;
