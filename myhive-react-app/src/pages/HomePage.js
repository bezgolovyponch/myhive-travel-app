import PageHead from '../components/PageHead';
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
import HeroPlanner from '../components/home/HeroPlanner';
import StickyVoteCta from '../components/home/StickyVoteCta';
import {SITE_URL, STICKY_VOTE_CTA_ENABLED} from '../services/config';
import {useT} from '../i18n';
import './HomePage.css';

// `featuredActivities` exists only for the server-rendered copy of this page
// (Next.js Ф1) — forwarded to the grid so it reaches the initial HTML instead
// of arriving in an effect no crawler runs. The SPA omits it.
//
// "Start Group Vote" opens the setup modal in place on both mounts: the confirm
// handler carries the setup through /vote/new's query string (useStartGroupVote),
// which survives the full page load a server-rendered mount makes.
function HomePage({featuredActivities}) {
    const tMeta = useT('meta');
    const {state: catalog} = useCatalog();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();
    const startVote = openVoteSetup;
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

            <section className="hero hero--planner">
                <div className="hero-overlay"/>
                <div className="hero-fade" aria-hidden="true"/>
                <div className="hero-content">
                    <div className="hero-text">
                        {/* v3 landing 2a: dates and head-count, then the planner chat. */}
                        <HeroPlanner explorePath={`/destination/${exploreActivitiesSlug}?tab=activities`}/>
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
