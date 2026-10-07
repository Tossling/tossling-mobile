package com.kopylovis.tossling.notifications.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.kopylovis.tossling.core.presentation.glass.CapsuleButton
import com.kopylovis.tossling.core.presentation.glass.CapsuleStyle
import com.kopylovis.tossling.core.presentation.glass.ConfirmSheet
import com.kopylovis.tossling.core.presentation.glass.FloatingBar
import com.kopylovis.tossling.core.presentation.glass.FootNote
import com.kopylovis.tossling.core.presentation.glass.GlassField
import com.kopylovis.tossling.core.presentation.glass.GlassGroup
import com.kopylovis.tossling.core.presentation.glass.GlassSheet
import com.kopylovis.tossling.core.presentation.glass.Hairline
import com.kopylovis.tossling.core.presentation.glass.PageScaffold
import com.kopylovis.tossling.core.presentation.glass.ProjectAvatar
import com.kopylovis.tossling.core.presentation.glass.SectionLabel
import com.kopylovis.tossling.core.presentation.glass.TosslingIcons
import com.kopylovis.tossling.core.presentation.glass.ValueRow
import com.kopylovis.tossling.core.presentation.glass.glassLite
import com.kopylovis.tossling.core.presentation.glass.pressable
import com.kopylovis.tossling.core.presentation.theme.ProjectColors
import com.kopylovis.tossling.core.presentation.theme.Tossling
import com.kopylovis.tossling.notifications.R
import com.kopylovis.tossling.protocol.alerts.projectInitials
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch

@Composable
internal fun ProjectContent(
    component: ProjectComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val palette = Tossling.palette
    val isCreated = state.token.isNotEmpty()
    val title = when {
        isCreated -> stringResource(R.string.project_created)
        state.isEdit -> state.name.ifBlank { stringResource(R.string.projects_title) }
        else -> stringResource(R.string.project_new)
    }
    val example = state.tokenExample.ifEmpty {
        example(
            server = state.server.ifEmpty { "https://ntfy.example.com" },
            channel = state.channel.ifEmpty { "home-nas" },
            title = stringResource(R.string.project_example_title),
            message = stringResource(R.string.project_example_message),
        )
    }
    PageScaffold(
        onBack = component::onBackClicked,
        backLabel = stringResource(R.string.notif_back),
        modifier = modifier,
        bottomPadding = 140f.dp,
        overlay = {
            FloatingBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                if (state.token.isNotEmpty()) {
                    CapsuleButton(
                        text = stringResource(R.string.project_done),
                        onClick = component::onDoneClicked,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    if (state.isEdit) {
                        CapsuleButton(
                            text = stringResource(R.string.project_delete_short),
                            onClick = component::onDeleteClicked,
                            style = CapsuleStyle.PLAIN,
                            textColor = palette.dangerInk,
                        )
                    }
                    CapsuleButton(
                        text = stringResource(if (state.isEdit) R.string.project_save else R.string.project_add),
                        onClick = component::onSaveClicked,
                        enabled = state.canSave && !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AppPicker(state = state, component = component)
            ConfirmSheet(
                visible = state.isSheetVisible,
                icon = TosslingIcons.Trash,
                title = stringResource(R.string.project_delete_title, state.name),
                text = stringResource(R.string.project_delete_text, state.channel),
                confirm = stringResource(R.string.project_delete),
                cancel = stringResource(R.string.notif_cancel),
                onConfirm = component::onDeleteConfirmed,
                onDismiss = component::onSheetDismissed,
            )
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 4f.dp, end = 4f.dp, top = 20f.dp),
            horizontalArrangement = Arrangement.spacedBy(14f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = projectInitials(name = state.name.ifBlank { "?" })
            val app = state.app
            if (app != null) {
                AppIcon(packageName = app, size = 48f.dp)
            } else {
                ProjectAvatar(initials = initials, color = ProjectColors[state.color], size = 48f.dp, iconPath = state.iconPath)
            }
            Text(text = title, style = Tossling.type.title, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isCreated) {
            GlassGroup(modifier = Modifier.padding(top = 22f.dp)) {
                ValueRow(label = stringResource(R.string.project_name), value = state.name)
                Hairline(start = 16f.dp)
                ValueRow(label = stringResource(R.string.project_channel), value = state.channel, mono = true)
            }
        } else {
            GlassGroup(modifier = Modifier.padding(top = 22f.dp)) {
                GlassField(
                    label = stringResource(R.string.project_name),
                    value = state.name,
                    onValueChange = component::onNameChanged,
                    placeholder = stringResource(R.string.project_name_hint),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Hairline(start = 16f.dp)
                GlassField(
                    label = stringResource(R.string.project_channel),
                    value = state.channel,
                    onValueChange = component::onChannelChanged,
                    placeholder = "home-nas",
                    mono = true,
                    enabled = !state.isEdit,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                )
                Hairline(start = 16f.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60f.dp)
                        .padding(start = 16f.dp, end = 10f.dp, top = 8f.dp, bottom = 8f.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.project_color), style = Tossling.type.row, color = palette.ink, modifier = Modifier.weight(1f))
                    ProjectColors.forEachIndexed { index, color ->
                        Swatch(color = color, isSelected = index == state.color, onClick = { component.onColorClicked(index = index) })
                    }
                }
                Hairline(start = 16f.dp)
                ValueRow(
                    label = stringResource(R.string.project_icon),
                    value = state.appLabel.ifEmpty {
                        stringResource(
                            when {
                                state.isCustomIcon -> R.string.project_icon_custom
                                state.iconPath.isNotEmpty() -> R.string.project_icon_ntfy
                                else -> R.string.project_icon_initials
                            },
                        )
                    },
                    modifier = Modifier.pressable(onClick = component::onIconClicked, pressedScale = 0.98f),
                )
            }
        }
        if (isCreated) {
            SectionLabel(text = stringResource(R.string.project_token))
            CopyBlock(label = "token", text = state.token, onCopy = { component.onCopyExample(example = state.token) })
            FootNote(text = stringResource(R.string.project_token_note))
        }
        SectionLabel(text = stringResource(R.string.project_example))
        CopyBlock(label = "curl", text = example, onCopy = { component.onCopyExample(example = example) })
        FootNote(text = stringResource(R.string.project_example_note))
        if (!isCreated) FootNote(text = stringResource(R.string.project_icon_note))
    }
}

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(width = 144, height = 144).asImageBitmap() }.getOrNull()
    }
    if (icon == null) {
        Box(modifier = Modifier.size(size).background(color = Tossling.palette.glassWeak, shape = CircleShape))
    } else {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    }
}

