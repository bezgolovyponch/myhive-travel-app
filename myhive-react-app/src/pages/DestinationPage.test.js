import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {AppContext} from '../context/AppContext';
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

const baseState = {
    tripItems: [],
    activities: [],
    loading: false,
    error: null,
};

function renderPage() {
    return render(
        <HelmetProvider>
            <AppContext.Provider value={{state: baseState, dispatch: jest.fn()}}>
                <MemoryRouter initialEntries={['/destination/prague']}>
                    <Routes>
                        <Route path="/destination/:slug" element={<DestinationPage/>}/>
                    </Routes>
                </MemoryRouter>
            </AppContext.Provider>
        </HelmetProvider>
    );
}

test('a failed filter change shows an inline error, not "Destination not found"', async () => {
    api.getDestinationBySlug.mockResolvedValue({id: 'd1', name: 'Prague', slug: 'prague'});
    api.getCategoriesForDestination.mockResolvedValue([{slug: 'food', name: 'food'}]);
    api.getPackagesByDestination.mockResolvedValue([]);
    api.getActivitiesPaged
        .mockResolvedValueOnce({content: [], totalElements: 0, last: true})
        .mockRejectedValueOnce(new Error('network'));

    renderPage();
    expect(await screen.findByRole('heading', {name: 'Prague'})).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'Food'}));

    expect(await screen.findByText(/couldn't load activities/i)).toBeInTheDocument();
    // The page itself must survive the list failure
    expect(screen.getByRole('heading', {name: 'Prague'})).toBeInTheDocument();
    expect(screen.queryByText('Destination not found')).not.toBeInTheDocument();
});
