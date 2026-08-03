import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import Header from './Header';
import {CatalogProvider} from '../context/CatalogContext';
import {TripProvider} from '../context/TripContext';
import api from '../services/api';

beforeEach(() => {
  // CRA's jest config resets mocks between tests, so re-apply per test.
  jest.spyOn(api, 'getDestinations').mockResolvedValue([]);
});

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

test('nav has no Destinations link', () => {
  renderHeader();
  expect(screen.queryByRole('link', {name: 'Destinations'})).toBeNull();
});

test('Activities nav item scrolls to the homepage activities section', async () => {
  const user = userEvent.setup();
  renderHeader();
  const section = document.createElement('div');
  section.id = 'activities';
  section.scrollIntoView = jest.fn();
  document.body.appendChild(section);

  const link = screen.getByRole('link', {name: 'Activities'});
  expect(link).toHaveAttribute('href', '/#activities');
  await user.click(link);
  await waitFor(() => expect(section.scrollIntoView).toHaveBeenCalled());
  section.remove();
});

test('header is transparent on a non-home page', () => {
  const {container} = renderHeader('/about');
  expect(container.querySelector('.header')).toHaveClass('header--transparent');
});

test('shows breadcrumbs on a destination page', () => {
  const {container} = renderHeader('/destination/prague?tab=activities');
  expect(container.querySelector('.breadcrumbs')).toBeInTheDocument();
});

test('header is static (non-sticky) on the destination list page', () => {
  const {container} = renderHeader('/destination/prague?tab=activities');
  expect(container.querySelector('.header')).toHaveClass('header--static');
});

test('header is not static on the homepage', () => {
  const {container} = renderHeader('/');
  expect(container.querySelector('.header')).not.toHaveClass('header--static');
});

test('header is not static on the activity detail page', () => {
  const {container} = renderHeader('/destination/prague/activity/karting');
  expect(container.querySelector('.header')).not.toHaveClass('header--static');
});

test('cart button reflects the item count in its label', () => {
  const {container} = renderHeader('/');
  // Empty cart → generic label, no count badge.
  expect(screen.getByRole('button', {name: 'Cart'})).toBeInTheDocument();
  expect(container.querySelector('.cart-count')).toBeNull();
});

test('hides breadcrumbs on the activity detail page (page renders its own)', () => {
  const {container} = renderHeader('/destination/prague/activity/karting');
  expect(container.querySelector('.breadcrumbs')).toBeNull();
});

test('hides breadcrumbs on the package detail page (page renders its own)', () => {
  const {container} = renderHeader('/destination/prague/package/stag-weekend');
  expect(container.querySelector('.breadcrumbs')).toBeNull();
});
