package com.amitray.goodscroll.ui.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Runtime state of the `POST_NOTIFICATIONS` grant, plus a way to ask for it.
 *
 * Below API 33 the permission does not exist, so [isGranted] is always true and [requestIfNeeded]
 * does nothing.
 */
@Stable
class NotificationPermissionRequester internal constructor(
    private val grantedState: State<Boolean>,
    private val request: () -> Unit,
) {
    val isGranted: Boolean get() = grantedState.value

    /** Shows the system permission dialog, unless notifications are already allowed. */
    fun requestIfNeeded() {
        if (!isGranted) request()
    }
}

/**
 * Remembers a [NotificationPermissionRequester] bound to this composition.
 *
 * Call [NotificationPermissionRequester.requestIfNeeded] at the moment the permission earns its
 * keep — when the user sets their first reminder — rather than on app start, so the prompt has
 * obvious context. Android only shows the system dialog twice; after that the call silently does
 * nothing, which is why reminders are still persisted and scheduled when [isGranted] is false. The
 * reminder simply arrives invisibly, and the deadline stays visible in the app.
 *
 * The grant is re-read on resume, so returning from system settings updates [isGranted].
 */
@Composable
fun rememberNotificationPermissionRequester(
    onResult: (granted: Boolean) -> Unit = {},
): NotificationPermissionRequester {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val granted = remember(context) { mutableStateOf(hasNotificationPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted.value = isGranted
        currentOnResult(isGranted)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted.value = hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(granted, launcher) {
        NotificationPermissionRequester(granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
