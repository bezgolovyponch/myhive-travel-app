import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import ActivityCard from './ActivityCard';
import {TripProvider} from '../context/TripContext';

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

function renderCard() {
  return render(
    <MemoryRouter>
      <TripProvider>
        <ActivityCard activity={activity} />
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
