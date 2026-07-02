import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActivityGallery from './ActivityGallery';

const sixPhotos = [
  'https://x/p1.jpg',
  'https://x/p2.jpg',
  'https://x/p3.jpg',
  'https://x/p4.jpg',
  'https://x/p5.jpg',
  'https://x/p6.jpg',
];

test('a single photo renders without thumbnails or see-all badge', () => {
  const {container} = render(<ActivityGallery images={[sixPhotos[0]]} title="Karting" />);
  expect(container.querySelector('.activity-gallery')).toHaveAttribute('data-count', '0');
  expect(container.querySelectorAll('.ag-cell')).toHaveLength(0);
  expect(screen.queryByText(/See all photos/i)).toBeNull();
});

test('three photos render two thumbnails and no see-all badge', () => {
  const {container} = render(<ActivityGallery images={sixPhotos.slice(0, 3)} title="Karting" />);
  expect(container.querySelector('.activity-gallery')).toHaveAttribute('data-count', '2');
  expect(container.querySelectorAll('.ag-cell')).toHaveLength(2);
  expect(screen.queryByText(/See all photos/i)).toBeNull();
});

test('six photos render four thumbnails and a see-all badge with the total', () => {
  const {container} = render(<ActivityGallery images={sixPhotos} title="Karting" />);
  expect(container.querySelector('.activity-gallery')).toHaveAttribute('data-count', '4');
  expect(container.querySelectorAll('.ag-cell')).toHaveLength(4);
  expect(screen.getByText(/See all photos \(6\)/i)).toBeInTheDocument();
});

test('clicking the main photo opens the lightbox at that photo', async () => {
  render(<ActivityGallery images={sixPhotos} title="Karting" />);
  await userEvent.click(screen.getByRole('button', {name: /open photo 1/i}));
  expect(screen.getByRole('dialog', {name: /Karting photos/i})).toBeInTheDocument();
  expect(screen.getByText('1 / 6')).toBeInTheDocument();
});

test('clicking a thumbnail opens the lightbox at that photo', async () => {
  render(<ActivityGallery images={sixPhotos} title="Karting" />);
  await userEvent.click(screen.getByRole('button', {name: /open photo 3/i}));
  expect(screen.getByText('3 / 6')).toBeInTheDocument();
});

test('next and previous navigation wraps around', async () => {
  render(<ActivityGallery images={sixPhotos} title="Karting" />);
  await userEvent.click(screen.getByRole('button', {name: /open photo 1/i}));
  await userEvent.click(screen.getByRole('button', {name: /next/i}));
  expect(screen.getByText('2 / 6')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', {name: /previous/i}));
  await userEvent.click(screen.getByRole('button', {name: /previous/i}));
  expect(screen.getByText('6 / 6')).toBeInTheDocument();
});

test('Escape closes the lightbox', async () => {
  render(<ActivityGallery images={sixPhotos} title="Karting" />);
  await userEvent.click(screen.getByRole('button', {name: /open photo 1/i}));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('dialog')).toBeNull();
});

test('a single photo shows no lightbox navigation arrows', async () => {
  render(<ActivityGallery images={[sixPhotos[0]]} title="Karting" />);
  await userEvent.click(screen.getByRole('button', {name: /open photo 1/i}));
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  expect(screen.queryByRole('button', {name: /next/i})).toBeNull();
  expect(screen.queryByRole('button', {name: /previous/i})).toBeNull();
});
