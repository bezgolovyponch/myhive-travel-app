import {render, screen} from '@testing-library/react';
import SuccessModal from './SuccessModal';
import {WHATSAPP_URL} from '../services/config';

test('shows the WhatsApp contact CTA with heading and link', () => {
  render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" />);

  expect(screen.getByText(/Contact us to get details about your trip/i)).toBeInTheDocument();
  const link = screen.getByRole('link', {name: /whatsapp/i});
  expect(link).toHaveAttribute('href', WHATSAPP_URL);
  expect(link).toHaveAttribute('target', '_blank');
  expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
});
