import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import UnsubscribePage from './UnsubscribePage';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

function renderAt(url) {
    return render(
        <MemoryRouter initialEntries={[url]}>
            <UnsubscribePage />
        </MemoryRouter>
    );
}

describe('UnsubscribePage', () => {
    test('unsubscribes on confirm click', async () => {
        leadApi.unsubscribe.mockResolvedValue();
        renderAt('/unsubscribe?token=tok-1');

        await userEvent.click(screen.getByRole('button', { name: /unsubscribe/i }));

        await waitFor(() => expect(leadApi.unsubscribe).toHaveBeenCalledWith('tok-1'));
        expect(screen.getByText(/you're unsubscribed/i)).toBeInTheDocument();
    });

    test('button is disabled without a token', () => {
        renderAt('/unsubscribe');

        expect(screen.getByRole('button', { name: /unsubscribe/i })).toBeDisabled();
    });

    test('shows an error when the request fails', async () => {
        leadApi.unsubscribe.mockRejectedValue(new Error('down'));
        renderAt('/unsubscribe?token=tok-1');

        await userEvent.click(screen.getByRole('button', { name: /unsubscribe/i }));

        expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
    });
});
