'use client';

// Server-rendered header for the SSR public pages — markup/classes mirror
// legacy-src/components/Header.js so the existing CSS applies unchanged.
// Interactive Trip Builder state lives in the legacy SPA; the button here
// full-page-navigates into an SPA-owned URL (?tab=trip-builder escape hatch).
import Link from 'next/link';
import { useState } from 'react';
import '../../legacy-src/components/Header.css';

const TRIP_BUILDER_URL = '/destination/prague?tab=trip-builder';

export default function Header() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const close = () => setMobileNavOpen(false);

  return (
    <header className="header header--transparent">
      <div className="header-content">
        <Link href="/" className="logo">
          <img src="/logo-trivlu.svg?v=4" alt="Trivlu" className="logo-img" />
        </Link>
        <nav className={`nav-links ${mobileNavOpen ? 'nav-open' : ''}`}>
          <a href="/#activities" onClick={close}>Activities</a>
          <Link href="/about" onClick={close}>About</Link>
          <Link href="/blog" onClick={close}>Blog</Link>
          <Link href="/contact" onClick={close}>Contact</Link>
        </nav>
        <div className="trip-builder-wrapper">
          <a className="trip-builder-btn" href={TRIP_BUILDER_URL}>
            TRIP BUILDER
          </a>
        </div>
        <button
          type="button"
          className="hamburger-btn"
          aria-label="Menu"
          aria-expanded={mobileNavOpen}
          onClick={() => setMobileNavOpen(!mobileNavOpen)}
        >
          <span className={`hamburger-icon ${mobileNavOpen ? 'open' : ''}`} />
        </button>
      </div>
    </header>
  );
}
