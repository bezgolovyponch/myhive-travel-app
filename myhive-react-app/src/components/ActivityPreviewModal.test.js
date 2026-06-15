import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActivityPreviewModal from './ActivityPreviewModal';

const activity = {
  name: 'Snorkeling Tour',
  price: 45,
  duration: 180,
  categories: ['Water', 'Nature'],
  imageUrl: 'http://img/snorkel.jpg',
  description: 'Explore the coral reefs with a guide.',
};

test('renders nothing when activity is null', () => {
  const { container } = render(
    <ActivityPreviewModal activity={null} link={null} onClose={jest.fn()} />
  );
  expect(container).toBeEmptyDOMElement();
});

test('shows name, meta and description', () => {
  render(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);

  expect(screen.getByRole('heading', { name: 'Snorkeling Tour' })).toBeInTheDocument();
  expect(screen.getByText(/€45 \/ person/)).toBeInTheDocument();
  expect(screen.getByText(/3h/)).toBeInTheDocument();
  expect(screen.getByText(/Water · Nature/)).toBeInTheDocument();
  expect(screen.getByText('Explore the coral reefs with a guide.')).toBeInTheDocument();
});

test('shows a muted placeholder when there is no description', () => {
  render(
    <ActivityPreviewModal activity={{ ...activity, description: '' }} link={null} onClose={jest.fn()} />
  );
  expect(screen.getByText(/No description yet/i)).toBeInTheDocument();
});

test('shows the View full page link only when link is provided', () => {
  const { rerender } = render(
    <ActivityPreviewModal activity={activity} link="/destination/bali/activity/snorkel" onClose={jest.fn()} />
  );
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/snorkel');

  rerender(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);
  expect(screen.queryByRole('link', { name: /View full page/i })).not.toBeInTheDocument();
});

test('calls onClose on close button, backdrop click, and Escape', async () => {
  const onClose = jest.fn();
  render(<ActivityPreviewModal activity={activity} link={null} onClose={onClose} />);

  await userEvent.click(screen.getByRole('button', { name: /close/i }));
  expect(onClose).toHaveBeenCalledTimes(1);

  await userEvent.click(screen.getByRole('dialog'));   // backdrop
  expect(onClose).toHaveBeenCalledTimes(2);

  await userEvent.keyboard('{Escape}');
  expect(onClose).toHaveBeenCalledTimes(3);
});

test('clicking inside the content does not close', async () => {
  const onClose = jest.fn();
  render(<ActivityPreviewModal activity={activity} link={null} onClose={onClose} />);

  await userEvent.click(screen.getByRole('heading', { name: 'Snorkeling Tour' }));
  expect(onClose).not.toHaveBeenCalled();
});

test('moves focus to the close button when opened', () => {
  render(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);
  expect(screen.getByRole('button', { name: /close/i })).toHaveFocus();
});

test('restores focus to the previously focused element when closed', () => {
  const onClose = jest.fn();
  const { rerender } = render(
    <>
      <button>opener</button>
      <ActivityPreviewModal activity={null} link={null} onClose={onClose} />
    </>
  );
  const opener = screen.getByRole('button', { name: 'opener' });
  opener.focus();
  expect(opener).toHaveFocus();

  rerender(
    <>
      <button>opener</button>
      <ActivityPreviewModal activity={activity} link={null} onClose={onClose} />
    </>
  );
  expect(screen.getByRole('button', { name: /close/i })).toHaveFocus();

  rerender(
    <>
      <button>opener</button>
      <ActivityPreviewModal activity={null} link={null} onClose={onClose} />
    </>
  );
  expect(opener).toHaveFocus();
});

test('traps Tab focus within the dialog', async () => {
  render(
    <ActivityPreviewModal
      activity={activity}
      link="/destination/bali/activity/snorkel"
      onClose={jest.fn()}
    />
  );
  const closeBtn = screen.getByRole('button', { name: /close/i });
  const fullPageLink = screen.getByRole('link', { name: /View full page/i });

  expect(closeBtn).toHaveFocus();           // autofocus on open

  await userEvent.tab();                     // close -> link
  expect(fullPageLink).toHaveFocus();

  await userEvent.tab();                     // link -> wraps back to close
  expect(closeBtn).toHaveFocus();

  await userEvent.tab({ shift: true });      // close -> wraps back to link
  expect(fullPageLink).toHaveFocus();
});
