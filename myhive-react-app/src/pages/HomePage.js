import {useContext, useEffect, useRef} from 'react';
import {Helmet} from 'react-helmet-async';
import {AppContext} from '../context/AppContext';
import DestinationCard from '../components/DestinationCard';
import {SITE_URL} from '../services/config';
import './HomePage.css';

function HomePage() {
  const { state } = useContext(AppContext);
  const videoRef = useRef(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.play().catch(() => {});
  }, []);

  return (
    <div className="homepage">
        <Helmet>
            <title>Trivlu — Group Travel Made Easy</title>
            <meta name="description"
                  content="Turn group travel chaos into epic adventures with zero stress. Trivlu is the first AI trip maker for multi-traveler experiences."/>
            <link rel="canonical" href={`${SITE_URL}/`}/>
        </Helmet>
      <section className="hero">
        <video ref={videoRef} autoPlay muted loop playsInline className="hero-video">
          <source src="https://res.cloudinary.com/dfhvltbjz/video/upload/ac_none,q_auto/v1758716526/panorama_sqshpf.mp4" type="video/mp4" />
          Your browser does not support the video tag.
        </video>
        <div className="hero-content">
          <h1 className="hero-title">Epic Weekend of Freedom</h1>
          <p className="hero-subtitle">
            Turn group travel chaos into epic adventures with zero stress using first AI trip maker for multi-traveler experiences
          </p>
            {!state.loading && !state.error && (
                <a href="#destinations" className="btn btn--primary btn--lg">
                    Explore Destinations
                </a>
            )}
        </div>
      </section>

      <section className="destinations-section" id="destinations">
          {state.loading ? (
              <h2 className="section-title">Loading destinations...</h2>
          ) : state.error ? (
              <>
                  <h2 className="section-title">Error loading destinations</h2>
                  <p className="text-center text-error">{state.error}</p>
              </>
          ) : (
              <>
                  <h2 className="section-title">Destinations</h2>
                  <div className="destinations-grid">
                      {state.destinations.map(destination => (
                          <DestinationCard key={destination.id} destination={destination}/>
                      ))}
                  </div>
              </>
          )}
      </section>
    </div>
  );
}

export default HomePage;
