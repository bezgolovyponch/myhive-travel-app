# 2026-10-07 — AI planner: the trip draft is split by day again

## Why

The trip-draft redesign (PR #36) flattened the package into one list of rows, with the day pushed into
each row's subtitle ("2 h · Day 1"). The owner asked for the day breakdown back: on a two- or three-day
trip the organiser could not see at a glance what happens on which day, and a day with nothing in it
disappeared from the draft altogether.

## What changed

`AiPackageView` renders the draft as one section per `pkg.days` entry:

- the heading is the date (`Fri 16 Oct`, when the trip's dates match the plan) or "Day N", followed by
  the planner's name for the day ("Landing night") — hidden while the copy is still being written
  (`textsPending`) and whenever the title is still the backend's stock "Day N"/"Tag N"
  (`PlaceholderTexts.dayTitle`, which survives past `textsPending` when a package's copy call failed,
  the model left the title blank, or an edit reset it): the label already says it, "Day 1 · Day 1"
  would not;
- the day's activity count sits on the right of the heading when there is one; the draft's total count
  stays in its head;
- the rows are unchanged (picture, name → card, ×, "AI added" badge) but their subtitle carries the
  duration only — the day is the heading now — and a row without a duration has no subtitle at all;
- an empty day stays on the page with a quiet line, "Nothing planned yet" (`result.emptyDay`, EN/DE),
  so a two-day trip never reads as one; the line says it, so no "0 activities" next to it. Adding to it
  goes through the dock's "+ Add …" tags and the chat, as for any day.

Each day is a `<section aria-labelledby>` headed by an `h2`, so it is a named region for assistive
technology and for the tests. CSS: `.aip-day`, `.aip-day-head`, `.aip-day-title`, `.aip-day-name`,
`.aip-day-count`, `.aip-day-empty` in `AiPackageView.css`.

## Tests

`AiPlannerPage.test.js`: the dated trip shows "Fri 16 Oct · Landing night" with its two rows and
"2 activities", "Sat 17 Oct · Big day" and "Sun 18 Oct · Recovery" with "Nothing planned yet" and
"0 activities"; a trip whose dates do not match the plan shows "Day 1 · Landing night"; while the copy is
pending the heading is "Day 1" alone; rows show "2 h" without a day.
