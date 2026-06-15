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

beforeEach(() => {
    window.dataLayer = [];
});

test('renders the heading and the GTM mount point with a placeholder until the policy is published', () => {
    const {container} = renderPage();

    expect(screen.getByRole('heading', {name: /privacy policy/i})).toBeInTheDocument();
    expect(container.querySelector('#privacy-policy-content')).toBeInTheDocument();
    expect(screen.getByText(/being finalised/i)).toBeInTheDocument();
});

test('signals GTM that the privacy policy view is ready', () => {
    renderPage();

    expect(window.dataLayer).toEqual(
        expect.arrayContaining([{event: 'privacy_policy_view'}])
    );
});
