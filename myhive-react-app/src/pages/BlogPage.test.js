import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import BlogPage from './BlogPage';
import api from '../services/api';

beforeEach(() => {
  jest.spyOn(api, 'getBlogPosts').mockResolvedValue([]);
});

function renderBlog() {
  return render(
    <HelmetProvider>
      <MemoryRouter>
        <BlogPage />
      </MemoryRouter>
    </HelmetProvider>
  );
}

test('renders posts without a page-hero or Blog heading', async () => {
  api.getBlogPosts.mockResolvedValue([
    {id: '1', slug: 'p1', title: 'First Post', excerpt: 'hi'},
  ]);   // overrides the beforeEach default for this test
  const {container} = renderBlog();

  await waitFor(() => expect(screen.getByText('First Post')).toBeInTheDocument());
  expect(container.querySelector('.page-hero')).toBeNull();
  expect(screen.queryByRole('heading', {name: 'Blog'})).toBeNull();
});
