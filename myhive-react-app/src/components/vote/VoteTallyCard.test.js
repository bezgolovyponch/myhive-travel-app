import { render, screen } from '@testing-library/react';
import VoteTallyCard from './VoteTallyCard';

const rows = [
  { activityId: 'a1', name: 'Bar Crawl', price: 45, likeCount: 8 },
  { activityId: 'a2', name: 'Karting', price: 60, likeCount: 4 },
];

test('renders the voter count and one row per activity', () => {
  render(<VoteTallyCard participantCount={9} rows={rows} />);
  expect(screen.getByText('9 mates have voted')).toBeInTheDocument();
  expect(screen.getByText('Bar Crawl')).toBeInTheDocument();
  expect(screen.getByText('8')).toBeInTheDocument();
  expect(screen.getByText('Karting')).toBeInTheDocument();
});

test('uses singular copy for one voter', () => {
  render(<VoteTallyCard participantCount={1} rows={rows} />);
  expect(screen.getByText('1 mate has voted')).toBeInTheDocument();
});

test('bar width is likeCount over participantCount', () => {
  const { container } = render(<VoteTallyCard participantCount={8} rows={rows} />);
  const fills = container.querySelectorAll('.vote-tally-fill');
  expect(fills[0].style.width).toBe('100%');
  expect(fills[1].style.width).toBe('50%');
});

test('shows prices only when showPrices is set', () => {
  const { rerender } = render(<VoteTallyCard participantCount={9} rows={rows} />);
  expect(screen.queryByText(/€45/)).not.toBeInTheDocument();
  rerender(<VoteTallyCard participantCount={9} rows={rows} showPrices />);
  expect(screen.getByText(/€45/)).toBeInTheDocument();
});
