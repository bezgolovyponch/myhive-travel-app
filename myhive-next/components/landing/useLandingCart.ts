'use client';

// The landings' shortlist IS the site's trip cart. Before this, each landing
// kept a private list in a reducer, so the header badge, the swipe deck and the
// trip builder all disagreed and the picks had to be smuggled through the URL.
//
// Adds are silent: ADD_TO_TRIP would otherwise pop the travelers/dates modal on
// the first one, and on the vote landing that lands mid-swipe, halfway through a
// deck the visitor is still flicking through. The modal is opened deliberately
// by the page's CTAs instead (openSetup).
import { useCallback, useMemo } from 'react';
import { useTrip } from '../../legacy-src/context/TripContext';
import { toCartItem, type LandingActivity } from './data';

export interface LandingCart {
  /** Slugs in the cart, so the existing picked-based props keep working. */
  picked: string[];
  count: number;
  add: (slug: string) => void;
  toggle: (slug: string) => void;
  openSetup: () => void;
}

export function useLandingCart(
  destinationSlug: string,
  activities: LandingActivity[],
): LandingCart {
  const { state, dispatch } = useTrip();

  const bySlug = useMemo(() => {
    const map = new Map<string, LandingActivity>();
    for (const a of activities) map.set(a.slug, a);
    return map;
  }, [activities]);

  // Cart items carry the slug the landing UI keys on; a cart restored from an
  // older build may predate it, so fall back to the id.
  const picked = useMemo(
    () => (state.tripItems as { slug?: string; id: string }[]).map((i) => i.slug ?? i.id),
    [state.tripItems],
  );

  const add = useCallback(
    (slug: string) => {
      const activity = bySlug.get(slug);
      if (!activity) return;
      dispatch({
        type: 'ADD_TO_TRIP',
        activity: toCartItem(activity, destinationSlug),
        silent: true,
      });
    },
    [bySlug, destinationSlug, dispatch],
  );

  const toggle = useCallback(
    (slug: string) => {
      const activity = bySlug.get(slug);
      if (!activity) return;
      if (picked.includes(slug)) {
        dispatch({ type: 'REMOVE_FROM_TRIP', activityId: activity.id });
        return;
      }
      add(slug);
    },
    [add, bySlug, dispatch, picked],
  );

  const openSetup = useCallback(() => dispatch({ type: 'OPEN_TRIP_SETUP' }), [dispatch]);

  return { picked, count: state.tripItems.length, add, toggle, openSetup };
}
