package com.cullect.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cullect.app.ui.theme.OnSelectedContainer
import com.cullect.app.ui.theme.SelectedContainer

/**
 * One look for "this option is chosen" everywhere: a peach chip with dark text, in both themes.
 * Material's default (secondaryContainer) was a dim brown on the dark cards, only 1.35:1 against
 * them, so a selected segment hardly differed from the unselected ones. Unselected options are
 * transparent with a visible outline and normal text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun cullectSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = SelectedContainer,
    activeContentColor = OnSelectedContainer,
    activeBorderColor = SelectedContainer,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
    inactiveBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
)

@Composable
fun cullectFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = SelectedContainer,
    selectedLabelColor = OnSelectedContainer,
)
