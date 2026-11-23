package com.scrollnumb.app.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/**
 * Width-normalised scale factor for giant display text living in fixed-size
 * containers. Normalises to the 411dp reference width AND cancels the system
 * font scale, so hero numerals and brand words render identically on every
 * phone (AGENTS.md UI invariant). Use ONLY for display text whose container
 * cannot grow — body copy must keep honouring the user's font-size setting.
 */
@Composable
fun rememberDeviceTextScale(min: Float = 0.6f, max: Float = 1.05f): Float =
    (
        (LocalConfiguration.current.screenWidthDp / 411f) /
            LocalDensity.current.fontScale
        ).coerceIn(min, max)
