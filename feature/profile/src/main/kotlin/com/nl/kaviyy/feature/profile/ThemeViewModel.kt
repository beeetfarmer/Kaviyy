package com.nl.kaviyy.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.datastore.AppSettings
import com.nl.kaviyy.core.datastore.AppSettingsStore
import com.nl.kaviyy.core.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onAccent: (String) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onLanguage: () -> Unit = {},
)

/** You › Theme (DESIGN.md §2.4). */
@HiltViewModel
class ThemeViewModel
    @Inject
    constructor(
        private val store: AppSettingsStore,
    ) : ViewModel() {
        val state: StateFlow<AppSettings> =
            store.settings.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                AppSettings(),
            )

        fun setTheme(mode: ThemeMode) = update { it.copy(theme = mode) }

        fun setAccent(name: String) = update { it.copy(accent = name) }

        fun setDynamicColor(on: Boolean) = update { it.copy(dynamicColor = on) }

        private fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { store.update(transform) }
        }
    }
