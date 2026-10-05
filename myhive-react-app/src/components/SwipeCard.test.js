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

test('preloads the next few card images so a swipe reveals a ready photo', () => {
    const preloadedUrls = [];
    const OriginalImage = window.Image;
    window.Image = class {
        set src(value) {
            preloadedUrls.push(value);
        }
    };
    const deck = [
        { id: 'a1', name: 'One', imageUrl: 'http://img/1.jpg' },
        { id: 'a2', name: 'Two', imageUrl: 'http://img/2.jpg' },
        { id: 'a3', name: 'Three', imageUrl: 'http://img/3.jpg' },
        { id: 'a4', name: 'Four', imageUrl: 'http://img/4.jpg' },
        { id: 'a5', name: 'Five', imageUrl: 'http://img/5.jpg' },
    ];

    render(<SwipeCard cards={deck} currentIndex={0} onSwipe={jest.fn()} />);

    expect(preloadedUrls).toEqual(['http://img/2.jpg', 'http://img/3.jpg', 'http://img/4.jpg']);
    window.Image = OriginalImage;
});

test('shows how long an activity takes but never a price', () => {
  render(
    <SwipeCard
      cards={[{ id: 'a', name: 'Tank driving', imageUrl: 'http://img/a.jpg', duration: 120, price: 149, minPrice: 400 }]}
      currentIndex={0}
      onSwipe={jest.fn()}
    />,
  );

  expect(screen.getByText('2h')).toBeInTheDocument();
  expect(screen.queryByText(/149|person|Group minimum/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /invite link/i })).not.toBeInTheDocument();
});
