package com.nl.kaviyy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nl.kaviyy.core.designsystem.component.GroupedList
import com.nl.kaviyy.core.designsystem.component.GroupedRow
import com.nl.kaviyy.core.designsystem.component.KavitaBottomSheet
import com.nl.kaviyy.core.designsystem.component.SheetHeader
import com.nl.kaviyy.core.designsystem.icon.KavitaIcons
import com.nl.kaviyy.core.designsystem.theme.Appearance
import com.nl.kaviyy.core.designsystem.theme.Kavita
import com.nl.kaviyy.core.designsystem.theme.KavitaSpacing
import com.nl.kaviyy.core.designsystem.theme.KavitaTheme
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult

/**
 * [derived] Server switcher opened from the ServerPill (DESIGN.md §ServerPill names it but doesn't draw it), built
 * from existing sheet + grouped-list parts. Tap a server to switch; the active one can be removed (two taps).
 */
@Composable
internal fun ServerSwitcherSheet(
    servers: List<Server>,
    active: ServerId,
    onSwitch: (ServerId) -> Unit,
    onAdd: () -> Unit,
    onRemove: (ServerId) -> Unit,
    onDismiss: () -> Unit,
) {
    KavitaBottomSheet(onDismiss = onDismiss) {
        ServerSwitcherContent(servers, active, onSwitch, onAdd, onRemove, onDismiss)
    }
}

@Composable
internal fun ServerSwitcherContent(
    servers: List<Server>,
    active: ServerId,
    onSwitch: (ServerId) -> Unit,
    onAdd: () -> Unit,
    onRemove: (ServerId) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmRemove by rememberSaveable(active.value) { mutableStateOf(false) }
    SheetHeader(stringResource(R.string.servers_title), stringResource(R.string.servers_done), onDismiss)
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GroupedList {
            servers.forEachIndexed { i, server ->
                val isActive = server.id == active
                GroupedRow(
                    server.name,
                    value = server.url.host.takeIf { it != server.name },
                    onClick = if (isActive) null else ({ onSwitch(server.id) }),
                    showChevron = false,
                    divider = i < servers.lastIndex,
                ) {
                    if (isActive) {
                        Icon(
                            KavitaIcons.Check,
                            stringResource(R.string.servers_active),
                            Modifier.size(18.dp),
                            tint = Kavita.colors.accent,
                        )
                    }
                }
            }
        }
        GroupedList { GroupedRow(stringResource(R.string.servers_add), onClick = onAdd) }
        GroupedList {
            val name = servers.firstOrNull { it.id == active }?.name.orEmpty()
            Text(
                if (confirmRemove) {
                    stringResource(
                        R.string.servers_remove_confirm,
                    )
                } else {
                    stringResource(R.string.servers_remove, name)
                },
                style = Kavita.type.body,
                color = Kavita.colors.destructive,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = KavitaSpacing.rowHeight)
                        .clickable(role = Role.Button) { if (confirmRemove) onRemove(active) else confirmRemove = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ServerSwitcherLightPreview() = ServerSwitcherPreview(Appearance.Light)

@Preview
@Composable
private fun ServerSwitcherDarkPreview() = ServerSwitcherPreview(Appearance.Dark)

@Composable
private fun ServerSwitcherPreview(appearance: Appearance) =
    KavitaTheme(appearance = appearance) {
        val url = (ServerUrl.parse("https://kavita.home.lan") as ServerUrlResult.Valid).url
        Column(Modifier.background(Kavita.colors.background)) {
            ServerSwitcherContent(listOf(Server(ServerId(1), "Home Server", url)), ServerId(1), {}, {}, {}, {})
        }
    }
