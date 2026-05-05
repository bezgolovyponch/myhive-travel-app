import { render, screen, fireEvent } from '@testing-library/react';
import DateRangePicker from './DateRangePicker';

describe('DateRangePicker', () => {
  describe('date fields display', () => {
    it('shows placeholder in both fields when no dates selected', () => {
      render(<DateRangePicker from="" to="" onChange={() => {}} />);
      expect(screen.getAllByText('Add date')).toHaveLength(2);
    });

    it('hides from placeholder when from is set', () => {
      render(<DateRangePicker from="2026-06-12" to="" onChange={() => {}} />);
      expect(screen.queryAllByText('Add date')).toHaveLength(1);
    });

    it('shows night count footer when both dates are set', () => {
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={() => {}} />);
      expect(screen.getByText(/3 night/)).toBeInTheDocument();
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

    it('calls onChange("","") when footer clear button clicked', () => {
      const onChange = jest.fn();
      render(<DateRangePicker from="2026-06-12" to="2026-06-15" onChange={onChange} />);
      fireEvent.click(screen.getByRole('button', { name: /Clear dates/i }));
      expect(onChange).toHaveBeenCalledWith('', '');
    });
  });
});
