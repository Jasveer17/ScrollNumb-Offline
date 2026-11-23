package com.scrollnumb.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.theme.LocalFestival
import com.scrollnumb.app.core.designsystem.theme.Festival
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.displayName
import com.scrollnumb.app.core.util.AvatarHelper

@Composable
fun WorldHeader(section: String, detail: String = "", choose: Boolean = false, ratio: Float = 0f) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("design_world", 0) }
    var selected by remember { mutableStateOf(prefs.getString("layout", "Avatar Stage") ?: "Avatar Stage") }
    val festival = LocalFestival.current
    val layout = if (festival != null) "Festival Studio" else selected
    val male = AvatarHelper.getGender(context).equals("Male", true)
    val art = when {
        ratio >= .25f -> AvatarHelper.getScrollStatusAvatar(context, ratio)
        layout == "Focus Quest" && male -> R.drawable.seasonal_quest_male
        layout == "Focus Quest" -> R.drawable.seasonal_quest_female
        layout == "Festival Studio" && (festival == Festival.DIWALI || festival == null) ->
            if (male) R.drawable.seasonal_festival_male else R.drawable.seasonal_festival_female
        else -> AvatarHelper.getScrollStatusAvatar(context, ratio)
    }
    val accent = ScrollNumbColors.Saffron
    val surface = ScrollNumbColors.InkRaised
    val text = ScrollNumbColors.Cream
    val tall = section == "Home"
    Column {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(surface, androidx.compose.ui.graphics.lerp(surface, accent, .17f), surface)),
                RoundedCornerShape(28.dp)
            ).padding(16.dp)
        ) {
            Column {
                Text(festival?.displayName ?: layout, color = accent, style = MaterialTheme.typography.labelLarge)
                if (tall) {
                    Box(Modifier.fillMaxWidth().height(if (layout == "Focus Quest") 200.dp else 160.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(accent.copy(alpha = .10f), size.height * .44f)
                            drawOval(accent.copy(alpha = .54f), Offset(size.width*.1f,size.height*.56f),
                                Size(size.width*.8f,size.height*.25f), style = Stroke(1.dp.toPx()))
                        }
                        AnimatedAvatar(art, Modifier.fillMaxHeight().fillMaxWidth(.88f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(section, style = MaterialTheme.typography.headlineMedium, color = text)
                        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = ScrollNumbColors.CreamDim)
                    }
                    if (!tall) AnimatedAvatar(art, Modifier.size(76.dp))
                }
            }
        }
        if (choose) {
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { open = true }) { Text("Home layout · " + selected) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    listOf("Avatar Stage", "Festival Studio", "Focus Quest").forEach { world ->
                        DropdownMenuItem(text = { Text(world) }, onClick = {
                            selected = world
                            prefs.edit().putString("layout", world).apply()
                            open = false
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
