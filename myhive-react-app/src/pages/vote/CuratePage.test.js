import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

const setup = {
  destination: { id: 'dest1' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

function renderWith(state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state }]}>
      <Routes>
        <Route path="/vote/new/curate" element={<CuratePage />} />
        <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

test('picks an activity and creates a session', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, categories: ['Chillout'] },
    ],
  });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-abc', managerToken: 'mgr-xyz' });

  renderWith({ setup, responses: [] });

  expect(await screen.findByText('Tank Driving')).toBeInTheDocument();

  // Click 'Add' on the first card
  await userEvent.click(screen.getAllByRole('button', { name: 'Add' })[0]);
  expect(screen.getByText('Selected: 1')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Create & get link' }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalled());
  const arg = voteApi.createSession.mock.calls[0][0];
  expect(arg.activityIds).toEqual(['act1']);
  expect(arg.budget).toBe(3000);
  expect(await screen.findByText('waiting page')).toBeInTheDocument();
});

test('no setup state → redirects home', async () => {
  render(
    <MemoryRouter initialEntries={['/vote/new/curate']}>
      <Routes>
        <Route path="/vote/new/curate" element={<CuratePage />} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
  expect(await screen.findByText('home')).toBeInTheDocument();
});

test('empty pool shows empty-state message', async () => {
  voteApi.buildPool.mockResolvedValue({ pool: [] });
  renderWith({ setup, responses: [] });
  expect(await screen.findByText(/no activities match/i)).toBeInTheDocument();
});
