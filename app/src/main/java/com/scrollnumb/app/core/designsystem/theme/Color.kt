package com.scrollnumb.app.core.designsystem.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware palette slots. Every screen in the app reads colors through
 * [ScrollNumbColors] property getters, which resolve against the active
 * palette held in Compose state — so switching themes recolors the entire
 * app (backgrounds, text, buttons, pill, shield) without per-screen edits.
 */
data class SNPalette(
    val saffron: Color,
    val saffronDim: Color,
    val saffronMuted: Color,
    val saffronSoft: Color,
    val coral: Color,
    val coralDim: Color,
    val sage: Color,
    val sageDim: Color,
    val ink: Color,          // app background
    val inkRaised: Color,    // card / surface
    val inkElevated: Color,  // elevated surface
    val inkLine: Color,      // border
    val inkWhisper: Color,   // subtle highlight
    val cream: Color,        // primary text
    val creamDim: Color,     // secondary text
    val creamMuted: Color,   // tertiary text
    val glassFill: Color,
    // High-energy FILL color (buttons, progress fills, ring sweeps). Always a
    // bright surface carrying fixed dark content (OnHighlight) — never used
    // as text on the theme background.
    val highlight: Color
)

private val DarkPalette = SNPalette(
    saffron = Color(0xFFFFD642),
    saffronDim = Color(0xFFE6BD2E),
    saffronMuted = Color(0x33FFD642),
    saffronSoft = Color(0x1AFFD642),
    coral = Color(0xFFFF6B4A),
    coralDim = Color(0xFFE5573A),
    sage = Color(0xFF5BC0A0),
    sageDim = Color(0xFF45A381),
    ink = Color(0xFF0A0907),
    inkRaised = Color(0xFF181410),
    inkElevated = Color(0xFF221C16),
    inkLine = Color(0xFF2A231B),
    inkWhisper = Color(0xFF3A3127),
    cream = Color(0xFFF5F0E8),
    creamDim = Color(0xCCF5F0E8),
    creamMuted = Color(0x80F5F0E8),
    glassFill = Color(0x14F5F0E8),
    highlight = Color(0xFFFFD642)
)

// Warm "gallery paper" light scheme — ivory background, pure-white cards with
// warm hairlines, espresso near-black text. Accents are the calm sage/olive
// greens of the design mockup (deep sage primary, soft olive secondary) —
// honey/amber accents stay a dark-theme identity; terracotta remains the
// over-limit warning on both.
private val LightPalette = SNPalette(
    // Sampled from the reference mockup: deep sage primary, olive progress
    // green, ivory ground with warm off-white (not pure white) cards, and
    // dark-olive espresso text. Terracotta stays the over-limit warning.
    // Golden accent family: deep antique gold for text accents (readable on
    // paper), matching the shiny gold highlight fill.
    // Saffron family nudged from antique GOLD (hue ~45°, read as brown on
    // paper) toward ORANGE (hue ~34°) — user-requested warmer accent text.
    // Same depth/contrast on the ivory ground, only the hue moved.
    saffron = Color(0xFFAD6A0E),
    saffronDim = Color(0xFF8F5609),
    saffronMuted = Color(0x33AD6A0E),
    saffronSoft = Color(0x14AD6A0E),
    coral = Color(0xFFE05B38),
    coralDim = Color(0xFFC44A2B),
    sage = Color(0xFF8CA05E),
    sageDim = Color(0xFF71844B),
    // Ground mixed toward PALE AMBER: warm honeyed ivory instead of a neutral
    // olive-ivory, so the paper reads sunlit against the sage accents.
    ink = Color(0xFFF8F1E0),
    inkRaised = Color(0xFFFDF9EE),
    inkElevated = Color(0xFFF1E7CF),
    inkLine = Color(0xFFEADFC4),
    inkWhisper = Color(0xFFDCCCA5),
    cream = Color(0xFF333A2A),
    creamDim = Color(0xCC333A2A),
    creamMuted = Color(0x8C333A2A),
    glassFill = Color(0x14333A2A),
    // Shiny gold fill (buttons, rings, nav pill, glow).
    highlight = Color(0xFFFFD234)
)

