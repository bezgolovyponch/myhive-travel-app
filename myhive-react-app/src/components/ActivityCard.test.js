import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import ActivityCard from './ActivityCard';
import {TripProvider} from '../context/TripContext';

const activity = {
  id: 'a1',
  slug: 'karting',
  name: 'Karting',
  description: 'Race go-karts with the lads.',
  price: 40,
  destinationSlug: 'prague',
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
