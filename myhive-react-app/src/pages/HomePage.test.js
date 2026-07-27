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

  expect(screen.getByRole('heading', {level: 1, name: 'The Easiest Prague Stag Do. All Sorted For You.'})).toBeInTheDocument();
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

test('hero headline is plain text, not a link or button', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await screen.findByText('What the Lads Say');

  const headline = screen.getByRole('heading', {level: 1, name: /the easiest prague stag do/i});
  expect(headline.querySelector('button')).toBeNull();
  expect(headline.querySelector('a')).toBeNull();
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

test('hero title mentions Prague', () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();
  expect(screen.getByRole('heading', {level: 1}))
    .toHaveTextContent('The Easiest Prague Stag Do. All Sorted For You.');
});

test('sticky CTA is feature-flagged off by default', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();
  await screen.findByText('What the Lads Say');
  expect(document.querySelector('.sticky-vote-cta')).toBeNull();
});

test('activities section comes directly after the hero', async () => {
  api.getFeaturedActivities.mockResolvedValue([
    { id: 'a1', name: 'Go-Karting', price: 50, slug: 'go-karting', destinationSlug: 'prague', categories: [] },
  ]);
  renderHome();

  await screen.findByText('Go-Karting');

  const hero = document.querySelector('.hero');
  const activities = document.querySelector('.featured-activities') || document.querySelector('#activities');
  const trust = document.querySelector('.trust-bar');
  expect(hero.compareDocumentPosition(activities) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(activities.compareDocumentPosition(trust) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});
