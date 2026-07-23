import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter, useLocation} from 'react-router-dom';
import {TripProvider, useTrip} from '../context/TripContext';
import useTripDeepLink from './useTripDeepLink';
import api from '../services/api';

jest.mock('../services/api');

function Probe() {
    useTripDeepLink();
    const {state} = useTrip();
    const location = useLocation();
    return (
        <>
            <div data-testid="items">{state.tripItems.map(i => i.id).join(',')}</div>
            <div data-testid="search">{location.search}</div>
        </>
    );
}

function renderAt(url) {
    return render(
        <TripProvider>
            <MemoryRouter initialEntries={[url]}>
                <Probe/>
            </MemoryRouter>
        </TripProvider>
    );
}

beforeEach(() => {
    localStorage.clear();
    jest.resetAllMocks();
});

test('adds the activity from ?add= and strips the param', async () => {
    api.getActivityBySlug.mockResolvedValue({id: 'a1', name: 'Karting', price: 50, slug: 'karting'});
    renderAt('/destination/prague?tab=trip-builder&add=karting');
    await waitFor(() => expect(screen.getByTestId('items')).toHaveTextContent('a1'));
    expect(api.getActivityBySlug).toHaveBeenCalledWith('karting');
    expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder');
});

test('adds the package from ?addPackage= and strips the param', async () => {
    api.getPackageBySlug.mockResolvedValue({
        id: 'p1', name: 'Full Weekend', discountPct: 20, destinationSlug: 'prague',
        activities: [{activityId: 'a1', name: 'Karting', price: 50, imageUrl: '', duration: 60}],
    });
    renderAt('/destination/prague?tab=trip-builder&addPackage=full-weekend');
    await waitFor(() => expect(screen.getByTestId('items')).toHaveTextContent('a1'));
    expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder');
});

test('does nothing without the params', () => {
    renderAt('/destination/prague?tab=trip-builder');
    expect(api.getActivityBySlug).not.toHaveBeenCalled();
    expect(screen.getByTestId('items').textContent).toBe('');
});

test('a fetch failure still strips the param', async () => {
    api.getActivityBySlug.mockRejectedValue(new Error('backend down'));
    renderAt('/destination/prague?tab=trip-builder&add=karting');
    await waitFor(() => expect(screen.getByTestId('search').textContent).toBe('?tab=trip-builder'));
    expect(screen.getByTestId('items').textContent).toBe('');
});
