package com.amitray.goodscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single saved link.
 *
 * Times are stored as epoch millis rather than `Instant` + type converters so they can be handed
 * straight to WorkManager delays and AlarmManager trigger times without conversion.
 *
 * [url] is uniquely indexed: sharing the same link twice must not create a second row.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val url: String,

    val title: String,

    /** When the bookmark was saved, epoch millis. */
    val timestamp: Long,

    /** Optional excerpt scraped from the shared page, shown on the reading card. */
    val excerpt: String? = null,

    /** Optional hero image scraped from the shared page, shown on the reading card. */
    val imageUrl: String? = null,

    /** Set once the user has read the bookmark in the pager. */
    @ColumnInfo(defaultValue = "0")
    val isRead: Boolean = false,

    /** Reminder time in epoch millis, or null when no reminder is set. */
    val reminderDeadline: Long? = null,

    /**
     * True once the reminder notification has been posted. Kept separate from [reminderDeadline]
     * so the deadline stays visible in the UI after firing, and so reboot rescheduling can tell a
     * still-pending reminder from one that has already been delivered.
     */
    @ColumnInfo(defaultValue = "0")
    val reminderFired: Boolean = false,
)
