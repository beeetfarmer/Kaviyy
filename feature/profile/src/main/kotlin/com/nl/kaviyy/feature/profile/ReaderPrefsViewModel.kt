package com.nl.kaviyy.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.ReaderSettingsRepository
import com.nl.kaviyy.core.model.result.AppResult
import com.nl.kaviyy.core.network.ReadingProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class ReaderPrefsUiState(
    val settings: ReadingProfileDto? = null,
    /** The default profile couldn't be loaded (offline). */
    val failed: Boolean = false,
    /** The last change didn't reach the server and was undone. */
    val saveFailed: Boolean = false,
)

/** You › Reader Preferences: edits the user's Default Kavita reading profile, so the web reader shares it. */
@HiltViewModel
class ReaderPrefsViewModel
    @Inject
    constructor(
        private val session: AuthSession,
        private val repository: ReaderSettingsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ReaderPrefsUiState())
        val state: StateFlow<ReaderPrefsUiState> = mutable.asStateFlow()
        private var raw: JsonObject? = null
        private val saving = Mutex()

        init {
            viewModelScope.launch {
                val server = session.activeServer.first() ?: return@launch
                when (val r = repository.defaultProfile(server)) {
                    is AppResult.Success -> accept(r.value)
                    is AppResult.Failure -> mutable.value = ReaderPrefsUiState(failed = true)
                }
            }
        }

        /** Shows the change at once, then saves; changes save one at a time, in order. */
        fun change(transform: (ReadingProfileDto) -> ReadingProfileDto) {
            val before = mutable.value.settings ?: return
            val after = transform(before)
            mutable.update { it.copy(settings = after, saveFailed = false) }
            viewModelScope.launch {
                saving.withLock {
                    val server = session.activeServer.first() ?: return@withLock
                    val base = raw ?: return@withLock
                    val latest = mutable.value.settings ?: after
                    when (val r = repository.saveDefault(server, base, latest)) {
                        is AppResult.Success -> raw = r.value
                        is AppResult.Failure -> mutable.update { it.copy(settings = before, saveFailed = true) }
                    }
                }
            }
        }

        private fun accept(profile: JsonObject) {
            raw = profile
            mutable.value = ReaderPrefsUiState(ReadingProfileDto.parse(profile.toString()))
        }
    }
