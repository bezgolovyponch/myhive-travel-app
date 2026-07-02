import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import ActivityDetailPage from './ActivityDetailPage';
import {TripProvider} from '../context/TripContext';
import api from '../services/api';
import {WHATSAPP_URL} from '../services/config';

const activity = {
  id: 'a1',
  slug: 'ak-47-shooting',
  name: 'AK-47 Shooting',
  description: 'Feel the raw power of the legendary AK-47.',
  price: 89,
  duration: 180,
  includes: 'English-speaking guide, Return transfer, All equipment',
  destinationId: 'd1',
  destinationSlug: 'prague',
  destinationName: 'Prague',
  imageUrl: 'https://x/main.jpg',
  categories: [{name: 'Guns & Bullets'}],
};

const otherActivities = [
  activity,
  {
    id: 'a2',
    slug: 'karting',
    name: 'Go-Karting',
    price: 40,
    destinationSlug: 'prague',
    categories: [{name: 'Extreme'}],
  },
];

beforeEach(() => {
  jest.spyOn(api, 'getActivityBySlug').mockResolvedValue(activity);
  jest.spyOn(api, 'getActivities').mockResolvedValue(otherActivities);
});

afterEach(() => {
  jest.restoreAllMocks();
});

function renderPage() {
  return render(
    <HelmetProvider>
      <MemoryRouter initialEntries={['/destination/prague/activity/ak-47-shooting']}>
        <TripProvider>
          <Routes>
            <Route
              path="/destination/:destinationSlug/activity/:slug"
              element={<ActivityDetailPage />}
            />
          </Routes>
        </TripProvider>
      </MemoryRouter>
    </HelmetProvider>
  );
}

async function renderLoadedPage() {
  const result = renderPage();
  await waitFor(() =>
    expect(screen.getByRole('heading', {name: 'AK-47 Shooting'})).toBeInTheDocument()
  );
  return result;
}

test('renders breadcrumbs with destination and activity name', async () => {
  await renderLoadedPage();
  expect(screen.getByRole('link', {name: 'Home'})).toBeInTheDocument();
  expect(screen.getByRole('link', {name: 'Prague'})).toBeInTheDocument();
});

test('renders category and duration chips under the title', async () => {
  await renderLoadedPage();
  expect(screen.getByText('Guns & Bullets')).toBeInTheDocument();
  expect(screen.getAllByText('3h').length).toBeGreaterThan(0);
});

test("renders What's included as a checklist", async () => {
  await renderLoadedPage();
  expect(screen.getByRole('heading', {name: /What's included/i})).toBeInTheDocument();
  expect(screen.getByText('English-speaking guide')).toBeInTheDocument();
  expect(screen.getByText('Return transfer')).toBeInTheDocument();
  expect(screen.getByText('All equipment')).toBeInTheDocument();
});

test('renders the About section with the description', async () => {
  await renderLoadedPage();
  expect(screen.getByRole('heading', {name: /About this activity/i})).toBeInTheDocument();
  expect(screen.getByText(/Feel the raw power/)).toBeInTheDocument();
});

test('renders the add panel price and Add to trip actions', async () => {
  await renderLoadedPage();
  expect(screen.getAllByText('€89').length).toBeGreaterThan(0);
  // one button in the sticky panel, one in the mobile add bar
  expect(screen.getAllByRole('button', {name: /Add to trip/i})).toHaveLength(2);
});

test('adding to trip marks both add buttons as added', async () => {
  await renderLoadedPage();
  const [panelButton] = screen.getAllByRole('button', {name: /Add to trip/i});
  await userEvent.click(panelButton);
  const addedButtons = screen.getAllByRole('button', {name: /Added to trip/i});
  expect(addedButtons).toHaveLength(2);
  addedButtons.forEach(button => expect(button).toBeDisabled());
});

test('renders the WhatsApp help link', async () => {
  await renderLoadedPage();
  const link = screen.getByRole('link', {name: /Chat with our team/i});
  expect(link).toHaveAttribute('href', WHATSAPP_URL);
  expect(link).toHaveAttribute('target', '_blank');
});

test('renders More Activities from the same destination excluding the current one', async () => {
  await renderLoadedPage();
  await waitFor(() =>
    expect(screen.getByRole('heading', {name: 'More Activities in Prague'})).toBeInTheDocument()
  );
  expect(api.getActivities).toHaveBeenCalledWith('d1');
  const moreSection = screen.getByRole('heading', {name: 'More Activities in Prague'}).closest('section');
  expect(within(moreSection).getByText('Go-Karting')).toBeInTheDocument();
  // current activity must not appear as a card in the grid
  expect(within(moreSection).queryByText('AK-47 Shooting')).toBeNull();
});
