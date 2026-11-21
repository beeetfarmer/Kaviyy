package com.nl.kaviyy.feature.lists

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nl.kaviyy.core.data.AuthSession
import com.nl.kaviyy.core.data.CblPreview
import com.nl.kaviyy.core.data.ReadingListRepository
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.PrimaryButton
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaShapes
import com.nl.kaviyy.core.model.dispatch.Dispatcher
import com.nl.kaviyy.core.model.dispatch.KaviyyDispatchers
import com.nl.kaviyy.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** CBL files are small XML; anything bigger is refused before it leaves the device. */
internal const val CBL_MAX_BYTES = 2 * 1024 * 1024
private const val NAME_MAX = 100
private const val MISSING_SHOWN = 8

enum class CblProblem { TooBig, Unreadable, Failed }

data class CblImportState(
    val open: Boolean = false,
    val busy: Boolean = false,
    val preview: CblPreview? = null,
    val problem: CblProblem? = null,
    /** The imported list's id, to open once. */
    val imported: Int? = null,
)

/** [derived] Import CBL: pick a file (SAF, no permission), see how much Kavita matched, then import. */
@HiltViewModel
class CblImportViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:Dispatcher(KaviyyDispatchers.IO) private val io: CoroutineDispatcher,
        private val session: AuthSession,
        private val lists: ReadingListRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(CblImportState())
        val state: StateFlow<CblImportState> = mutable.asStateFlow()

        fun picked(uri: Uri) {
            mutable.value = CblImportState(open = true, busy = true)
            viewModelScope.launch {
                val server = session.activeServer.first()
                val file = withContext(io) { readCbl(context, uri) }
                mutable.value =
                    when {
                        file == null -> {
                            CblImportState(open = true, problem = CblProblem.Unreadable)
                        }

                        file.second.size > CBL_MAX_BYTES -> {
                            CblImportState(open = true, problem = CblProblem.TooBig)
                        }

                        server == null -> {
                            CblImportState(open = true, problem = CblProblem.Failed)
                        }

                        else -> {
                            when (val r = lists.previewCbl(server, file.first, file.second)) {
                                is AppResult.Success -> CblImportState(open = true, preview = r.value)
                                is AppResult.Failure -> CblImportState(open = true, problem = CblProblem.Failed)
                            }
                        }
                    }
            }
        }

        fun import() {
            val preview = mutable.value.preview ?: return
            if (mutable.value.busy) return
            mutable.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val server = session.activeServer.first()
                val r = server?.let { lists.importCbl(it, preview) }
                mutable.value =
                    if (r is AppResult.Success) {
                        CblImportState(imported = r.value)
                    } else {
                        mutable.value.copy(busy = false, problem = CblProblem.Failed)
                    }
            }
        }

        fun close() {
            mutable.value = CblImportState()
        }

        fun consumeImported() = mutable.update { it.copy(imported = null) }
    }

/**
 * The picked file's name (made safe to send: letters, digits, `. _ -` and spaces, ending in .cbl) and at most
 * [CBL_MAX_BYTES] + 1 bytes, so an oversized file is detected without reading all of it. Null if unreadable.
 */
internal fun readCbl(
    context: Context,
    uri: Uri,
): Pair<String, ByteArray>? =
    runCatching {
        val resolver = context.contentResolver
        val display =
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment
        val bytes =
            resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (out.size() <= CBL_MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: return null
        safeCblName(display) to bytes
    }.getOrNull()

internal fun safeCblName(display: String?): String {
    val base =
        display
            .orEmpty()
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .take(NAME_MAX)
            .ifEmpty { "reading-list" }
    return if (base.endsWith(".cbl", ignoreCase = true)) base else "$base.cbl"
}

/** Reading Lists header pill: accent container, import icon, "Import CBL". */
@Composable
internal fun ImportCblPill(onClick: () -> Unit) =
    Row(
        Modifier
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(KavitaShapes.pill)
                .background(Kavita.colors.accentContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KavitaIcons.Import, null, Modifier.size(15.dp), tint = Kavita.colors.accent)
            Text(
                stringResource(R.string.lists_import_cbl),
                style = Kavita.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = Kavita.colors.accent,
            )
        }
    }

@Composable
internal fun CblImportSheet(
    state: CblImportState,
    onClose: () -> Unit,
    onImport: () -> Unit,
) = KavitaBottomSheet(onDismiss = onClose) {
    SheetHeader(stringResource(R.string.lists_import_cbl), stringResource(R.string.lists_cancel), onClose)
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val p = state.preview
        when {
            state.problem != null -> {
                SheetText(stringResource(problemText(state.problem)))
            }

            p == null -> {
                SheetText(stringResource(R.string.lists_cbl_checking))
            }

            else -> {
                Text(p.name, style = Kavita.type.title3, color = Kavita.colors.label)
                SheetText(stringResource(R.string.lists_cbl_found, p.found, p.total))
                if (p.missing.isNotEmpty()) {
                    SheetText(stringResource(R.string.lists_cbl_missing))
                    p.missing.take(MISSING_SHOWN).forEach {
                        Text("· $it", style = Kavita.type.footnote, color = Kavita.colors.labelSecondary)
                    }
                    val more = p.missing.size - MISSING_SHOWN
                    if (more > 0) SheetText(pluralStringResource(R.plurals.lists_cbl_more, more, more))
                }
            }
        }
        if (p != null) {
            PrimaryButton(
                stringResource(R.string.lists_cbl_import),
                onImport,
                Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = p.importable && !state.busy,
            )
        }
    }
}

private fun problemText(problem: CblProblem) =
    when (problem) {
        CblProblem.TooBig -> R.string.lists_cbl_too_big
        CblProblem.Unreadable -> R.string.lists_cbl_unreadable
        CblProblem.Failed -> R.string.lists_cbl_failed
    }

@Composable
private fun SheetText(text: String) = Text(text, style = Kavita.type.callout, color = Kavita.colors.labelSecondary)
