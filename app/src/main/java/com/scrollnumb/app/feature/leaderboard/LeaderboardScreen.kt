package com.scrollnumb.app.feature.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.core.datastore.UserPreferences
import com.scrollnumb.app.core.designsystem.components.WorldHeader

@Composable
fun LeaderboardScreen(userPreferences: UserPreferences, onXpClick: () -> Unit = {}) {
    val weekly by userPreferences.weeklyXp.collectAsState(initial = 0)
    val lifetime by userPreferences.focusXp.collectAsState(initial = 0)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorldHeader("Your progress", "$weekly XP this week")
        Text("$lifetime lifetime XP", style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onXpClick) { Text("View XP breakdown") }
    }
}
