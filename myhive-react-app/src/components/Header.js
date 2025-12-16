import { Link } from 'react-router-dom';

function Header() {
  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">MyHive</Link>
        <nav className="nav-links">
          <Link to="/">Destinations</Link>
          <a 
            href="#" 
            onClick={(e) => { e.preventDefault(); alert('About page coming soon!'); }}
          >
            About
          </a>
          <a 
            href="#" 
            onClick={(e) => { e.preventDefault(); alert('Contact page coming soon!'); }}
          >
            Contact
          </a>
        </nav>
        <button className="trip-builder-btn">TRIP BUILDER</button>
      </div>
    </header>
  );
}

export default Header;
