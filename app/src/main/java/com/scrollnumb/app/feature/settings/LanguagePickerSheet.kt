package com.scrollnumb.app.feature.settings

import com.scrollnumb.app.MainActivity
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scrollnumb.app.R
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbColors
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbCorners
import com.scrollnumb.app.core.designsystem.theme.ScrollNumbType
import kotlinx.coroutines.launch

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val tag: String
)

val supportedLanguages = listOf(
    LanguageOption("en", "English", "English", "EN"),
    LanguageOption("es", "Español", "Spanish", "ES"),
    LanguageOption("fr", "Français", "French", "FR"),
    LanguageOption("de", "Deutsch", "German", "DE"),
    LanguageOption("it", "Italiano", "Italian", "IT"),
    LanguageOption("pt", "Português", "Portuguese", "PT"),
    LanguageOption("ru", "Русский", "Russian", "RU"),
    LanguageOption("zh", "中文", "Chinese", "ZH"),
    LanguageOption("ja", "日本語", "Japanese", "JA"),
    LanguageOption("ko", "한국어", "Korean", "KO"),
    LanguageOption("ar", "العربية", "Arabic", "AR"),
    LanguageOption("hi", "हिन्दी", "Hindi", "HI")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    onDismiss: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel()
) {
    val currentCode by viewModel.languageCode.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var applyingCode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ScrollNumbColors.Ink,
        contentColor = ScrollNumbColors.Cream,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(ScrollNumbColors.InkWhisper)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.language_sheet_title),
                style = ScrollNumbType.BrandDisplay,
                fontWeight = FontWeight.Bold,
                color = ScrollNumbColors.Cream,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.language_sheet_sub),
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamDim,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(supportedLanguages, key = { it.code }) { lang ->
                    val isSelected = currentCode == lang.code
                    val isApplying = applyingCode == lang.code
                    LanguageRow(
                        lang = lang,
                        isSelected = isSelected,
                        isApplying = isApplying,
                        onClick = {
                            if (currentCode != lang.code && applyingCode == null) {
                                applyingCode = lang.code
                                scope.launch {
                                    viewModel.setLanguage(lang.code)
                                    val activity = context as? Activity
                                if (activity is MainActivity) {
                                    @Suppress("DEPRECATION")
                                    activity.overridePendingTransition(0, 0)
                                    activity.recreate()
                                    @Suppress("DEPRECATION")
                                    activity.overridePendingTransition(0, 0)
                                }
                                applyingCode = null
                                }
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LanguageRow(
    lang: LanguageOption,
    isSelected: Boolean,
    isApplying: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) ScrollNumbColors.Saffron.copy(alpha = 0.10f)
             else ScrollNumbColors.InkRaised

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(ScrollNumbCorners.lg))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) ScrollNumbColors.Saffron.copy(alpha = 0.18f)
                    else ScrollNumbColors.InkElevated
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lang.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) ScrollNumbColors.Saffron else ScrollNumbColors.CreamDim
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lang.nativeName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) ScrollNumbColors.Saffron else ScrollNumbColors.Cream
            )
            Text(
                text = lang.englishName,
                style = ScrollNumbType.Microcopy,
                color = ScrollNumbColors.CreamMuted
            )
        }
        AnimatedContent(
            targetState = isApplying to isSelected,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.6f, animationSpec = tween(180)))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120)))
            },
            label = "check"
        ) { (applying, selected) ->
            when {
                applying -> DotLoader()
                selected -> CheckPill()
                else -> Spacer(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CheckPill() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(ScrollNumbColors.Saffron),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.br_ic_check_circle),
            contentDescription = null,
            tint = ScrollNumbColors.Ink,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DotLoader() {
    val infinite = rememberInfiniteTransition(label = "loader")
    val a1 by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(420), repeatMode = RepeatMode.Reverse),
        label = "a1"
    )
    val a2 by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(420, delayMillis = 140), repeatMode = RepeatMode.Reverse),
        label = "a2"
    )
    val a3 by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(420, delayMillis = 280), repeatMode = RepeatMode.Reverse),
        label = "a3"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(ScrollNumbColors.Saffron.copy(alpha = a1))
        Spacer(Modifier.width(3.dp))
        Dot(ScrollNumbColors.Saffron.copy(alpha = a2))
        Spacer(Modifier.width(3.dp))
        Dot(ScrollNumbColors.Saffron.copy(alpha = a3))
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(color)
    )
}
