import { render, screen } from '@testing-library/react';
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

  expect(screen.getByRole('heading', {level: 1, name: /Best man, not travel agent\./})).toBeInTheDocument();
  expect(screen.getByText('Stag Do Specialists')).toBeInTheDocument();
  expect(screen.getByText('Let the group decide. You just book it.')).toBeInTheDocument();
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
  expect(await screen.findByText('Continue')).toBeInTheDocument();
  // The destination is preset silently — no picker and no read-only row; it
  // only surfaces later on the booking page.
  expect(screen.queryByText('Prague')).not.toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});

test('hero offers the two main flows: browsing activities and Stag Do AI, no vote button', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  const {container} = renderHome();

  await screen.findByText('What the Lads Say');

  const heroCtas = container.querySelector('.hero-cta-group');
  expect([...heroCtas.children].map(el => el.textContent.trim()))
    .toEqual(['Browse activities', 'Stag Do AI']);
});

test('hero "Stag Do AI" links to the planner chat and fires cta_click', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();

  const cta = screen.getByRole('link', {name: /Stag Do AI/i});
  expect(cta).toHaveAttribute('href', '/plan');
  await userEvent.click(cta);
  expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Stag Do AI', block: 'hero'});
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
  await screen.findByText('Continue');

  // The destination is resolved silently (default Prague) — the modal never
  // shows a picker or a read-only destination row.
  expect(screen.queryByText('Prague')).not.toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});

test('hero headline is plain text, not a link or button', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);

  renderHome();

  await screen.findByText('What the Lads Say');

  const headline = screen.getByRole('heading', {level: 1, name: /prague stag do planner/i});
  expect(headline.querySelector('button')).toBeNull();
  expect(headline.querySelector('a')).toBeNull();
});

test('Browse activities CTA links to the destination activities catalog', async () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();

  const cta = screen.getByRole('link', {name: /Browse activities/i});
  // Routes to the default destination's activities tab (the catalog page),
  // not an on-page anchor scroll.
  expect(cta).toHaveAttribute('href', '/destination/prague?tab=activities');
  await userEvent.click(cta);
  expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Browse activities', block: 'hero'});
});

test('hero title mentions Prague', () => {
  api.getFeaturedActivities.mockResolvedValue([]);
  renderHome();
  expect(screen.getByRole('heading', {level: 1}))
    .toHaveTextContent('Prague stag do planner');
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
