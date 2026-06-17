import {useEffect, useRef} from 'react';
import {Helmet} from 'react-helmet-async';
import {useStartGroupVote} from '../hooks/useStartGroupVote';
import TripSetupModal from '../components/TripSetupModal';
import TrustBar from '../components/home/TrustBar';
import HowItWorksSection from '../components/home/HowItWorksSection';
import FeaturedActivitiesSection from '../components/home/FeaturedActivitiesSection';
import HowBookingWorksSection from '../components/home/HowBookingWorksSection';
import ReviewsSection from '../components/home/ReviewsSection';
import {SITE_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import './HomePage.css';

function HomePage() {
    const videoRef = useRef(null);
    const {voteSetupOpen, openVoteSetup, closeVoteSetup, handleVoteConfirm, preselectedDestination} = useStartGroupVote();

    useEffect(() => {
        const video = videoRef.current;
        if (!video) {
            return;
        }
        video.play().catch(() => {});
    }, []);

    return (
        <div className="homepage">
            <Helmet>
                <title>Trivlu — The Easiest Stag Do Decision. All Sorted For You.</title>
                <meta name="description"
                      content="Your mates vote in 10 minutes. We deliver the perfect stag do weekend — activities, booking and logistics all sorted for you."/>
                <link rel="canonical" href={`${SITE_URL}/`}/>
            </Helmet>

            <section className="hero">
                <video ref={videoRef} autoPlay muted loop playsInline className="hero-video">
                    <source src="https://res.cloudinary.com/dfhvltbjz/video/upload/ac_none,q_auto/v1758716526/panorama_sqshpf.mp4" type="video/mp4"/>
                    Your browser does not support the video tag.
                </video>
                <div className="hero-content">
                    <h1 className="hero-title">The Easiest Stag Do Decision. All Sorted For You.</h1>
                    <p className="hero-subtitle">
                        Your mates vote in 10 minutes. We deliver the perfect weekend.
                    </p>
                    <button
                        className="btn btn--primary btn--lg"
                        onClick={() => {
                            pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'hero'});
                            openVoteSetup();
                        }}
                    >
                        Start Group Vote
                    </button>
                </div>
            </section>

            <TrustBar/>
            <HowItWorksSection onStartVote={openVoteSetup}/>
            <FeaturedActivitiesSection/>
            <HowBookingWorksSection/>
            <ReviewsSection onStartVote={openVoteSetup}/>

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
