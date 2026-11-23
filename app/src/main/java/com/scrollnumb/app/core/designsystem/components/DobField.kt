package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** ISO yyyy-MM-dd -> "dd/MM/yyyy", or "" if unparseable/empty. */
fun formatDob(iso: String): String = runCatching {
    if (iso.isBlank()) "" else LocalDate.parse(iso).format(DISPLAY)
}.getOrDefault("")

/**
 * Date-of-birth field: a rounded, tappable surface that shows the DOB as
 * dd/MM/yyyy and opens a Material date picker. Value is stored/emitted as ISO
 * yyyy-MM-dd (locale-stable, matches the app's date keys). Future dates are not
 * selectable — you can't be born tomorrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DobPickerField(
    iso: String,
    onIso: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "DD/MM/YYYY"
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = formatDob(iso)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ScrollNumbColors.InkRaised)
            .border(1.dp, ScrollNumbColors.InkLine, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = display.ifEmpty { placeholder },
            color = if (display.isEmpty()) ScrollNumbColors.CreamMuted else ScrollNumbColors.Cream,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        val nowUtcDays = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        val initialMillis = runCatching {
            if (iso.isBlank()) null
            else LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()

        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = 1920..LocalDate.now(ZoneOffset.UTC).year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val days = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    return days <= nowUtcDays
                }

                override fun isSelectableYear(year: Int): Boolean =
                    year <= LocalDate.now(ZoneOffset.UTC).year
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onIso(date.toString()) // ISO yyyy-MM-dd
                    }
                    showDialog = false
                }) { Text("OK", color = ScrollNumbColors.Saffron, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = ScrollNumbColors.CreamMuted)
                }
            }
        ) {
            DatePicker(state = state, showModeToggle = true)
        }
    }
}
