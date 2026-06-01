import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SwipeCard from './SwipeCard';

const cards = [
  {
    id: 'a1',
    name: 'Snorkeling Tour',
    price: 45,
    duration: 180,
    description: 'Explore the coral reefs.',
    slug: 'snorkel',
    destinationSlug: 'bali',
    categories: ['Water'],
  },
];

const getCardLink = (a) => `/destination/${a.destinationSlug}/activity/${a.slug}`;

test('clicking the card name opens the info modal and does not trigger a swipe', async () => {
  const onSwipe = jest.fn();
  render(
    <SwipeCard cards={cards} currentIndex={0} onSwipe={onSwipe} getCardLink={getCardLink} />
  );

  await userEvent.click(screen.getByRole('button', { name: 'Snorkeling Tour' }));

  expect(screen.getByText('Explore the coral reefs.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/snorkel');
  expect(onSwipe).not.toHaveBeenCalled();
});
