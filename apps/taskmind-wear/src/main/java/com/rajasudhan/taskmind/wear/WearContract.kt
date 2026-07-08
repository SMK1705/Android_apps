package com.rajasudhan.taskmind.wear

/**
 * The phone <-> watch Data Layer contract (#216). These constants MUST stay identical to the copy in the
 * phone module (`com.rajasudhan.taskmind.data.source.wear.WearContract`) — the two apps are separate
 * modules, so the small contract is duplicated rather than shared through a common module.
 */
object WearContract {
    /** MessageClient path: the watch sends the spoken capture text (UTF-8 bytes) to the phone. */
    const val PATH_CAPTURE = "/taskmind/capture"

    /**
     * CapabilityClient capability the PHONE app advertises (via `res/values/wear.xml`) so the watch can
     * target only a phone that actually has TaskMind installed, instead of any connected node. MUST match
     * the `android_wear_capabilities` item in the phone's wear.xml.
     */
    const val CAPABILITY_PHONE_CAPTURE = "taskmind_phone_capture"

    /** DataClient path: the phone publishes the next-due item for the watch tile to read. */
    const val PATH_NEXT_DUE = "/taskmind/next_due"

    // DataMap keys under PATH_NEXT_DUE.
    const val KEY_TITLE = "title"
    const val KEY_WHEN = "when"          // e.g. "Today · 15:00", or "" when nothing is due
    const val KEY_UPDATED_AT = "updated_at"  // ms timestamp so the DataItem changes even for identical text
}
