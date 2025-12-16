import { useContext } from 'react';
import { AppContext } from '../context/AppContext';
import DestinationCard from '../components/DestinationCard';

function HomePage() {
  const { state } = useContext(AppContext);

  if (state.loading) {
    return (
      <div className="homepage">
        <section className="hero">
          <video autoPlay muted loop playsInline className="hero-video">
            <source src="https://res.cloudinary.com/dfhvltbjz/video/upload/v1758716526/panorama_sqshpf.mp4" type="video/mp4" />
            Your browser does not support the video tag.
          </video>
          <div className="hero-content">
            <h1 className="hero-title">Epic Weekend of Freedom</h1>
            <p className="hero-subtitle">
              Turn group travel chaos into epic adventures with zero stress using first AI trip maker for multi-traveler experiences
            </p>
          </div>
        </section>
        <section className="destinations-section" id="destinations">
          <h2 className="section-title">Loading destinations...</h2>
        </section>
      </div>
    );
  }

  if (state.error) {
    return (
      <div className="homepage">
        <section className="destinations-section" id="destinations">
          <h2 className="section-title">Error loading destinations</h2>
          <p style={{ textAlign: 'center', color: 'var(--color-error)' }}>{state.error}</p>
        </section>
      </div>
    );
  }

  return (
    <div className="homepage">
      <section className="hero">
        <video autoPlay muted loop playsInline className="hero-video">
          <source src="https://res.cloudinary.com/dfhvltbjz/video/upload/v1758716526/panorama_sqshpf.mp4" type="video/mp4" />
          Your browser does not support the video tag.
        </video>
        <div className="hero-content">
          <h1 className="hero-title">Epic Weekend of Freedom</h1>
          <p className="hero-subtitle">
            Turn group travel chaos into epic adventures with zero stress using first AI trip maker for multi-traveler experiences
          </p>
          <a href="#destinations" className="btn btn--primary btn--lg">
            Explore Destinations
          </a>
        </div>
      </section>

      <section className="destinations-section" id="destinations">
        <h2 className="section-title">Destinations</h2>
        <div className="destinations-grid">
          {state.destinations.map(destination => (
            <DestinationCard key={destination.id} destination={destination} />
          ))}
        </div>
      </section>
    </div>
  );
}

export default HomePage;
