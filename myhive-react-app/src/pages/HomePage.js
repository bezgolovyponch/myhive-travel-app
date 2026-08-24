import PageHead from '../components/PageHead';
import {useNavigate} from 'react-router-dom';
import {useCatalog} from '../context/CatalogContext';
import {getDefaultDestination} from '../utils/defaultDestination';
import {DEFAULT_DESTINATION_SLUG} from '../services/config';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import TripSetupModal from '../components/TripSetupModal';
import TrustBar from '../components/home/TrustBar';
import HowItWorksSection from '../components/home/HowItWorksSection';
import FeaturedActivitiesSection from '../components/home/FeaturedActivitiesSection';
import ReviewsSection from '../components/home/ReviewsSection';
import ContactCtaSection from '../components/home/ContactCtaSection';
import VoteDemoCard from '../components/home/VoteDemoCard';
import StickyVoteCta from '../components/home/StickyVoteCta';
import {SITE_URL, STICKY_VOTE_CTA_ENABLED} from '../services/config';
import {pushEvent, navigateAfterEvents} from '../utils/analytics';
import {useLocalePath, useT} from '../i18n';
import './HomePage.css';

// Two props exist only for the server-rendered copy of this page (Next.js Ф1);
// both are omitted in the SPA, which behaves exactly as before.
//
// `featuredActivities` — forwarded to the grid so it reaches the initial HTML
// instead of arriving in an effect no crawler runs.
//
// `voteHref` — where the "Start Group Vote" CTAs go instead of opening the setup
// modal in place. The modal's confirm handler hands the setup to /vote/new/quiz
// through react-router location state, which cannot survive the full page load
// that leaving a server-rendered page requires; QuizPage bounces to '/' without
// it. /vote/new exists for exactly this reason: it mounts the SPA and opens the
// same modal, so the funnel stays intact. Analytics are unaffected — every CTA
// still fires its cta_click before navigating.
function HomePage({featuredActivities, voteHref}) {
    const t = useT('home');
    const tMeta = useT('meta');
    const lp = useLocalePath();
    const navigate = useNavigate();
    const {state: catalog} = useCatalog();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();
    // navigateAfterEvents, not a bare assign: the cta_click pushed immediately
    // before this must survive leaving the document.
    const startVote = voteHref ? () => navigateAfterEvents(voteHref) : openVoteSetup;
    // "Explore activities" goes to the catalog — the default destination's
    // activities listing — rather than scrolling to the homepage teaser.
    const exploreActivitiesSlug =
        getDefaultDestination(catalog.destinations)?.slug || DEFAULT_DESTINATION_SLUG;

    return (
        <div className="homepage">
            <PageHead>
                <title>{tMeta('home.title')}</title>
                <meta name="description" content={tMeta('home.description')}/>
                <link rel="canonical" href={`${SITE_URL}/`}/>
            </PageHead>

            <section className="hero">
                <div className="hero-overlay"/>
                <div className="hero-fade" aria-hidden="true"/>
                <div className="hero-content">
                    <div className="hero-text">
                        <h1 className="hero-title">{t('hero.title')}</h1>
                        <p className="hero-subtitle">
                            {t('hero.subtitle')}
                        </p>

                        <VoteDemoCard/>

                        <div className="hero-cta-group">
                            <button
                                className="hp-btn-primary"
                                onClick={() => {
                                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'hero'});
                                    startVote();
                                }}
                            >
                                <i className="ph ph-check-square" aria-hidden="true"/> {t('hero.startVoteCta')}
                            </button>
                            <a
                                className="hp-btn-secondary"
                                href={lp(`/destination/${exploreActivitiesSlug}?tab=activities`)}
                                onClick={(e) => {
                                    e.preventDefault();
                                    pushEvent('cta_click', {cta_label: 'Explore activities', block: 'hero'});
                                    navigate(`/destination/${exploreActivitiesSlug}?tab=activities`);
                                }}
                            >
                                {t('hero.exploreCta')}
                            </a>
                        </div>
                    </div>
                </div>
            </section>

            <FeaturedActivitiesSection activities={featuredActivities}/>
            <HowItWorksSection onStartVote={startVote}/>
            <TrustBar/>
            <ReviewsSection onStartVote={startVote}/>
            <ContactCtaSection/>

            <TripSetupModal
                isVoteMode={true}
                voteOpen={voteSetupOpen}
                onVoteConfirm={handleVoteConfirm}
                onVoteCancel={closeVoteSetup}
                preselectedDestination={preselectedDestination}
            />

            {STICKY_VOTE_CTA_ENABLED && <StickyVoteCta onStartVote={startVote} hidden={voteSetupOpen}/>}
        </div>
    );
}

export default HomePage;
