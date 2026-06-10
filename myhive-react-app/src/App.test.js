import { render, screen } from '@testing-library/react';
import App from './App';
import api from './services/api';

jest.mock('./services/api');

beforeEach(() => {
  // jsdom does not implement media playback; HomePage autoplays its hero video.
  jest.spyOn(window.HTMLMediaElement.prototype, 'play').mockResolvedValue();
});

test('renders the public layout with the site header', async () => {
  api.getDestinations.mockResolvedValue([]);
  api.getActivities.mockResolvedValue([]);

  render(<App />);

  expect(await screen.findByAltText('Trivlu')).toBeInTheDocument();
});
