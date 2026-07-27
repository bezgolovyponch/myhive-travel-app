import {Helmet} from 'react-helmet-async';
import {useNavigate} from 'react-router-dom';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import TripSetupModal from '../components/TripSetupModal';
import TrustBar from '../components/home/TrustBar';
import HowItWorksSection from '../components/home/HowItWorksSection';
import FeaturedActivitiesSection from '../components/home/FeaturedActivitiesSection';
import ReviewsSection from '../components/home/ReviewsSection';
import ContactCtaSection from '../components/home/ContactCtaSection';
import VoteDemoCard from '../components/home/VoteDemoCard';
import {SITE_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import './HomePage.css';

function HomePage() {
    const navigate = useNavigate();
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();

    return (
        <div className="homepage">
            <Helmet>
                <title>Trivlu — The Easiest Prague Stag Do. All Sorted For You.</title>
                <meta name="description"
                      content="Your mates vote in 10 minutes. We deliver the perfect Prague stag do weekend — activities, booking and logistics all sorted for you."/>
                <link rel="canonical" href={`${SITE_URL}/`}/>
            </Helmet>

            <section className="hero">
                <div className="hero-overlay"/>
                <div className="hero-fade" aria-hidden="true"/>
                <div className="hero-content">
                    <div className="hero-text">
                        <h1 className="hero-title">The Easiest Prague Stag Do. All Sorted For You.</h1>
                        <p className="hero-subtitle">
                            Your mates vote in 10 minutes. We deliver the perfect weekend.
                        </p>

                        <VoteDemoCard/>

                        <div className="hero-cta-group">
                            <button
                                className="hp-btn-primary"
                                onClick={() => {
                                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'hero'});
                                    openVoteSetup();
                                }}
                            >
                                <i className="ph ph-check-square" aria-hidden="true"/> Start Group Vote
                            </button>
                            <a
                                className="hp-btn-secondary"
                                href="/#activities"
                                onClick={(e) => {
                                    e.preventDefault();
                                    pushEvent('cta_click', {cta_label: 'Explore activities', block: 'hero'});
                                    scrollToHomeSection(navigate, 'activities');
                                }}
                            >
                                Explore activities
                            </a>
                        </div>
                        <div className="hero-trust-line">
                            <span>You pick the vibe</span>
                            <span className="dot">·</span>
                            <span>Lads vote</span>
                            <span className="dot">·</span>
                            <span>We organise it</span>
                        </div>
                    </div>
                </div>
            </section>

            <FeaturedActivitiesSection/>
            <TrustBar/>
            <HowItWorksSection onStartVote={openVoteSetup}/>
            <ReviewsSection onStartVote={openVoteSetup}/>
            <ContactCtaSection/>

            <TripSetupModal
                isVoteMode={true}
                voteOpen={voteSetupOpen}
                onVoteConfirm={handleVoteConfirm}
                onVoteCancel={closeVoteSetup}
                preselectedDestination={preselectedDestination}
            />
        </div>
    );
}

export default HomePage;
