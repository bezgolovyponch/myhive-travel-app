'use client';

// The landing header's cart button. Same behaviour as the main header's
// (legacy-src/components/Header.js): the button toggles the shared
// TripBuilderDropdown, and the badge counts the real cart — not a list private
// to this page. Only the button's paint is the landing's; the panel below it is
// main's own component.
import { useTrip } from '../../legacy-src/context/TripContext';
import TripBuilderDropdown from '../../legacy-src/components/TripBuilderDropdown';
import { useT, useLocalePath } from '../../legacy-src/i18n';
import { VOTE_FLOW_PATH } from '../../lib/routes';

export default function LandingCart() {
  const { state, dispatch } = useTrip();
  const t = useT('landing.chrome');
  const lp = useLocalePath();
  const count = state.tripItems.length;

  return (
    // Relative, unlike the SPA's static wrapper: there the dropdown anchors to
    // the fixed .header-actions cluster, here the landing header is the full
    // width of the viewport, so without this the panel would open flush against
    // the screen edge instead of under the button.
    <div className="trip-builder-wrapper">
      <button
        className="hdr__cart"
        type="button"
        aria-label={t(count === 1 ? 'cartAriaOne' : 'cartAriaOther', { count })}
        onClick={() =>
          dispatch({
            type: state.tripBuilderModalOpen
              ? 'CLOSE_TRIP_BUILDER_MODAL'
              : 'OPEN_TRIP_BUILDER_MODAL',
          })
        }
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
        <span className="hdr__cart-count">{count > 0 ? count : ''}</span>
      </button>
      {/* voteHref, not the in-place setup modal: this is outside the SPA, where
          the modal's confirm cannot hand its payload to the quiz. */}
      <TripBuilderDropdown voteHref={lp(VOTE_FLOW_PATH)} />
    </div>
  );
}
