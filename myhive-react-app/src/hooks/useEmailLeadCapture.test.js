import {renderHook, act} from '@testing-library/react';
import {useEmailLeadCapture} from './useEmailLeadCapture';
import leadApi from '../services/leadApi';
import {readTripLead} from '../utils/tripLead';

jest.mock('../services/leadApi');

const CTX = {destinationId: 'd1', numberOfTravelers: 8, startDate: '2026-09-04', endDate: '2026-09-06', budget: null};

beforeEach(() => {
    jest.useFakeTimers();
    localStorage.clear();
    leadApi.createLead.mockResolvedValue({id: 'lead-1', restoreToken: 'tok'});
});
afterEach(() => jest.useRealTimers());

test('creates a lead 2s after a valid email, and stores it', async () => {
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@example.com'));
    act(() => jest.advanceTimersByTime(1999));
    expect(leadApi.createLead).not.toHaveBeenCalled();
    await act(async () => jest.advanceTimersByTime(1));
    expect(leadApi.createLead).toHaveBeenCalledWith({email: 'sam@example.com', ...CTX});
    expect(readTripLead()).toEqual({id: 'lead-1', restoreToken: 'tok'});
});

test('invalid email never fires; retyping resets the timer; same email not re-captured', async () => {
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@'));
    await act(async () => jest.advanceTimersByTime(3000));
    expect(leadApi.createLead).not.toHaveBeenCalled();

    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    expect(leadApi.createLead).toHaveBeenCalledTimes(1);
});

test('createLead rejection is silent', async () => {
    leadApi.createLead.mockRejectedValue(new Error('boom'));
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    expect(readTripLead()).toBeNull(); // no lead stored, no throw
});
