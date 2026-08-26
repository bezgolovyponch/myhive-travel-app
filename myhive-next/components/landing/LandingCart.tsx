'use client';

// The landing header's cart, behaving like the main header's: the badge counts
// the real cart, clicking toggles a panel listing it, and Continue carries the
// visitor into the trip builder with those activities already there.
//
// The panel is the landing's own markup rather than the SPA's
// TripBuilderDropdown: that component's styles reach for main's class names and
// token vocabulary (--surface/--text/--border), which the landings deliberately
// do not load. The state, the reducer actions and the pricing helpers are the
// shared ones, so behaviour cannot drift — only the paint is local.
//
// TripSetupModal, by contrast, IS the shared component: it owns the
// travelers/dates form, its draft persistence and the tb_start /
// tb_group_submitted analytics, none of which should exist twice. It is
// portalled to <body> so it inherits main's :root tokens instead of the
// landing's .tl overrides.
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import dynamic from 'next/dynamic';
import { useTrip } from '../../legacy-src/context/TripContext';
import { computeTripTotal } from '../../legacy-src/utils/tripPricing';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { navigateAfterEvents } from '../../legacy-src/utils/analytics';
import { VOTE_FLOW_PATH } from '../../lib/routes';
import { builderUrl } from './data';

// Loaded on demand: the modal pulls in the date picker (react-day-picker), which
// no visitor needs in the initial payload of an ad landing page — it cannot
// appear before a click. ssr:false for the same reason; it renders null closed.
const TripSetupModal = dynamic(() => import('../../legacy-src/components/TripSetupModal'), {
  ssr: false,
});

interface TripItem {
  id: string;
  name: string;
  price: number;
  minPrice?: number | null;
}

export default function LandingCart({ destinationSlug }: { destinationSlug: string }) {
  const { state, dispatch } = useTrip();
  const t = useT('tripDropdown');
  const tChrome = useT('landing.chrome');
  const lp = useLocalePath();
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  const items = state.tripItems as TripItem[];
  const travelers = state.tripTravelers || 1;
  const builderHref = lp(builderUrl(destinationSlug));

  const openPanel = () =>
    dispatch({
      type: state.tripBuilderModalOpen ? 'CLOSE_TRIP_BUILDER_MODAL' : 'OPEN_TRIP_BUILDER_MODAL',
    });

  return (
    <div className="lcart">
      <button
        className="hdr__cart"
        type="button"
        aria-label={tChrome(items.length === 1 ? 'cartAriaOne' : 'cartAriaOther', {
          count: items.length,
        })}
        aria-expanded={state.tripBuilderModalOpen ? 'true' : 'false'}
        onClick={openPanel}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <circle cx="9" cy="20" r="1" />
          <circle cx="19" cy="20" r="1" />
          <path d="M3 4h2l2.4 10.4a2 2 0 0 0 2 1.6h7.7a2 2 0 0 0 2-1.6L21 8H6" />
        </svg>
        <span className="hdr__cart-count">{items.length > 0 ? items.length : ''}</span>
      </button>

      {state.tripBuilderModalOpen && (
        <div className="lcart__panel">
          <div className="lcart__hd">
            <h3>{t('title')}</h3>
            <button
              type="button"
              className="lcart__x"
              aria-label={t('closeAria')}
              onClick={() => dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' })}
            >
              ×
            </button>
          </div>

          {items.length ? (
            <>
              <ul className="lcart__list">
                {items.map((i) => (
                  <li key={i.id}>
                    <span className="lcart__nm">{i.name}</span>
                    <span className="lcart__pr">€{i.price}</span>
                    <button
                      type="button"
                      className="lcart__rm"
                      aria-label={t('removeAria', { name: i.name })}
                      onClick={() => dispatch({ type: 'REMOVE_FROM_TRIP', activityId: i.id })}
                    >
                      ×
                    </button>
                  </li>
                ))}
              </ul>
              <div className="lcart__ft">
                <span>
                  {t(travelers === 1 ? 'totalOne' : 'totalOther', {
                    count: travelers,
                  })}
                  {': '}
                  <strong>€{computeTripTotal(items, travelers)}</strong>
                </span>
                <a
                  className="btn btn--primary btn--block"
                  href={builderHref}
                  onClick={(e) => {
                    e.preventDefault();
                    dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
                    navigateAfterEvents(builderHref);
                  }}
                >
                  {t('continue')}
                </a>
              </div>
            </>
          ) : (
            <div className="lcart__empty">
              <p>{t('emptyState')}</p>
              {/* Same handoff as everywhere else outside the SPA: the vote
                  funnel is entered by URL, never by a modal whose confirm
                  would lose its payload to the full page load. */}
              <a className="btn btn--primary btn--block" href={lp(VOTE_FLOW_PATH)}>
                {t('voteTogether')}
              </a>
            </div>
          )}
        </div>
      )}

      {mounted &&
        createPortal(
          <TripSetupModal
            clearOnCancel={false}
            // Confirming with an empty cart has nothing to show in the panel the
            // reducer opens, so continue to the builder instead.
            onTripConfirm={() => {
              if (items.length === 0) navigateAfterEvents(builderHref);
            }}
          />,
          document.body,
        )}
    </div>
  );
}
