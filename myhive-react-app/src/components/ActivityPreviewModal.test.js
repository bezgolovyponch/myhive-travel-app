import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActivityPreviewModal from './ActivityPreviewModal';
import api from '../services/api';

jest.mock('../services/api', () => ({ __esModule: true, default: { getActivity: jest.fn() } }));

beforeEach(() => jest.clearAllMocks());

const activity = {
  name: 'Snorkeling Tour',
  price: 45,
  duration: 180,
  categories: ['Water', 'Nature'],
  imageUrl: 'http://img/snorkel.jpg',
  description: 'Explore the coral reefs with a guide.',
  includes: 'Mask, snorkel & tube; Fins\nGuide',
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
  expect(screen.queryByText(/€45|person/)).not.toBeInTheDocument();
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

test('shows the What\'s included list parsed from the includes string', () => {
  render(<ActivityPreviewModal activity={activity} link={null} onClose={jest.fn()} />);

  expect(screen.getByText(/What's included/i)).toBeInTheDocument();
  // Commas are not separators — 'Mask, snorkel & tube' stays one item.
  expect(screen.getByText('Mask, snorkel & tube')).toBeInTheDocument();
  expect(screen.getByText('Fins')).toBeInTheDocument();
  expect(screen.getByText('Guide')).toBeInTheDocument();
});

test('hides the What\'s included section when includes is empty', () => {
  render(
    <ActivityPreviewModal activity={{ ...activity, includes: '' }} link={null} onClose={jest.fn()} />
  );
  expect(screen.queryByText(/What's included/i)).not.toBeInTheDocument();
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

test('a row with only a name is completed by id, and links to its own page', async () => {
  api.getActivity.mockResolvedValue({
    name: 'Snorkeling Tour', description: 'Explore the coral reefs with a guide.', includes: 'Fins',
    categories: [{ name: 'Water' }], slug: 'snorkeling-tour', destinationSlug: 'tenerife',
  });
  render(<ActivityPreviewModal activity={{ name: 'Snorkeling Tour', duration: 180 }} activityId="a1" onClose={jest.fn()} />);

  expect(screen.getByText('Loading the details…')).toBeInTheDocument();
  expect(await screen.findByText('Explore the coral reefs with a guide.')).toBeInTheDocument();
  expect(api.getActivity).toHaveBeenCalledWith('a1');
  expect(screen.getByText(/3h · Water/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /View full page/ }))
    .toHaveAttribute('href', '/destination/tenerife/activity/snorkeling-tour');
});

test('an activity that already has its text is not loaded again', () => {
  render(<ActivityPreviewModal activity={activity} activityId="a1" onClose={jest.fn()} />);
  expect(api.getActivity).not.toHaveBeenCalled();
});

test('when the details cannot be loaded the name stays and the dialog still closes', async () => {
  api.getActivity.mockRejectedValue(new Error('offline'));
  const onClose = jest.fn();
  render(<ActivityPreviewModal activity={{ name: 'Snorkeling Tour' }} activityId="a1" onClose={onClose}
                               closeLabel="Back to the draft" />);

  expect(await screen.findByText(/No description yet/i)).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Snorkeling Tour' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Back to the draft' }));
  expect(onClose).toHaveBeenCalledTimes(1);
});

test('the action does its thing and closes the dialog', async () => {
  const onClose = jest.fn();
  const onClick = jest.fn();
  render(<ActivityPreviewModal activity={activity} onClose={onClose} closeLabel="Back to the draft"
                               action={{ label: 'Add to the draft', onClick }} />);

  await userEvent.click(screen.getByRole('button', { name: 'Add to the draft' }));
  expect(onClick).toHaveBeenCalledTimes(1);
  expect(onClose).toHaveBeenCalledTimes(1);
});
