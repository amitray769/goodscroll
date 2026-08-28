package com.amitray.goodscroll.data.repository

/** Order in which saved bookmarks are surfaced in the reading pager. */
enum class SortOrder {
    /** Most recently saved first. */
    NEWEST_FIRST,

    /** Oldest saved first, for working through a backlog. */
    OLDEST_FIRST,
}
