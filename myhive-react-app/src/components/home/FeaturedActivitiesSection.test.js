import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import FeaturedActivitiesSection from './FeaturedActivitiesSection';
import api from '../../services/api';
import {CatalogContext} from '../../context/CatalogContext';
import {TripContext} from '../../context/TripContext';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../services/api');
jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
    jest.clearAllMocks();
});

const baseCatalogState = {
    destinations: [{id: 'd1', slug: 'prague', name: 'Prague'}],
    loading: false,
    error: null,
};

const baseTripState = {
    tripItems: [],
};

function renderSection(catalogState = baseCatalogState, tripState = baseTripState) {
    return render(
        <CatalogContext.Provider value={{state: catalogState, dispatch: jest.fn()}}>
            <TripContext.Provider value={{state: tripState, dispatch: jest.fn()}}>
                <MemoryRouter>
                    <FeaturedActivitiesSection/>
                </MemoryRouter>
            </TripContext.Provider>
        </CatalogContext.Provider>
    );
}

test('clicking "View All Activities" fires cta_click with block activities', async () => {
    const user = userEvent.setup();
    api.getFeaturedActivities.mockResolvedValue([
        {id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: []},
    ]);

    renderSection();

    const link = await screen.findByRole('link', {name: 'View All Activities'});
    await user.click(link);

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'View All Activities', block: 'activities'});
});

test('falls back to regular activities when nothing is featured', async () => {
    api.getFeaturedActivities.mockResolvedValue([]);
    api.getActivities.mockResolvedValue([
        {id: 'a2', name: 'Shooting', price: 80, slug: 'shooting', destinationSlug: 'prague', categories: []},
    ]);

    renderSection();

    expect(await screen.findByText('Shooting')).toBeInTheDocument();
    expect(api.getActivities).toHaveBeenCalledTimes(1);
});

test('falls back to regular activities when the featured fetch fails', async () => {
    api.getFeaturedActivities.mockRejectedValue(new Error('boom'));
    api.getActivities.mockResolvedValue([
        {id: 'a2', name: 'Shooting', price: 80, slug: 'shooting', destinationSlug: 'prague', categories: []},
    ]);

    renderSection();

    expect(await screen.findByText('Shooting')).toBeInTheDocument();
});

test('does not fetch the fallback when featured activities exist', async () => {
    api.getFeaturedActivities.mockResolvedValue([
        {id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: []},
    ]);

    renderSection();

    expect(await screen.findByText('Go-Karting')).toBeInTheDocument();
    expect(api.getActivities).not.toHaveBeenCalled();
});

test('pushEvent does not fire on render', async () => {
    api.getFeaturedActivities.mockResolvedValue([
        {id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: []},
    ]);

    renderSection();

    await screen.findByRole('link', {name: 'View All Activities'});

    expect(pushEvent).not.toHaveBeenCalled();
});
