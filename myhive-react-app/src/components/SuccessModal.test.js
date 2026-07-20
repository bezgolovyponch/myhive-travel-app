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

test('no deposit CTA without a bookingId', () => {
    render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" />);

    expect(screen.queryByRole('button', {name: /pay 30% deposit/i})).not.toBeInTheDocument();
});

// Online payment is temporarily disabled (PAYMENTS_ENABLED=false): the deposit CTA
// must stay hidden even for a booking that could otherwise be paid.
test('no deposit CTA even with a bookingId while payment is disabled', () => {
    render(<SuccessModal isOpen onClose={jest.fn()} userName="Sam" userEmail="sam@x.com" bookingId="b-1" />);

    expect(screen.queryByRole('button', {name: /pay 30% deposit/i})).not.toBeInTheDocument();
});
