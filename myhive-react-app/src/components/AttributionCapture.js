import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { captureFromUrl, captureFirstTouch } from '../utils/attribution';

// Captures UTM/ref attribution on first load and on every SPA navigation.
// captureFromUrl is idempotent and only overwrites on non-direct visits, so
// re-running on each navigation is safe. captureFirstTouch is a no-op after
// the first-ever call, so it's equally safe to re-run. Renders nothing.
function AttributionCapture() {
  const { search } = useLocation();
  useEffect(() => {
    captureFromUrl(search, document.referrer);
    captureFirstTouch(search, document.referrer);
  }, [search]);
  return null;
}

export default AttributionCapture;
