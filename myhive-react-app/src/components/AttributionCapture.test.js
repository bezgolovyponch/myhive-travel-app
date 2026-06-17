import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import AttributionCapture from './AttributionCapture';
import { captureFromUrl } from '../utils/attribution';

jest.mock('../utils/attribution', () => ({ captureFromUrl: jest.fn() }));

function Nav({ to }) {
  const navigate = useNavigate();
  return <button onClick={() => navigate(to)}>go</button>;
}

beforeEach(() => jest.clearAllMocks());

test('captures on mount with the initial search string', () => {
  render(
    <MemoryRouter initialEntries={['/?utm_source=fb&utm_medium=paid_social']}>
      <AttributionCapture />
    </MemoryRouter>
  );
  expect(captureFromUrl).toHaveBeenCalledTimes(1);
  expect(captureFromUrl).toHaveBeenCalledWith('?utm_source=fb&utm_medium=paid_social', expect.any(String));
});

test('re-captures on SPA navigation to a new search', async () => {
  render(
    <MemoryRouter initialEntries={['/?utm_source=fb']}>
      <AttributionCapture />
      <Nav to="/?ref=invite" />
    </MemoryRouter>
  );
  expect(captureFromUrl).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByText('go'));
  expect(captureFromUrl).toHaveBeenCalledTimes(2);
  expect(captureFromUrl).toHaveBeenLastCalledWith('?ref=invite', expect.any(String));
});

test('does not re-capture when navigating to a different path with the same search', async () => {
  render(
    <MemoryRouter initialEntries={['/?utm_source=fb']}>
      <AttributionCapture />
      <Nav to="/about?utm_source=fb" />
    </MemoryRouter>
  );
  expect(captureFromUrl).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByText('go'));
  // same search string -> effect dep unchanged -> no re-fire
  expect(captureFromUrl).toHaveBeenCalledTimes(1);
});
