import {render, screen} from '@testing-library/react';
import {HelmetProvider} from 'react-helmet-async';
import PrivacyPolicyPage from './PrivacyPolicyPage';

function renderPage() {
    return render(
        <HelmetProvider>
            <PrivacyPolicyPage/>
        </HelmetProvider>
    );
}

test('renders the heading and a placeholder until the policy is published', () => {
    renderPage();

    expect(screen.getByRole('heading', {name: /privacy policy/i})).toBeInTheDocument();
    expect(screen.getByText(/being finalised/i)).toBeInTheDocument();
});
