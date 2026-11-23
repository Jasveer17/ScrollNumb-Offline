package com.scrollnumb.app.core.util

import java.time.LocalDate
import java.time.Period

/** Whole years between an ISO yyyy-MM-dd DOB and today. 0 for blank/invalid. */
object AgeUtil {
    fun ageFromDob(dobIso: String): Int = runCatching {
        Period.between(LocalDate.parse(dobIso.trim()), LocalDate.now()).years
    }.getOrDefault(0).coerceIn(0, 150)
}
