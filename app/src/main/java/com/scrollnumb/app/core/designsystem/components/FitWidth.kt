package com.scrollnumb.app.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints

/**
 * Measures [content] at its natural (unbounded) width and, when it is wider
 * than the incoming max width, scales it down uniformly to fit — the general
 * cure for the "giant display text clipped on narrow phones / large system
 * fonts" class of bugs (AGENTS.md UI invariant). Whatever the device width,
 * font scale, or digit count, the content shrinks to exactly fit; when it
 * already fits, it renders at its natural size, pixel-identical to a plain Text.
 *
 * Implemented as an explicit [MeasurePolicy] because intrinsic measurement must
 * be overridden by hand: the default lambda-Layout intrinsic bridge fed the
 * measure block an infinite-width placeholder, the scale math divided by it,
 * and FitWidth reported ~0 intrinsic height — which collapsed any
 * IntrinsicSize.Min ancestor (the dashboard hero card lost its undo row, the
 * counter clipped, and the brain-state glow shrank with the card).
 *
 * Use ONLY for single-line display text (hero numerals, brand words, rank
 * badges); body copy must keep honouring the user's font-size setting.
 */
@Composable
fun FitWidth(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier, measurePolicy = FitWidthMeasurePolicy)
}

private object FitWidthMeasurePolicy : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        // Natural size: fully unbounded so the child never wraps or clips.
        val child = measurables.first().measure(Constraints())
        val scale = if (constraints.hasBoundedWidth && child.width > constraints.maxWidth && child.width > 0) {
            constraints.maxWidth.toFloat() / child.width
        } else {
            1f
        }
        val w = (child.width * scale).toInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (child.height * scale).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(w, h) {
            // place() (not placeRelative): numerals/brand words render LTR even
            // in RTL locales, and the scale pivot is the top-left corner.
            child.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

    // Width queries report the child's NATURAL width so content-sized parents
    // reserve full room; parents that grant less width get the scaled height
    // below, mirroring exactly what measure() will do.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int = measurables.first().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ): Int = measurables.first().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int = scaledIntrinsicHeight(measurables.first(), width) { m, w -> m.minIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int = scaledIntrinsicHeight(measurables.first(), width) { m, w -> m.maxIntrinsicHeight(w) }

    /** Natural height, scaled by width/naturalWidth when the width is tighter. */
    private inline fun scaledIntrinsicHeight(
        child: IntrinsicMeasurable,
        width: Int,
        query: (IntrinsicMeasurable, Int) -> Int
    ): Int {
        val naturalW = child.maxIntrinsicWidth(Constraints.Infinity)
        val naturalH = query(child, if (naturalW > 0) naturalW else Constraints.Infinity)
        if (naturalW <= 0 || width <= 0) return naturalH
        if (width == Constraints.Infinity || naturalW <= width) return naturalH
        return ((naturalH.toLong() * width) / naturalW).toInt().coerceAtLeast(1)
    }
}
