import {render} from '@testing-library/react';
import VoteDemoCard from './VoteDemoCard';

test('renders the four demo vote rows', () => {
    const {container} = render(<VoteDemoCard/>);
    expect(container.querySelectorAll('.vc-row')).toHaveLength(4);
    expect(container.textContent).toContain('Bar Crawl');
    expect(container.querySelector('.vote-card').getAttribute('aria-hidden')).toBe('true');
});
