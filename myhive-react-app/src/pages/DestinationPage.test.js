import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {TripContext} from '../context/TripContext';
import {CatalogContext} from '../context/CatalogContext';
import DestinationPage from './DestinationPage';
import api from '../services/api';

jest.mock('../services/api');

beforeEach(() => {
    // jsdom implements neither matchMedia nor ResizeObserver; the TripBuilder
    // tab (rendered hidden inside DestinationPage) uses both for layout sync.
    window.matchMedia = jest.fn().mockReturnValue({
        matches: false,
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
    });
    global.ResizeObserver = jest.fn().mockImplementation(() => ({
        observe: jest.fn(),
        unobserve: jest.fn(),
        disconnect: jest.fn(),
    }));
});

const tripState = {
    tripItems: [],
};

// TripBuilder (mounted on the trip-builder tab) reads the catalog via useCatalog.
const catalogValue = {
    state: {destinations: [], loading: false, error: null},
};

function renderPage(initialEntry = '/destination/prague') {
    return render(
        <HelmetProvider>
            <CatalogContext.Provider value={catalogValue}>
                <TripContext.Provider value={{state: tripState, dispatch: jest.fn()}}>
                    <MemoryRouter initialEntries={[initialEntry]}>
                        <Routes>
                            <Route path="/destination/:slug" element={<DestinationPage/>}/>
                        </Routes>
                    </MemoryRouter>
                </TripContext.Provider>
            </CatalogContext.Provider>
        </HelmetProvider>
    );
}

function mockHappyPathApi() {
    api.getDestinationBySlug.mockResolvedValue({id: 'd1', name: 'Prague', slug: 'prague'});
    api.getCategoriesForDestination.mockResolvedValue([]);
    api.getPackagesByDestination.mockResolvedValue([]);
    api.getActivities.mockResolvedValue([]);
    api.getActivitiesPaged.mockResolvedValue({content: [], totalElements: 0, last: true});
}

test('a failed filter change shows an inline error, not "Destination not found"', async () => {
    api.getDestinationBySlug.mockResolvedValue({id: 'd1', name: 'Prague', slug: 'prague'});
    api.getCategoriesForDestination.mockResolvedValue([{slug: 'food', name: 'food'}]);
    api.getPackagesByDestination.mockResolvedValue([]);
    // The hidden TripBuilder tab fetches the destination's browse activities.
    api.getActivities.mockResolvedValue([]);
    api.getActivitiesPaged
        .mockResolvedValueOnce({content: [], totalElements: 0, last: true})
        .mockRejectedValueOnce(new Error('network'));

    renderPage();
    // The tab nav is the first content now (no destination hero/title). Its
    // presence stands in for "the page rendered / survived".
    expect(await screen.findByRole('button', {name: 'Activities'})).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Food'}));

    expect(await screen.findByText(/couldn't load activities/i)).toBeInTheDocument();
    // The page itself must survive the list failure
    expect(screen.getByRole('button', {name: 'Activities'})).toBeInTheDocument();
    expect(screen.queryByText('Destination not found')).not.toBeInTheDocument();
});

test('the tab nav is hidden on the trip-builder (checkout) tab', async () => {
    mockHappyPathApi();

    renderPage('/destination/prague?tab=trip-builder');

    // TripBuilder's empty state confirms the checkout view rendered.
    expect(await screen.findByText(/start building your trip/i)).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Trip Builder'})).not.toBeInTheDocument();
});

test('switching to Trip Builder removes the tab nav', async () => {
    mockHappyPathApi();

    renderPage();
    expect(await screen.findByRole('navigation')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Trip Builder'}));

    expect(await screen.findByText(/start building your trip/i)).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
});
