import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Link, Routes, Route} from 'react-router-dom';
import ScrollToTop from './ScrollToTop';

beforeEach(() => {
    window.scrollTo = jest.fn();
});

function renderWithRouter() {
    return render(
        <MemoryRouter initialEntries={['/']}>
            <ScrollToTop/>
            <Routes>
                <Route path="/" element={<Link to="/destination/prague/activity/karting">go</Link>}/>
                <Route path="/destination/:destinationSlug/activity/:slug" element={<div>detail</div>}/>
            </Routes>
        </MemoryRouter>
    );
}

test('scrolls the window to the top when the pathname changes', async () => {
    renderWithRouter();
    window.scrollTo.mockClear();

    await userEvent.click(screen.getByText('go'));

    expect(await screen.findByText('detail')).toBeInTheDocument();
    expect(window.scrollTo).toHaveBeenCalledWith(0, 0);
});
