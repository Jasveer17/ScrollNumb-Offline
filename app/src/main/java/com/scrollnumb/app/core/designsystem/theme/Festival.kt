package com.scrollnumb.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Indian dates: IIRS holiday calendar and IMD Rashtriya Panchanga.
internal enum class Festival(val light: Color, val dark: Color) {
    GURPURAB(Color(0xFF8D5105), Color(0xFFFFC660)),
    CHRISTMAS(Color(0xFF8B3042), Color(0xFFFFABB8)),
    HARVEST(Color(0xFF566E20), Color(0xFFCEE77F)),
    EID(Color(0xFF19654D), Color(0xFF8AE6C1)),
    HOLI(Color(0xFF95346B), Color(0xFFFFACDE)),
    DIWALI(Color(0xFF994412), Color(0xFFFFB56A))
}

private val festivalDates = mapOf(
    LocalDate.of(2026, 11, 24) to Festival.GURPURAB,
    LocalDate.of(2027, 1, 15) to Festival.HARVEST,
    LocalDate.of(2027, 3, 10) to Festival.EID,
    LocalDate.of(2027, 3, 23) to Festival.HOLI,
    LocalDate.of(2027, 10, 29) to Festival.DIWALI,
    LocalDate.of(2027, 11, 14) to Festival.GURPURAB
)

internal fun festivalFor(date: LocalDate): Festival? {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).firstNotNullOfOrNull { offset ->
        val day = monday.plusDays(offset)
        if (day.monthValue == 12 && day.dayOfMonth == 25) Festival.CHRISTMAS else festivalDates[day]
    }
}

internal val LocalFestival = androidx.compose.runtime.compositionLocalOf<Festival?> { null }

internal val Festival.displayName: String get() = when (this) {
    Festival.GURPURAB -> "Guru Nanak Gurpurab"
    Festival.CHRISTMAS -> "Christmas"
    Festival.HARVEST -> "Pongal & Makar Sankranti"
    Festival.EID -> "Eid al-Fitr"
    Festival.HOLI -> "Holi"
    Festival.DIWALI -> "Diwali"
}