private val KawaiiPalette = SNPalette(
    saffron = Color(0xFFFF6F9C),
    saffronDim = Color(0xFFE5598A),
    saffronMuted = Color(0x33FF6F9C),
    saffronSoft = Color(0x1AFF6F9C),
    coral = Color(0xFF9D8BE8),
    coralDim = Color(0xFF8472D1),
    sage = Color(0xFF4CC49A),
    sageDim = Color(0xFF38A37E),
    ink = Color(0xFFFFF3F7),
    inkRaised = Color(0xFFFFFFFF),
    inkElevated = Color(0xFFFCE7EF),
    inkLine = Color(0xFFF4D7E3),
    inkWhisper = Color(0xFFEBC3D4),
    cream = Color(0xFF4A2B3C),
    creamDim = Color(0xCC4A2B3C),
    creamMuted = Color(0x8C4A2B3C),
    glassFill = Color(0x144A2B3C),
    highlight = Color(0xFFFF8FAB)
)

private val DonghuaPalette = SNPalette(
    saffron = Color(0xFFE8C872),
    saffronDim = Color(0xFFCFAC52),
    saffronMuted = Color(0x33E8C872),
    saffronSoft = Color(0x1AE8C872),
    coral = Color(0xFFE34234),
    coralDim = Color(0xFFC2342A),
    sage = Color(0xFF49C5B1),
    sageDim = Color(0xFF389C8C),
    ink = Color(0xFF0B1020),
    inkRaised = Color(0xFF141B30),
    inkElevated = Color(0xFF1C2440),
    inkLine = Color(0xFF273255),
    inkWhisper = Color(0xFF31407A),
    cream = Color(0xFFDCE3F2),
    creamDim = Color(0xCCDCE3F2),
    creamMuted = Color(0x80DCE3F2),
    glassFill = Color(0x14DCE3F2),
    highlight = Color(0xFFE8C872)
)

object ScrollNumbColors {

    // Active palette — Compose state, so any composable that reads a color
    // through the getters below automatically recomposes on theme change.
    private val active = mutableStateOf(DarkPalette)

    /** True when the active palette has dark surfaces. */
    val isDarkPalette: Boolean
        get() = active.value.ink == DarkPalette.ink || active.value.ink == DonghuaPalette.ink

    internal fun applyThemePalette(themeName: String, systemDark: Boolean, festival: Festival?) {
        val target = when (themeName) {
            "DARK" -> DarkPalette
            "LIGHT" -> LightPalette
            "KAWAII" -> KawaiiPalette
            "DONGHUA" -> DonghuaPalette
            else -> if (systemDark) DarkPalette else LightPalette
        }
        val seasonal = festival?.let {
            target.copy(
                saffron = if (target == DarkPalette || target == DonghuaPalette) it.dark else it.light,
                highlight = it.dark
            )
        } ?: target
        if (active.value != seasonal) active.value = seasonal
    }

    // ── Brand ────────────────────────────────────
    val Saffron: Color get() = active.value.saffron
    val SaffronDim: Color get() = active.value.saffronDim
    val SaffronMuted: Color get() = active.value.saffronMuted
    val SaffronSoft: Color get() = active.value.saffronSoft
    val Coral: Color get() = active.value.coral
    val CoralDim: Color get() = active.value.coralDim
    val Sage: Color get() = active.value.sage
    val SageDim: Color get() = active.value.sageDim
    val Highlight: Color get() = active.value.highlight
    /** Fixed dark content for anything sitting ON a Highlight fill. */
    val OnHighlight = Color(0xFF14110C)

    // ── Surfaces ─────────────────────────────────
    val Ink: Color get() = active.value.ink
    val InkRaised: Color get() = active.value.inkRaised
    val InkElevated: Color get() = active.value.inkElevated
    val InkLine: Color get() = active.value.inkLine
    val InkWhisper: Color get() = active.value.inkWhisper
    val Cream: Color get() = active.value.cream
    val CreamDim: Color get() = active.value.creamDim
    val CreamMuted: Color get() = active.value.creamMuted

