import {fireEvent, render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import HeroPlanner, {PLANNER_DRAFT_KEY} from './HeroPlanner';
import {TripContext} from '../../context/TripContext';
import {pushEvent} from '../../utils/analytics';

jest.mock('../../utils/analytics', () => ({pushEvent: jest.fn()}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
    ...jest.requireActual('react-router-dom'),
    useNavigate: () => mockNavigate,
}));

// DayPicker is not what these tests are about: two inputs set the range.
jest.mock('../DateRangePicker', () =>
    function MockDateRangePicker({from, to, onChange}) {
        return (
            <>
                <input data-testid="date-from" value={from} onChange={(e) => onChange(e.target.value, to)}/>
                <input data-testid="date-to" value={to} onChange={(e) => onChange(from, e.target.value)}/>
            </>
        );
    }
);

const dispatch = jest.fn();

function renderHero(trip = {}) {
    const state = {tripItems: [], tripTravelers: 1, tripStartDate: '', tripEndDate: '', ...trip};
    return render(
        <TripContext.Provider value={{state, dispatch}}>
            <MemoryRouter>
                <HeroPlanner explorePath="/destination/prague?tab=activities"/>
            </MemoryRouter>
        </TripContext.Provider>
    );
}

beforeEach(() => {
    jest.clearAllMocks();
    window.sessionStorage.clear();
});

test('shows the four-step timeline and the first-screen copy', () => {
    renderHero();
    expect(screen.getByRole('heading', {level: 1}))
        .toHaveTextContent('Prague stag do plannerBest man, not travel agent.');
    ['You pick dates and head-count', 'Three ready weekends', 'The lads pick one', 'A Prague planner calls you']
        .forEach((step) => expect(screen.getByText(step)).toBeInTheDocument());
});

test('without dates the chips offer flexible starts', () => {
    renderHero({tripTravelers: 8});
    expect(screen.getByText('Or start with')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^8 of us, any weekend in /})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: '2 nights, dates still flexible'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'What do most groups of 8 book?'})).toBeInTheDocument();
});

test('with dates the chips are built from them, and a chip fills the box', async () => {
    renderHero({tripTravelers: 10, tripStartDate: '2026-10-16', tripEndDate: '2026-10-18'});

    expect(screen.getByText('Next step — tap to fill')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', {name: 'Plan 2 nights for 10 of us — nightlife first'}));
    expect(screen.getByRole('button', {name: 'Something big on Sat 17: tanks or shooting'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Keep it chill on Sun for the groom'})).toBeInTheDocument();

    expect(screen.getByRole('textbox', {name: 'Pick the dates or write it here'}))
        .toHaveValue('Plan 2 nights for 10 of us — nightlife first');
    expect(mockNavigate).not.toHaveBeenCalled();
});

test('the people stepper writes the head-count to the trip, within the planner limits', async () => {
    renderHero({tripTravelers: 10});
    await userEvent.click(screen.getByRole('button', {name: 'More people'}));
    expect(dispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_TRAVELERS', travelers: 11});

    dispatch.mockClear();
    renderHero({tripTravelers: 2});
    await userEvent.click(screen.getAllByRole('button', {name: 'Fewer people'})[1]);
    expect(dispatch).not.toHaveBeenCalled();
});

test('picking dates writes them to the trip', () => {
    renderHero();
    fireEvent.change(screen.getByTestId('date-from'), {target: {value: '2026-10-16'}});
    expect(dispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_DATES', startDate: '2026-10-16', endDate: ''});
});

test('sending hands the message, days and head-count to the planner and opens it', async () => {
    // The cart's default head-count is 1: the hero shows 10 and plans for 10.
    renderHero({tripTravelers: 1, tripStartDate: '2026-10-16', tripEndDate: '2026-10-18'});

    await userEvent.type(screen.getByRole('textbox', {name: 'Pick the dates or write it here'}), 'Karting and a beer spa{Enter}');

    expect(JSON.parse(window.sessionStorage.getItem(PLANNER_DRAFT_KEY)))
        .toEqual({message: 'Karting and a beer spa', days: 3, groupSize: 10});
    expect(mockNavigate).toHaveBeenCalledWith('/plan');
    expect(dispatch).toHaveBeenCalledWith({type: 'UPDATE_TRIP_TRAVELERS', travelers: 10});
});

test('the Stag Do AI button with dates but no text sends the default brief', async () => {
    renderHero({tripTravelers: 10, tripStartDate: '2026-10-16', tripEndDate: '2026-10-18'});

    await userEvent.click(screen.getByRole('link', {name: /Stag Do AI/}));

    expect(JSON.parse(window.sessionStorage.getItem(PLANNER_DRAFT_KEY))).toEqual({
        message: 'Plan 2 nights in Prague for 10 of us, Fri 16 – Sun 18 Oct.', days: 3, groupSize: 10,
    });
    expect(mockNavigate).toHaveBeenCalledWith('/plan');
    expect(pushEvent).toHaveBeenCalledWith('cta_click', {cta_label: 'Stag Do AI', block: 'hero'});
});

test('the Stag Do AI button with nothing entered just opens the planner', async () => {
    renderHero();
    await userEvent.click(screen.getByRole('link', {name: /Stag Do AI/}));
    expect(window.sessionStorage.getItem(PLANNER_DRAFT_KEY)).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith('/plan');
});

test('Browse activities goes to the catalog', async () => {
    renderHero();
    const link = screen.getByRole('link', {name: 'Browse activities'});
    expect(link).toHaveAttribute('href', '/destination/prague?tab=activities');
    await userEvent.click(link);
    expect(mockNavigate).toHaveBeenCalledWith('/destination/prague?tab=activities');
});
