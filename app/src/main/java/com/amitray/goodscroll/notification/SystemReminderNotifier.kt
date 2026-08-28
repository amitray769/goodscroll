package com.amitray.goodscroll.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.amitray.goodscroll.R
import com.amitray.goodscroll.data.local.Bookmark
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Reading this creates the channel exactly once per process. [NotificationManagerCompat] is
     * the API 26 guard: channels do not exist below it, so the call is a no-op there and minSdk 24
     * stays safe without an explicit version check.
     */
    private val channelId: String by lazy {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, IMPORTANCE)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_description))
                .setShowBadge(true)
                .build()
        )
        CHANNEL_ID
    }

    override fun notifyReminder(bookmark: Bookmark): Boolean {
        if (!canPostNotifications()) return false

        val body = bookmark.excerpt?.trim()?.takeIf { it.isNotEmpty() }
            ?: hostOf(bookmark.url)
            ?: context.getString(R.string.reminder_notification_fallback_body)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(bookmark.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(context.getString(R.string.reminder_notification_subtext))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Mirrors IMPORTANCE for the pre-26 devices that ignore the channel.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openBookmarkIntent(bookmark.id))
            .build()

        notificationManager.notify(notificationId(bookmark.id), notification)
        return true
    }

    /**
     * Opens the bookmark through [TaskStackBuilder] so a cold start still gets a sensible task,
     * and back from the reader exits the app rather than dropping into an empty stack.
     */
    private fun openBookmarkIntent(bookmarkId: Long): PendingIntent {
        val stack = TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(ReminderDeepLink.createIntent(context, bookmarkId))
        return checkNotNull(
            stack.getPendingIntent(
                // Per-bookmark request code, otherwise a second reminder would reuse the first
                // PendingIntent and open the wrong bookmark.
                notificationId(bookmarkId),
                // FLAG_IMMUTABLE is mandatory from API 31 and correct everywhere: the system must
                // not be able to rewrite which bookmark this opens.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        ) { "TaskStackBuilder produced no PendingIntent for bookmark $bookmarkId" }
    }

    /**
     * `POST_NOTIFICATIONS` only exists from API 33; below that the permission is not defined, so
     * checking it would report denied and silence every reminder.
     */
    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")

    /** Stable per-bookmark id, so re-firing a reminder replaces its notification. */
    private fun notificationId(bookmarkId: Long): Int = bookmarkId.toInt()

    private companion object {
        const val CHANNEL_ID = "reading_reminders"

        /**
         * DEFAULT rather than HIGH on purpose: a reminder the user set for themselves deserves a
         * sound, but this is an anti-doomscrolling app and it should not hijack the screen with a
         * heads-up banner.
         */
        const val IMPORTANCE = NotificationManagerCompat.IMPORTANCE_DEFAULT
    }
}
