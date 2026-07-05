import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import ActiveVoteModal from './ActiveVoteModal';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

function renderModal(props = {}) {
  return render(
    <MemoryRouter>
      <ActiveVoteModal
        isOpen
        onClose={jest.fn()}
        shareToken="t-1"
        {...props}
      />
    </MemoryRouter>,
  );
}

test('renders the title and body copy', () => {
  renderModal();
  expect(screen.getByText('A vote is already running')).toBeInTheDocument();
  expect(screen.getByText(
      'Your mates are still voting on this trip. Finish that vote before starting a new one — '
      + 'you can end it early from the vote dashboard.'
  )).toBeInTheDocument();
});

test('the dashboard button navigates to the waiting page for the given share token', async () => {
  const user = userEvent.setup();
  renderModal({ shareToken: 't-1' });

  await user.click(screen.getByRole('button', { name: 'Open the vote dashboard' }));

  expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting');
});
