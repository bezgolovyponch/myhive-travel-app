/**
 * @jest-environment node
 */
// Next server-renders the homepage: the hero (and its calendar) must render
// with no window, document or storage.
import {renderToString} from 'react-dom/server';
import {MemoryRouter} from 'react-router-dom';
import HeroPlanner from './HeroPlanner';
import {TripContext} from '../../context/TripContext';

test('renders on the server without a window', () => {
    const state = {tripItems: [], tripTravelers: 1, tripStartDate: '', tripEndDate: ''};
    const html = renderToString(
        <TripContext.Provider value={{state, dispatch: () => {}}}>
            <MemoryRouter>
                <HeroPlanner explorePath="/destination/prague?tab=activities"/>
            </MemoryRouter>
        </TripContext.Provider>
    );
    expect(html).toContain('Your Custom Stag Do with no problems');
    expect(html).toContain('Select travel dates');
});
