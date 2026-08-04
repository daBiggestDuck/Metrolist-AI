/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.ui.component.aura.AuraBottomSheet
import com.metrolist.music.ui.component.aura.AuraSecondaryAction
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.screens.settings.AccountSettings
import kotlinx.coroutines.delay

/**
 * Aura bottom-sheet dialog — fingerprint-style popup rising from the nav area.
 * Drop-in replacement for the previous centered Material Dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AuraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp)
                    .imePadding(),
        ) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Box(Modifier.align(Alignment.CenterHorizontally)) {
                        icon()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (title != null) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        Box(
                            Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally),
                        ) {
                            title()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            content()

            if (buttons != null) {
                Spacer(Modifier.height(20.dp))
                FlowRow(modifier = Modifier.align(Alignment.End)) {
                    CompositionLocalProvider(LocalContentColor provides AuraSpotifyGreen) {
                        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsDialog(
    onDismiss: () -> Unit,
    latestVersionName: String,
) {
    val navController = LocalNavController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    AuraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
        ) {
            AccountSettings(
                navController = navController,
                onClose = onDismiss,
                latestVersionName = latestVersionName,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title =
            if (titleBar != null) {
                { Row { titleBar() } }
            } else if (title != null) {
                {
                    Text(
                        text = title,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            } else {
                null
            },
        buttons = {
            if (onReset != null) {
                Row(modifier = Modifier.weight(1f)) {
                    AuraSecondaryAction(onClick = { onReset() }) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (onCancel != null) {
                AuraSecondaryAction(onClick = { onCancel() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            AuraSecondaryAction(onClick = { onConfirm() }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    AuraBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(vertical = 8.dp)
                    .imePadding(),
        ) {
            LazyColumn(content = content)
        }
    }
}

@Composable
fun InfoLabel(text: String) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(4.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit = {},
    // new multi-field support
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    autoDismiss: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        buttons = {
            AuraSecondaryAction(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid =
                textFields?.all { isInputValid(it.second.text) }
                    ?: isInputValid(legacyFieldState.value.text)

            AuraSecondaryAction(enabled = isValid,
                onClick = {
                    if (autoDismiss) onDismiss()
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f, fill = false),
        ) {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                                keyboardType = keyboardType,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (onDoneMultiple != null) {
                                        onDoneMultiple(textFields.map { it.second.text })
                                        if (autoDismiss) onDismiss()
                                    }
                                },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (index < textFields.size - 1) 12.dp else 0.dp)
                                .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            } else {
                // Wrap in Box with pointerInput to prevent double-click crashes in TextClassifier
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { /* Consume double-tap to prevent TextClassifier crash */ },
                                )
                            },
                ) {
                    TextField(
                        value = legacyFieldState.value,
                        onValueChange = { legacyFieldState.value = it },
                        placeholder = placeholder,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                                keyboardType = keyboardType,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    onDone(legacyFieldState.value.text)
                                    if (autoDismiss) onDismiss()
                                },
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                }
            }

            extraContent?.invoke()
        }
    }
}
