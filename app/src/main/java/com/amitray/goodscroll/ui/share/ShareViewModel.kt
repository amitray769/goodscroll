package com.amitray.goodscroll.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amitray.goodscroll.domain.SaveSharedLinkResult
import com.amitray.goodscroll.domain.SaveSharedLinkUseCase
import com.amitray.goodscroll.util.SharedLinkParser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the share intent flow. Kept separate from `ReaderViewModel` so that the reader keeps its
 * single responsibility, and so a share that arrives while the app is closed does not have to spin
 * up the whole reading screen before the link is safe on disk.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val saveSharedLink: SaveSharedLinkUseCase,
) : ViewModel() {

    private val _status = MutableStateFlow<ShareStatus>(ShareStatus.Idle)
    val status: StateFlow<ShareStatus> = _status.asStateFlow()

    /**
     * Saves whatever another app put in `EXTRA_TEXT`. Ignores a second call while a save is in
     * flight so a double delivery of the same intent cannot race with itself.
     */
    fun onLinkShared(sharedText: String?, subject: String? = null) {
        if (_status.value is ShareStatus.Saving) return
        _status.value = ShareStatus.Saving

        viewModelScope.launch {
            _status.value = when (val result = saveSharedLink(sharedText, subject)) {
                is SaveSharedLinkResult.Saved -> ShareStatus.Saved(
                    id = result.id,
                    title = result.title,
                    domain = SharedLinkParser.sourceDomain(result.url),
                )

                is SaveSharedLinkResult.AlreadySaved -> ShareStatus.AlreadySaved(
                    id = result.id,
                    title = result.title,
                    domain = SharedLinkParser.sourceDomain(result.url),
                )

                SaveSharedLinkResult.NoLinkFound -> ShareStatus.NoLinkFound
            }
        }
    }

    /** Called once the outcome has been shown, so it is not shown twice. */
    fun onStatusHandled() {
        _status.value = ShareStatus.Idle
    }
}
