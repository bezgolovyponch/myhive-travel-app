import {useEffect, useRef} from 'react';
import leadApi from '../services/leadApi';
import {writeTripLead} from '../utils/tripLead';

const CAPTURE_DEBOUNCE_MS = 2000;
const EMAIL_RE = /\S+@\S+\.\S+/;

/**
 * Debounced lead capture at an email input: once a valid address sits
 * unchanged for 2s, create the lead so an abandoner still gets the reminder
 * flow. Fire-and-forget; each distinct address is captured at most once
 * (the server also dedups by email).
 *
 * The returned function also carries a `.cancel()` method (call sites that
 * only invoke it directly are unaffected) — callers that transition to a
 * state where a just-armed timer must not fire (e.g. a booking just
 * completed and cleared the lead) call `.cancel()` to stop it, since the
 * component that armed the timer may not unmount on that transition.
 */
export function useEmailLeadCapture(context) {
  const timerRef = useRef(null);
  const capturedRef = useRef(null);
  const contextRef = useRef(context);
  contextRef.current = context;

  useEffect(() => () => clearTimeout(timerRef.current), []);

  const capture = (email) => {
    clearTimeout(timerRef.current);
    const trimmed = (email || '').trim();
    if (!EMAIL_RE.test(trimmed) || capturedRef.current === trimmed) {
      return;
    }
    timerRef.current = setTimeout(() => {
      capturedRef.current = trimmed;
      leadApi.createLead({email: trimmed, ...contextRef.current})
        .then(writeTripLead)
        .catch(() => {
          capturedRef.current = null; // allow a retry on the next keystroke
        });
    }, CAPTURE_DEBOUNCE_MS);
  };

  capture.cancel = () => clearTimeout(timerRef.current);

  return capture;
}
