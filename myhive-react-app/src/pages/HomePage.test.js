import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import HomePage from './HomePage';
import api from '../services/api';
import { AppContext } from '../context/AppContext';

jest.mock('../services/api');

beforeEach(() => {
  // jsdom does not implement media playback; the hero video autoplays.
  jest.spyOn(window.HTMLMediaElement.prototype, 'play').mockResolvedValue();
});

const baseState = {
  destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  tripItems: [],
  loading: false,
  error: null,
};

function renderHome(state = baseState) {
  return render(
    <HelmetProvider>
      <AppContext.Provider value={{ state, dispatch: jest.fn() }}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </AppContext.Provider>
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
    ...baseState,
    destinations: [
      { id: 'd1', slug: 'prague', name: 'Prague' },
      { id: 'd2', slug: 'budapest', name: 'Budapest' },
    ],
  });

  await screen.findByText('What the Lads Say');

  await userEvent.click(screen.getAllByText('Start Group Vote')[0]);
  await screen.findByText('Continue to Categories');

  // DESTINATION_PICKER_ENABLED is off: the first destination is auto-selected.
  expect(screen.getByText('Prague')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});