@Composable
private fun AppPicker(state: ProjectScreenState, component: ProjectComponent) {
    val palette = Tossling.palette
    val shown = remember(state.apps, state.appQuery) {
        val query = state.appQuery.trim().lowercase()
        if (query.isEmpty()) state.apps else state.apps.filter { query in it.label.lowercase() || query in it.packageName.lowercase() }
    }
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(mode = FileKitMode.Single, type = FileKitType.Image) { file ->
        scope.launch { component.onImagePicked(bytes = file?.readBytes()) }
    }
    GlassSheet(visible = state.isPickerVisible, onDismiss = component::onPickerDismissed, horizontalAlignment = Alignment.Start) {
        Text(
            text = stringResource(R.string.project_icon_pick),
            style = Tossling.type.headline,
            color = palette.ink,
            modifier = Modifier.padding(top = 14f.dp, bottom = 8f.dp),
        )
        GlassField(
            label = stringResource(R.string.project_icon_search),
            value = state.appQuery,
            onValueChange = component::onAppQueryChanged,
            placeholder = stringResource(R.string.project_icon_search_hint),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 420f.dp)) {
            item(key = "image") {
                PickerRow(selected = state.isCustomIcon, onClick = { launcher.launch() }) {
                    if (state.isCustomIcon) {
                        ProjectAvatar(initials = "", color = ProjectColors[state.color], size = 36f.dp, iconPath = state.iconPath)
                    } else {
                        Box(modifier = Modifier.size(36f.dp).background(color = palette.accentSoft, shape = CircleShape), contentAlignment = Alignment.Center) {
                            Icon(imageVector = TosslingIcons.Plus, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(18f.dp))
                        }
                    }
                    Text(text = stringResource(R.string.project_icon_file), style = Tossling.type.row, color = palette.ink)
                }
            }
            item(key = "none") {
                PickerRow(selected = state.app == null && !state.isCustomIcon, onClick = { component.onAppPicked(app = null) }) {
                    ProjectAvatar(initials = projectInitials(name = state.name.ifBlank { "?" }), color = ProjectColors[state.color], size = 36f.dp)
                    Text(text = stringResource(R.string.project_icon_none), style = Tossling.type.row, color = palette.ink)
                }
            }
            items(items = shown, key = { it.packageName }) { app ->
                PickerRow(selected = state.app == app.packageName, onClick = { component.onAppPicked(app = app) }) {
                    AppIcon(packageName = app.packageName, size = 36f.dp)
                    Text(text = app.label, style = Tossling.type.row, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52f.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f)
            .padding(vertical = 6f.dp),
        horizontalArrangement = Arrangement.spacedBy(12f.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12f.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        if (selected) Icon(imageVector = TosslingIcons.Check, contentDescription = null, tint = Tossling.palette.accentInk, modifier = Modifier.size(18f.dp))
    }
}

@Composable
private fun Swatch(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(targetValue = if (isSelected) color else Color.Transparent, label = "ring")
    Box(
        modifier = Modifier
            .size(44f.dp)
            .pressable(onClick = onClick, pressedScale = 0.85f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34f.dp)
                .border(width = 2f.dp, color = ring, shape = CircleShape)
                .padding(4f.dp)
                .background(color = color, shape = CircleShape),
        )
    }
}

@Composable
private fun CopyBlock(label: String, text: String, onCopy: () -> Unit) {
    val palette = Tossling.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassLite(shape = RoundedCornerShape(20f.dp), withShadow = false)
            .clip(RoundedCornerShape(20f.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14f.dp, end = 4f.dp, top = 2f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = Tossling.type.monoSmall.copy(fontSize = 12.sp), color = palette.ink2, modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .heightIn(min = 44f.dp)
                    .pressable(onClick = onCopy, pressedScale = 0.94f)
                    .padding(horizontal = 12f.dp),
                horizontalArrangement = Arrangement.spacedBy(6f.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = TosslingIcons.Copy, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(16f.dp))
                Text(text = stringResource(R.string.project_copy), style = Tossling.type.hint.copy(fontWeight = FontWeight.SemiBold), color = palette.accentInk)
            }
        }
        Text(
            text = text,
            style = Tossling.type.monoSmall.copy(fontSize = 12.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
            color = palette.ink,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 14f.dp, end = 14f.dp, top = 2f.dp, bottom = 14f.dp),
        )
    }
}

private fun example(server: String, channel: String, title: String, message: String): String {
    val json = """{"topic":"$channel","title":"$title","message":"$message","priority":3,"markdown":true}"""
    return "curl $server \\\n  -H \"Authorization: Bearer \$NTFY_TOKEN\" \\\n  -d '$json'"
}
