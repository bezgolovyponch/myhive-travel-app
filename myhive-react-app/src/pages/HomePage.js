import { useContext } from 'react';
import { AppContext } from '../context/AppContext';
import DestinationCard from '../components/DestinationCard';

function HomePage() {
  const { state } = useContext(AppContext);

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
