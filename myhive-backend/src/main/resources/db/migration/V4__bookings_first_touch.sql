-- First-touch (first-ever visit) for the "first touch -> paid" report; the
-- 90-day last-non-direct utm_* columns answer a different question.
ALTER TABLE bookings ADD COLUMN first_touch_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN first_utm_source VARCHAR(255);
ALTER TABLE bookings ADD COLUMN first_utm_campaign VARCHAR(255);
