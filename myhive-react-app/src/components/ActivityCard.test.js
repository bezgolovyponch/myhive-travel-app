import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import ActivityCard from './ActivityCard';
import {TripProvider} from '../context/TripContext';
import {LocaleProvider} from '../i18n';
import de from '../i18n/messages/de.json';

const activity = {
  id: 'a1',
  slug: 'karting',
  name: 'Karting',
  description: 'Race go-karts with the lads.',
  price: 40,
  destinationSlug: 'prague',
  includes: 'Helmet, Fuel, Instructor',
  categories: [{name: 'Adrenaline'}],
};

function renderCard(activityOverride) {
  return render(
    <MemoryRouter>
      <TripProvider>
        <ActivityCard activity={activityOverride || activity} />
      </TripProvider>
    </MemoryRouter>
  );
}

test('does not render the inline description', () => {
  renderCard();
  expect(screen.queryByText('Race go-karts with the lads.')).toBeNull();
});

test('does not render the includes line', () => {
  renderCard();
  expect(screen.queryByText(/Includes:/i)).toBeNull();
});

test('shows Add to trip and More info buttons', () => {
  renderCard();
  expect(screen.getByRole('button', {name: /Add to trip/i})).toBeInTheDocument();
  expect(screen.getByRole('button', {name: /More info/i})).toBeInTheDocument();
});

test('More info opens the preview modal without navigating', async () => {
  renderCard();
  await userEvent.click(screen.getByRole('button', {name: /More info/i}));
  // modal renders the activity name as a dialog heading
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(screen.getAllByText('Karting').length).toBeGreaterThan(0);
});

test('preview modal is not rendered inside the card element', async () => {
  // The card has a hover transform (.card:hover) + overflow:hidden; a transformed
  // ancestor becomes the containing block for position:fixed, which made the
  // modal flicker. The modal must render outside the card element.
  const {container} = renderCard();
  await userEvent.click(screen.getByRole('button', {name: /More info/i}));
  const card = container.querySelector('.activity-card');
  const dialog = screen.getByRole('dialog');
  expect(card.contains(dialog)).toBe(false);
});

test('closing the preview via backdrop does not navigate to the detail page', async () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <TripProvider>
        <Routes>
          <Route path="/" element={<ActivityCard activity={activity} />} />
          <Route path="/destination/prague/activity/karting" element={<div>Detail page</div>} />
        </Routes>
      </TripProvider>
    </MemoryRouter>
  );
  await userEvent.click(screen.getByRole('button', {name: /More info/i}));
  const dialog = screen.getByRole('dialog');
  // click the backdrop (the dialog overlay itself, not its content)
  await userEvent.click(dialog);
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.queryByText('Detail page')).toBeNull();
  expect(screen.getByRole('button', {name: /More info/i})).toBeInTheDocument();
});

test('a card in a list shows no per-person price or group minimum', () => {
  renderCard({id: 'a1', name: 'Boat Rental', price: 50, minPrice: 300});
  expect(screen.queryByText(/€50|€300|person|Group minimum/)).not.toBeInTheDocument();
});

test('a card says how many optional extras there are, on one line, still without a price', () => {
  renderCard({...activity, extras: [
    {code: 'show', name: 'Private show', price: 250, pricing: 'PER_GROUP'},
    {code: 'photo', name: 'Photos', price: 15, pricing: 'PER_PERSON'},
    {code: 'beer', name: 'Beer', price: 5, pricing: 'PER_PERSON'},
  ]});
  expect(screen.getByText('3 optional extras')).toBeInTheDocument();
  expect(screen.queryByText(/€/)).not.toBeInTheDocument();
});

test('a card without extras shows no extras chip', () => {
  renderCard({...activity, extras: []});
  expect(screen.queryByText(/Optional extras/)).not.toBeInTheDocument();
});

test('linksToGermanSlugUnderDe', () => {
  const expectedSlugDe = 'go-kart-fahren';
  render(
    <LocaleProvider locale="de" messages={de}>
      <MemoryRouter>
        <TripProvider>
          <ActivityCard activity={{...activity, slugDe: expectedSlugDe}} />
        </TripProvider>
      </MemoryRouter>
    </LocaleProvider>
  );
  const link = document.querySelector('a.activity-title-link');
  expect(link.getAttribute('href')).toBe('/de/jga-prag/aktivitaeten/' + expectedSlugDe);
});
