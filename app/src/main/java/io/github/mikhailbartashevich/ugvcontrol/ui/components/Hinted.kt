package io.github.mikhailbartashevich.ugvcontrol.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable

/** Wraps a control so a long press shows [hint] as a tooltip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hinted(hint: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
        content = content,
    )
}
