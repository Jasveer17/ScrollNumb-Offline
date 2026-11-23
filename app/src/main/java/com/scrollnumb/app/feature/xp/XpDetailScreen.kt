package com.scrollnumb.app.feature.xp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.DailyXpEntry
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.designsystem.components.WarmGrainBackground
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class XpDetailViewModel @Inject constructor(
    userPreferences: UserPreferences
) : ViewModel() {
    // Newest day first.
    val days: StateFlow<List<DailyXpEntry>> = userPreferences.weeklyXpHistory
        .map { it.asReversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyTotal: StateFlow<Int> = userPreferences.weeklyXp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XpDetailScreen(
    onBack: () -> Unit,
    viewModel: XpDetailViewModel = hiltViewModel()
) {
    val days by viewModel.days.collectAsState()
    val weeklyTotal by viewModel.weeklyTotal.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(ScrollNumbColors.Ink)) {
        WarmGrainBackground(modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "XP this week",
                            style = ScrollNumbType.BrandDisplay,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.Cream
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = ScrollNumbColors.Cream
                            )
                        }
                    },
                    expandedHeight = 48.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = "EARNED THIS WEEK",
                        style = ScrollNumbType.Microcopy,
                        fontWeight = FontWeight.Bold,
                        color = ScrollNumbColors.Saffron
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = weeklyTotal.toString(),
                            style = ScrollNumbType.NumeralMedium.copy(fontSize = 40.sp, lineHeight = 44.sp),
                            color = ScrollNumbColors.Saffron,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "XP",
                            style = ScrollNumbType.Microcopy,
                            fontWeight = FontWeight.Bold,
                            color = ScrollNumbColors.Saffron,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (days.isEmpty()) {
                    item {
                        Text(
                            text = "No XP recorded yet this week. Stay under your reel limit, keep your streak, and do focus / breathe sessions to start earning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ScrollNumbColors.CreamMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(ScrollNumbCorners.lg))
                                .background(ScrollNumbColors.InkRaised)
                                .padding(16.dp)
                        )
                    }
                } else {
                    items(days, key = { it.date }) { day ->
                        XpDayCard(day)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun XpDayCard(day: DailyXpEntry) {
    val sources = listOf(
        "Less scrolling" to day.scrollLessXp,
        "Streak" to day.streakXp,
        "Math puzzle" to day.mathXp,
        "Breathe" to day.breatheXp
    ).filter { it.second > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(ScrollNumbColors.InkRaised)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayLabel(day.date),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = ScrollNumbColors.Cream,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "+${day.total} XP",
                style = ScrollNumbType.Microcopy.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Saffron
            )
        }
        if (sources.isEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No XP earned this day.",
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            sources.forEach { (label, xp) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = ScrollNumbType.Microcopy.copy(fontSize = 13.sp),
                        color = ScrollNumbColors.CreamDim,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "+$xp",
                        style = ScrollNumbType.Microcopy.copy(fontSize = 13.sp),
                        color = ScrollNumbColors.CreamDim,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** "Mon, 09 Jun" style label from a yyyy-MM-dd key (falls back to the raw key). */
private fun dayLabel(date: String): String = try {
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(date)
    if (parsed != null)
        java.text.SimpleDateFormat("EEEE, dd MMM", java.util.Locale.getDefault()).format(parsed)
    else date
} catch (e: Exception) { date }
