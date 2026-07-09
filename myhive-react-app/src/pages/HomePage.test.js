import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import HomePage from './HomePage';
import api from '../services/api';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';
import {pushEvent} from '../utils/analytics';

jest.mock('../services/api');
jest.mock('../utils/analytics', () => ({pushEvent: jest.fn()}));

beforeEach(() => {
  jest.clearAllMocks();
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

  expect(screen.getByRole('button', {name: 'The smartest way to plan a stag do'})).toBeInTheDocument();
  expect(screen.getByText('Stag Do Specialists')).toBeInTheDocument();
  expect(screen.getByText('The Smartest Way to Plan a Stag Do')).toBeInTheDocument();
  expect(await screen.findByText('Go-Karting')).toBeInTheDocument();
  expect(screen.getByText('View All Activities')).toHaveAttribute('href', '/destination/prague');
  expect(screen.getByText('What the Lads Say')).toBeInTheDocument();
  expect(screen.getByText("We're just a message away")).toBeInTheDocument();
});

test('hides the activities section when featured and fallback activities are both empty', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  api.getActivities.mockResolvedValue([]);

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

test('hero "Start Group Vote" fires cta_click with block hero before opening vote setup', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await screen.findByText('What the Lads Say');

  await userEvent.click(screen.getAllByText('Start Group Vote')[0]);

  // The hero CTA fires cta_click first; opening the vote setup modal then fires
  // tb_start, so pushEvent is called more than once. Assert the FIRST call is the
  // cta_click — proving it fired before the modal opened.
  expect(pushEvent.mock.calls[0]).toEqual(['cta_click', {cta_label: 'Start Group Vote', block: 'hero'}]);
  // The vote setup modal should still open (existing action not broken).
  expect(await screen.findByText('Continue to Categories')).toBeInTheDocument();
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

test('hero headline is clickable and opens the group vote setup', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await screen.findByText('What the Lads Say');

  await userEvent.click(screen.getByRole('button', {name: /the smartest way to plan a stag do/i}));

  // The headline CTA fires cta_click first; opening the modal then fires tb_start.
  expect(pushEvent.mock.calls[0]).toEqual(['cta_click', {cta_label: 'Hero headline', block: 'hero'}]);
  expect(await screen.findByText('Continue to Categories')).toBeInTheDocument();
});

test('Explore activities CTA scrolls to the activities section', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();
  const section = document.createElement('div');
  section.id = 'activities';
  section.scrollIntoView = jest.fn();
  document.body.appendChild(section);

  const cta = screen.getByRole('link', {name: /Explore activities/i});
  expect(cta).toHaveAttribute('href', '/#activities');
  await userEvent.click(cta);
  await waitFor(() => expect(section.scrollIntoView).toHaveBeenCalled());
  expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Explore activities', block: 'hero'});
  section.remove();
});
