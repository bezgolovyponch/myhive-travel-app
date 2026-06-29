import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import Header from './Header';
import {CatalogProvider} from '../context/CatalogContext';
import {TripProvider} from '../context/TripContext';
import api from '../services/api';

jest.spyOn(api, 'getDestinations').mockResolvedValue([]);

function renderHeader(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <CatalogProvider>
        <TripProvider>
          <Header />
        </TripProvider>
      </CatalogProvider>
    </MemoryRouter>
  );
}

test('Destinations link points at the apex domain', () => {
  renderHeader();
  expect(screen.getByRole('link', {name: 'Destinations'}))
    .toHaveAttribute('href', 'https://trivlu.com');
});

test('Activities link points at the destination subdomain', () => {
  renderHeader();
  expect(screen.getByRole('link', {name: 'Activities'}))
    .toHaveAttribute('href', expect.stringMatching(/^https:\/\/[a-z]+\.trivlu\.com$/));
});

test('header is transparent on a non-home page', () => {
  const {container} = renderHeader('/about');
  expect(container.querySelector('.header')).toHaveClass('header--transparent');
});
