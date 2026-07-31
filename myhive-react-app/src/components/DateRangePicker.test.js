import { render, screen, fireEvent } from '@testing-library/react';
import DateRangePicker from './DateRangePicker';

describe('DateRangePicker', () => {
  beforeEach(() => {
    // jsdom does not implement scrollIntoView — the collapsible calendar calls it on reopen.
    window.HTMLElement.prototype.scrollIntoView = jest.fn();
  });

  describe('date fields display', () => {
    it('shows placeholder in both fields when no dates selected', () => {
      render(<DateRangePicker from="" to="" onChange={() => {}} />);
      expect(screen.getAllByText('Add date')).toHaveLength(2);
    });

    it('hides from placeholder when from is set', () => {
      render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryAllByText('Add date')).toHaveLength(1);
    });


    it('hides footer when only from is set', () => {
      render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryByText(/night/)).not.toBeInTheDocument();
    });
  });

  describe('active field state', () => {
    it('marks from field active when no dates selected', () => {
      const { container } = render(<DateRangePicker from="" to="" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).toHaveClass('drp-field--active');
      expect(fields[1]).not.toHaveClass('drp-field--active');
    });

    it('marks to field active when only from is set', () => {
      const { container } = render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).not.toHaveClass('drp-field--active');
      expect(fields[1]).toHaveClass('drp-field--active');
    });

    it('marks no field active when both dates are set', () => {
      const { container } = render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      const fields = container.querySelectorAll('.drp-field');
      expect(fields[0]).not.toHaveClass('drp-field--active');
      expect(fields[1]).not.toHaveClass('drp-field--active');
    });
  });

  describe('collapsible mode (inline booking panel)', () => {
    it('hides the calendar once the range is complete', () => {
      render(<DateRangePicker collapsible from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      expect(screen.queryAllByRole('grid')).toHaveLength(0);
    });

    it('shows the calendar while the range is incomplete', () => {
      render(<DateRangePicker collapsible from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryAllByRole('grid').length).toBeGreaterThan(0);
    });

    it('clicking a filled field reopens the calendar', () => {
      render(<DateRangePicker collapsible from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      fireEvent.click(screen.getByText('Start'));
      expect(screen.queryAllByRole('grid').length).toBeGreaterThan(0);
    });

    it('default mode keeps the calendar always visible even with a complete range', () => {
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      expect(screen.queryAllByRole('grid').length).toBeGreaterThan(0);
    });

    it('scrolls the calendar fully into view when a field click reopens it', () => {
      const scrollIntoView = jest.fn();
      window.HTMLElement.prototype.scrollIntoView = scrollIntoView;
      render(<DateRangePicker collapsible from="2026-06-12" to="2026-06-15" onChange={() => {}} />);

      fireEvent.click(screen.getByText('Start'));

      expect(scrollIntoView).toHaveBeenCalledWith({behavior: 'smooth', block: 'nearest'});
    });

    it('does not scroll on initial render when the calendar starts visible', () => {
      const scrollIntoView = jest.fn();
      window.HTMLElement.prototype.scrollIntoView = scrollIntoView;
      render(<DateRangePicker collapsible from="" to="" onChange={() => {}} />);

      expect(scrollIntoView).not.toHaveBeenCalled();
    });
  });

  describe('clear buttons', () => {
    it('calls onChange("","") when from clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: 'Clear start date' }));
      expect(onChange).toHaveBeenCalledWith('', '');
    });

    it('calls onChange(from,"") when to clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: 'Clear end date' }));
      expect(onChange).toHaveBeenCalledWith('2026-06-12', '');
    });
  });

  describe('popover mode', () => {
    it('hides the calendar until a field is clicked', () => {
      const { container } = render(<DateRangePicker from="" to="" onChange={() => {}} popover />);
      expect(container.querySelector('.drp-cal-wrap')).toBeNull();

      fireEvent.click(container.querySelector('.drp-field'));

      expect(container.querySelector('.drp-pop .drp-cal-wrap')).toBeInTheDocument();
    });

    it('renders a single month inside the popover', () => {
      const { container } = render(<DateRangePicker from="" to="" onChange={() => {}} popover />);
      fireEvent.click(container.querySelector('.drp-field'));

      expect(container.querySelectorAll('.drp-month')).toHaveLength(1);
    });

    it('closes the popover when the range is completed', () => {
      const onChange = jest.fn();
      const { container, rerender } = render(
        <DateRangePicker from="2026-06-12" to="" onChange={onChange} popover />
      );
      fireEvent.click(container.querySelector('.drp-field'));
      expect(container.querySelector('.drp-pop')).toBeInTheDocument();

      const grid = container.querySelector('[role="grid"]');
      const dayButtons = grid.querySelectorAll('.drp-day-btn');
      fireEvent.click(dayButtons[dayButtons.length - 1]);

      rerender(<DateRangePicker from="2026-06-12" to="2026-06-20" onChange={onChange} popover />);
      expect(container.querySelector('.drp-pop')).toBeNull();
    });

    it('fills the start date and stays open on the first day click', () => {
      // Regression: a mousedown-based outside-click guard using contains(e.target)
      // closed the popover before the day's click fired onSelect, so Start never
      // filled. The guard now reads composedPath(), which survives react-day-picker
      // re-rendering the clicked day out of the tree.
      const onChange = jest.fn();
      const { container } = render(
        <div>
          <span data-testid="outside">out</span>
          <DateRangePicker from="" to="" onChange={onChange} popover />
        </div>
      );
      fireEvent.click(container.querySelector('.drp-field'));
      expect(container.querySelector('.drp-pop')).toBeInTheDocument();

      const grid = container.querySelector('[role="grid"]');
      const enabledDay = grid.querySelector('.drp-day:not(.drp-disabled):not(.drp-hidden) .drp-day-btn');
      fireEvent.mouseDown(enabledDay);
      fireEvent.click(enabledDay);

      expect(onChange).toHaveBeenCalled();
      const [from] = onChange.mock.calls[onChange.mock.calls.length - 1];
      expect(from).toBeTruthy();
      // With only a start picked, the calendar stays open for the end date.
      expect(container.querySelector('.drp-pop')).toBeInTheDocument();
    });

    it('closes the popover on outside click', () => {
      render(
        <div>
          <span data-testid="outside">out</span>
          <DateRangePicker from="" to="" onChange={() => {}} popover />
        </div>
      );
      fireEvent.click(document.querySelector('.drp-field'));
      expect(document.querySelector('.drp-pop')).toBeInTheDocument();

      fireEvent.mouseDown(screen.getByTestId('outside'));

      expect(document.querySelector('.drp-pop')).toBeNull();
    });

    it('does not affect default mode calendar visibility', () => {
      const { container } = render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      expect(container.querySelector('.drp-cal-wrap')).toBeInTheDocument();
      expect(container.querySelector('.drp-pop')).toBeNull();
    });
  });
});
