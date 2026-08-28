package com.amitray.goodscroll.ui.share

/**
 * Progress of the link the user shared into the app.
 *
 * Modelled as a state rather than a one-off event because the confirmation screen renders it, and
 * because a share that arrives while the reader is open has to survive a configuration change.
 */
sealed interface ShareStatus {

    /** Nothing shared, or the result has already been shown. */
    data object Idle : ShareStatus

    data object Saving : ShareStatus

    data class Saved(val id: Long, val title: String, val domain: String?) : ShareStatus

    data class AlreadySaved(val id: Long, val title: String, val domain: String?) : ShareStatus

    /** The shared text contained nothing that looks like a web link. */
    data object NoLinkFound : ShareStatus

    /** True once the share has finished and the user can be told about it. */
    val isFinished: Boolean
        get() = this is Saved || this is AlreadySaved || this is NoLinkFound

    /** The bookmark the reader should scroll to, when there is one. */
    val bookmarkId: Long?
        get() = when (this) {
            is Saved -> id
            is AlreadySaved -> id
            else -> null
        }
}
