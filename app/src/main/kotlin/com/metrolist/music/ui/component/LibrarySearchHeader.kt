/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.ui.component.aura.AuraElevated
import com.metrolist.music.ui.component.aura.AuraFloatingChromeButton
import com.metrolist.music.ui.component.aura.AuraHeroBrush
import com.metrolist.music.ui.component.aura.AuraSpotifyGreen
import com.metrolist.music.ui.component.aura.auraFloatingIsland
import androidx.compose.material3.Text

@Composable
fun LibrarySearchHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier,
    inactiveContent: @Composable RowScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val pillShape = RoundedCornerShape(percent = 50)

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .background(AuraHeroBrush)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (isSearchActive) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .auraFloatingIsland(
                            shape = pillShape,
                            color = AuraElevated,
                            elevation = 0.dp,
                        )
                        .padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = Color.White,
                            fontSize = 16.sp,
                        ),
                    cursorBrush = SolidColor(AuraSpotifyGreen),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_library),
                                style =
                                    TextStyle(
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 16.sp,
                                    ),
                            )
                        }
                        innerTextField()
                    },
                )
            }

            AuraFloatingChromeButton(
                onClick = onBack,
                size = 36.dp,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.padding(start = 10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            inactiveContent()
        }
    }
}

@Composable
fun LibrarySearchEmptyPlaceholder(
    modifier: Modifier = Modifier,
    icon: Int = R.drawable.search,
    text: String? = null,
) {
    EmptyPlaceholder(
        icon = icon,
        text = text ?: stringResource(R.string.no_results_found),
        modifier = modifier,
    )
}
