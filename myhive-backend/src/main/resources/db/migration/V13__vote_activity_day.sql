-- The day of the trip an activity on the ballot is planned for (1 = the first day), so the organiser's
-- dashboard and the friends' vote show the plan day by day. Null for votes started without a day plan
-- (from the cart, the quiz, or before this column existed): those are listed without days, as before.
ALTER TABLE vote_session_activities ADD COLUMN day_number INTEGER;
