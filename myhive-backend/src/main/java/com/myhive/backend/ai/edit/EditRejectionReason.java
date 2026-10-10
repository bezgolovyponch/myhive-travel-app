package com.myhive.backend.ai.edit;

/**
 * Why one edit could not be applied. {@code NO_PACKAGES_YET} and {@code EDIT_LIMIT} are raised by the
 * graph node around the editor, never by {@link PackageEditor} itself.
 */
public enum EditRejectionReason {
    UNKNOWN_ACTIVITY, AMBIGUOUS_ACTIVITY, NOT_IN_PACKAGE, ALREADY_IN_PACKAGE, WOULD_EMPTY_PACKAGE, NO_FREE_SLOT,
    WOULD_BREAK_SCHEDULE, NO_PACKAGES_YET, EDIT_LIMIT, INTERNAL,
    /** A move to the day the activity is on already: nothing to do, and the chat says so. */
    ALREADY_ON_DAY,
    /** The activity offers no extra by that name; {@code detail} lists the ones it does offer. */
    UNKNOWN_EXTRA,
    /** The extra is chosen on that activity already. */
    ALREADY_HAS_EXTRA,
    /** An extra asked to be removed that was never chosen on that activity. */
    EXTRA_NOT_SELECTED
}
