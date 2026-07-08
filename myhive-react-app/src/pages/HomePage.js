import {Helmet} from 'react-helmet-async';
import {useNavigate} from 'react-router-dom';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import TripSetupModal from '../components/TripSetupModal';
import TrustBar from '../components/home/TrustBar';
import HowItWorksSection from '../components/home/HowItWorksSection';
import FeaturedActivitiesSection from '../components/home/FeaturedActivitiesSection';
import ReviewsSection from '../components/home/ReviewsSection';
import ContactCtaSection from '../components/home/ContactCtaSection';
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
                <title>Trivlu — The Easiest Stag Do Decision. All Sorted For You.</title>
                <meta name="description"
                      content="Your mates vote in 10 minutes. We deliver the perfect stag do weekend — activities, booking and logistics all sorted for you."/>
                <link rel="canonical" href={`${SITE_URL}/`}/>
            </Helmet>

            <section className="hero">
                <div className="hero-overlay"/>
                <div className="hero-fade" aria-hidden="true"/>
                <div className="hero-content">
                    <div className="hero-text">
                        <h1 className="hero-title">
                            <button
                                type="button"
                                className="hero-title-link"
                                onClick={() => {
                                    pushEvent('cta_click', {cta_label: 'Hero headline', block: 'hero'});
                                    openVoteSetup();
                                }}
                            >
                                The smartest way to plan a stag do
                            </button>
                        </h1>
                        <p className="hero-subtitle">
                            Your mates vote in 10 minutes. We deliver the perfect weekend.
                        </p>

                        <aside className="vote-card" aria-hidden="true">
                            <div className="vc-head">
                                <span className="vc-badge"><i className="ph ph-check-square"/></span>
                                <span className="vc-title">Vote on activities</span>
                            </div>
                            <div className="vc-sub">9 of 11 lads voted</div>
                            {[
                                {icon: 'ph-beer-stein', name: 'Bar Crawl', num: 8, pct: 89, fill: 'rgba(255,255,255,0.92)'},
                                {icon: 'ph-steering-wheel', name: 'Karting', num: 6, pct: 67, fill: 'rgba(255,255,255,0.65)'},
                                {icon: 'ph-target', name: 'Shooting', num: 5, pct: 56, fill: 'rgba(255,255,255,0.65)'},
                                {icon: 'ph-boat', name: 'Tiki Boat', num: 4, pct: 44, fill: 'rgba(255,255,255,0.65)'},
                            ].map((row) => (
                                <div className="vc-row" key={row.name}>
                                    <div className="vc-row-top">
                                        <span className="vc-name"><i className={`ph ${row.icon}`}/>{row.name}</span>
                                        <span className="vc-num">{row.num}</span>
                                    </div>
                                    <div className="vc-bar">
                                        <div className="vc-fill" style={{width: `${row.pct}%`, background: row.fill}}/>
                                    </div>
                                </div>
                            ))}
                        </aside>

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

            <TrustBar/>
            <HowItWorksSection onStartVote={openVoteSetup}/>
            <FeaturedActivitiesSection/>
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
