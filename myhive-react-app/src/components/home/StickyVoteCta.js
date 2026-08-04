import {useEffect, useState} from 'react';
import {pushEvent} from '../../utils/analytics';
import './StickyVoteCta.css';

// Mobile-only bottom bar that repeats the hero CTA once it scrolls away.
function StickyVoteCta({onStartVote, heroSelector = '.hero-cta-group', hidden = false}) {
    const [heroGone, setHeroGone] = useState(false);

    useEffect(() => {
        const hero = document.querySelector(heroSelector);
        if (!hero || !window.IntersectionObserver) {
            return undefined;
        }
        const observer = new IntersectionObserver(
            (entries) => setHeroGone(!entries[0].isIntersecting)
        );
        observer.observe(hero);
        return () => observer.disconnect();
    }, [heroSelector]);

    const visible = heroGone && !hidden;

    useEffect(() => {
        document.body.classList.toggle('homepage-has-sticky-cta', visible);
        return () => document.body.classList.remove('homepage-has-sticky-cta');
    }, [visible]);

    if (!visible) {
        return null;
    }
    return (
        <div className="sticky-vote-cta">
            <button
                className="btn btn--primary btn--full-width"
                onClick={() => {
                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'sticky_mobile'});
                    onStartVote();
                }}
            >
                <i className="ph ph-check-square" aria-hidden="true"/> Start Group Vote
            </button>
        </div>
    );
}

export default StickyVoteCta;