    // Static light-scheme tones (M3 scheme construction only) — warm ivory neutrals.
    val Paper = Color(0xFFFAF6EF)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFE9E1D1)
    val Coal = Color(0xFF201A11)

    // ── Static M3 scheme tokens (evaluated once in Theme.kt) ──
    val PrimaryLight = Color(0xFFE08A00)
    val OnPrimaryLight = Coal
    val PrimaryContainerLight = Color(0xFFFFE7B8)
    val OnPrimaryContainerLight = Color(0xFF4A3300)

    val SecondaryLight = Color(0xFFE05B38)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFFFDDD1)
    val OnSecondaryContainerLight = Color(0xFF4A1500)

    val TertiaryLight = Color(0xFF238B66)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFCBEBDC)
    val OnTertiaryContainerLight = Color(0xFF00301F)

    val ErrorLight = Color(0xFFC03B2C)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFD9D2)
    val OnErrorContainerLight = Color(0xFF3F0805)

    val BackgroundLight = Paper
    val OnBackgroundLight = Coal
    val SurfaceLight = PaperRaised
    val OnSurfaceLight = Coal
    val SurfaceVariantLight = Color(0xFFF0E9DC)
    val OnSurfaceVariantLight = Color(0xFF544C3E)
    val OutlineLight = Color(0xFF877C69)
    val OutlineVariantLight = PaperLine

    val PrimaryDark = Color(0xFFFFD642)
    val OnPrimaryDark = Coal
    val PrimaryContainerDark = Color(0xFF4A3A00)
    val OnPrimaryContainerDark = Color(0xFFFFD642)

    val SecondaryDark = Color(0xFFFF6B4A)
    val OnSecondaryDark = Color(0xFF3A0F00)
    val SecondaryContainerDark = Color(0xFF6B2510)
    val OnSecondaryContainerDark = Color(0xFFFFD9CC)

    val TertiaryDark = Color(0xFF5BC0A0)
    val OnTertiaryDark = Color(0xFF003824)
    val TertiaryContainerDark = Color(0xFF1B5B41)
    val OnTertiaryContainerDark = Color(0xFFCFEDDF)

    val ErrorDark = Color(0xFFFF8A7A)
    val OnErrorDark = Color(0xFF3F0805)
    val ErrorContainerDark = Color(0xFF7A1F12)
    val OnErrorContainerDark = Color(0xFFFFD9D2)

    // ── Theme-relative surface aliases (used by screens as backgrounds) ──
    val BackgroundDark: Color get() = active.value.ink
    val OnBackgroundDark: Color get() = active.value.cream
    val SurfaceDark: Color get() = active.value.inkRaised
    val OnSurfaceDark: Color get() = active.value.cream
    val SurfaceVariantDark: Color get() = active.value.inkElevated
    val OnSurfaceVariantDark: Color get() = active.value.creamDim
    val OutlineDark: Color get() = active.value.inkLine
    val OutlineVariantDark: Color get() = active.value.inkWhisper

    // ── Semantic aliases used across the app ────
    val Primary: Color get() = active.value.saffron
    val Secondary: Color get() = active.value.coral
    val Warning: Color get() = active.value.saffron
    val ErrorRed = Color(0xFFC03B2C)
    val SuccessGreen: Color get() = active.value.sage
    val XpGold: Color get() = active.value.saffron
    val Error = Color(0xFFC03B2C)

    val PeachOrangeStart: Color get() = active.value.saffron
    val PeachOrangeEnd: Color get() = active.value.saffronDim
    val WidgetAmberBackground: Color get() = active.value.saffronSoft

    val BgVoid: Color get() = active.value.ink
    val BgSurface: Color get() = active.value.inkRaised
    val BgElevated: Color get() = active.value.inkElevated
    val AccentPrimary: Color get() = active.value.saffron
    val AccentSecondary: Color get() = active.value.coral
    val StrikeRed: Color get() = active.value.coral
    val TextPrimary: Color get() = active.value.cream
    val TextSecondary: Color get() = active.value.creamDim
    val TextAccent: Color get() = active.value.saffron
    val GlassFill: Color get() = active.value.glassFill

    val StateHealthy: Color get() = active.value.sage
    val StateFresh: Color get() = active.value.sage
    val StateMild: Color get() = active.value.saffron
    val StateWarning: Color get() = active.value.saffron
    val StateCritical: Color get() = active.value.coral
    val StateNumb: Color get() = active.value.creamMuted

    val StreakFlameStart: Color get() = active.value.saffron
    val StreakFlameEnd: Color get() = active.value.coral

    // ── Kawaii / Donghua base constants (M3 scheme construction) ──
    val KawaiiPink = Color(0xFFFF8FAB)
    val KawaiiPinkDeep = Color(0xFFE5598A)
    val KawaiiLavender = Color(0xFFB8A9F0)
    val KawaiiMint = Color(0xFF8FE3C0)
    val KawaiiCream = Color(0xFFFFF6F9)
    val KawaiiSurface = Color(0xFFFFFFFF)
    val KawaiiInk = Color(0xFF4A3B47)
    val KawaiiLine = Color(0xFFF4DCE6)

    val DonghuaNight = Color(0xFF0B1020)
    val DonghuaInkWash = Color(0xFF141B30)
    val DonghuaInkDeep = Color(0xFF1C2440)
    val DonghuaVermilion = Color(0xFFE34234)
    val DonghuaJade = Color(0xFF49C5B1)
    val DonghuaGold = Color(0xFFE8C872)
    val DonghuaMist = Color(0xFFDCE3F2)
    val DonghuaLine = Color(0xFF273255)
}
