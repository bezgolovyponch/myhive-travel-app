import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import HomePage from './HomePage';
import api from '../services/api';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';

jest.mock('../services/api');

beforeEach(() => {
  // jsdom does not implement media playback; the hero video autoplays.
  jest.spyOn(window.HTMLMediaElement.prototype, 'play').mockResolvedValue();
});

const baseCatalogState = {
  destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  loading: false,
  error: null,
};

const baseTripState = {
  tripItems: [],
};

function renderHome(catalogState = baseCatalogState, tripState = baseTripState) {
  return render(
    <HelmetProvider>
      <CatalogContext.Provider value={{ state: catalogState, dispatch: jest.fn() }}>
        <TripContext.Provider value={{ state: tripState, dispatch: jest.fn() }}>
          <MemoryRouter>
            <HomePage />
          </MemoryRouter>
        </TripContext.Provider>
      </CatalogContext.Provider>
    </HelmetProvider>
  );
}

test('renders all homepage sections', async () => {
  api.getFeaturedActivities.mockResolvedValue([
    { id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: [] },
  ]);

  renderHome();

  expect(screen.getByText('The Easiest Stag Do Decision. All Sorted For You.')).toBeInTheDocument();
  expect(screen.getByText('Stag Do Specialists')).toBeInTheDocument();
  expect(screen.getByText('The Smartest Way to Plan a Stag Do')).toBeInTheDocument();
  expect(await screen.findByText('Go-Karting')).toBeInTheDocument();
  expect(screen.getByText('View All Activities')).toHaveAttribute('href', '/destination/prague');
  expect(screen.getByText('How Booking Works')).toBeInTheDocument();
  expect(screen.getByText('What the Lads Say')).toBeInTheDocument();
});

test('hides the activities section when no featured activities exist', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  // Reviews section renders, so the page is done mounting.
  expect(await screen.findByText('What the Lads Say')).toBeInTheDocument();
  expect(screen.queryByText('70+ Activities. Something for Every Group.')).not.toBeInTheDocument();
});

test('Start Group Vote opens the vote setup modal with the only destination preset', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await screen.findByText('What the Lads Say');

  await userEvent.click(screen.getAllByText('Start Group Vote')[0]);

  // TripSetupModal in vote mode shows the vote-specific confirm button.
  expect(await screen.findByText('Continue to Categories')).toBeInTheDocument();
  // The destination picker is disabled, so the destination is preset read-only.
  expect(screen.getByText('Prague')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});

test('vote setup modal keeps the picker hidden even with several destinations in the API', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome({
    ...baseCatalogState,
    destinations: [
      { id: 'd2', slug: 'budapest', name: 'Budapest' },
      { id: 'd1', slug: 'prague', name: 'Prague' },
    ],
  });

  await screen.findByText('What the Lads Say');

  await userEvent.click(screen.getAllByText('Start Group Vote')[0]);
  await screen.findByText('Continue to Categories');

  // DESTINATION_PICKER_ENABLED is off: the default destination (Prague) is
  // auto-selected even when the API returns another destination first.
  expect(screen.getByText('Prague')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});
