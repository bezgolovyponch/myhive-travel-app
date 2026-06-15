import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SwipeCard from './SwipeCard';
import { copyToClipboard } from '../utils/clipboard';

jest.mock('../utils/clipboard');

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

test('shows the copied confirmation only when the clipboard write succeeds', async () => {
  copyToClipboard.mockResolvedValue(true);
  render(
    <SwipeCard cards={cards} currentIndex={0} onSwipe={jest.fn()} shareUrl="https://trivlu.com/vote/x" />
  );

  await userEvent.click(screen.getByRole('button', { name: 'Copy Invite Link' }));

  expect(copyToClipboard).toHaveBeenCalledWith('https://trivlu.com/vote/x');
  expect(await screen.findByRole('button', { name: /Link Copied/i })).toBeInTheDocument();
});

test('keeps the default label when the clipboard write fails', async () => {
  copyToClipboard.mockResolvedValue(false);
  render(
    <SwipeCard cards={cards} currentIndex={0} onSwipe={jest.fn()} shareUrl="https://trivlu.com/vote/x" />
  );

  await userEvent.click(screen.getByRole('button', { name: 'Copy Invite Link' }));

  await waitFor(() => expect(copyToClipboard).toHaveBeenCalledTimes(1));
  expect(screen.queryByText(/Link Copied/i)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Copy Invite Link' })).toBeInTheDocument();
});
