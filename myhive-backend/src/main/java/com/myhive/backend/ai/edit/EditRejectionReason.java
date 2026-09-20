package com.myhive.backend.ai.edit;

/**
 * Why one edit could not be applied. {@code NO_PACKAGES_YET} and {@code EDIT_LIMIT} are raised by the
 * graph node around the editor, never by {@link PackageEditor} itself.
 */
public enum EditRejectionReason {
    UNKNOWN_ACTIVITY, AMBIGUOUS_ACTIVITY, NOT_IN_PACKAGE, ALREADY_IN_PACKAGE, WOULD_EMPTY_PACKAGE, NO_FREE_SLOT,
    WOULD_BREAK_SCHEDULE, NO_PACKAGES_YET, EDIT_LIMIT, INTERNAL
}
