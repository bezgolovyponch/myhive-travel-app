import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { captureFromUrl } from '../utils/attribution';

// Captures UTM/ref attribution on first load and on every SPA navigation.
// captureFromUrl is idempotent and only overwrites on non-direct visits, so
// re-running on each navigation is safe. Renders nothing.
function AttributionCapture() {
  const { search } = useLocation();
  useEffect(() => {
    captureFromUrl(search, document.referrer);
  }, [search]);
  return null;
}

export default AttributionCapture;
