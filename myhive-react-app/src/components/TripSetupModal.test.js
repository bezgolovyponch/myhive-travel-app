import { render, screen } from '@testing-library/react';
import TripSetupModal from './TripSetupModal';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';

// The flag is off in real config while Prague is the only live destination;
// these tests cover the picker logic that comes back once it is re-enabled.
jest.mock('../services/config', () => ({
  ...jest.requireActual('../services/config'),
  DESTINATION_PICKER_ENABLED: true,
}));

const baseState = {
  destinations: [
    { id: 'd1', slug: 'prague', name: 'Prague' },
    { id: 'd2', slug: 'budapest', name: 'Budapest' },
  ],
  tripSetupModalOpen: false,
  loading: false,
  error: null,
};

function renderVoteModal(state = baseState) {
  return render(
    <CatalogContext.Provider value={{ state, dispatch: jest.fn() }}>
      <TripContext.Provider value={{ state, dispatch: jest.fn() }}>
        <TripSetupModal isVoteMode voteOpen onVoteConfirm={jest.fn()} onVoteCancel={jest.fn()} />
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
}

test('shows the destination picker when enabled and several destinations exist', () => {
  renderVoteModal();

  expect(screen.getByLabelText('Destination *')).toBeInTheDocument();
  expect(screen.getByRole('option', { name: 'Budapest' })).toBeInTheDocument();
});

test('auto-selects the only destination even with the picker enabled', () => {
  renderVoteModal({
    ...baseState,
    destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  });

  expect(screen.getByText('Prague')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});
