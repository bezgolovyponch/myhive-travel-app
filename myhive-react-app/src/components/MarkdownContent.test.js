import {render, screen} from '@testing-library/react';
import MarkdownContent from './MarkdownContent';

test('renders markdown headings', () => {
    render(<MarkdownContent>{'## Getting there\n\nSome text.'}</MarkdownContent>);
    expect(screen.getByRole('heading', {level: 2, name: 'Getting there'})).toBeInTheDocument();
});

test('renders internal links for cross-page SEO linking', () => {
    render(<MarkdownContent>{'See the [Prague guide](/destination/prague).'}</MarkdownContent>);
    expect(screen.getByRole('link', {name: 'Prague guide'})).toHaveAttribute('href', '/destination/prague');
});

test('renders GFM tables', () => {
    render(<MarkdownContent>{'| City | Price |\n| --- | --- |\n| Prague | €50 |'}</MarkdownContent>);
    expect(screen.getByRole('table')).toBeInTheDocument();
});

test('does not render raw HTML from content', () => {
    const {container} = render(<MarkdownContent>{'before <img src=x onerror=alert(1)> after'}</MarkdownContent>);
    expect(container.querySelector('img')).toBeNull();
});

test('neutralizes javascript: URLs', () => {
    const {container} = render(<MarkdownContent>{'[click](javascript:alert(1))'}</MarkdownContent>);
    const link = container.querySelector('a');
    expect(link).not.toBeNull();
    expect(link.getAttribute('href') || '').not.toMatch(/javascript:/i);
});

test('splits plain paragraphs like the legacy renderer (existing posts unchanged)', () => {
    const {container} = render(<MarkdownContent>{'First paragraph.\n\nSecond paragraph.'}</MarkdownContent>);
    expect(container.querySelectorAll('p')).toHaveLength(2);
});
