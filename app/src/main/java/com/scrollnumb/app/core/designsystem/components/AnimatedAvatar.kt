package com.scrollnumb.app.core.designsystem.components

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Movie
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.scrollnumb.app.R
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private fun loopFor(id: Int): Int = when (id) {
    R.drawable.female_avatar_1 -> R.raw.loop_female_avatar_1
    R.drawable.female_avatar_2 -> R.raw.loop_female_avatar_2
    R.drawable.female_avatar_3 -> R.raw.loop_female_avatar_3
    R.drawable.female_avatar_4 -> R.raw.loop_female_avatar_4
    R.drawable.female_avatar_5 -> R.raw.loop_female_avatar_5
    R.drawable.female_avatar_6_1 -> R.raw.loop_female_avatar_6_1
    R.drawable.female_avatar_6 -> R.raw.loop_female_avatar_6
    R.drawable.female_avatar_7 -> R.raw.loop_female_avatar_7
    R.drawable.female_avatar_fresh -> R.raw.loop_female_avatar_fresh
    R.drawable.female_avatar_little_tired -> R.raw.loop_female_avatar_little_tired
    R.drawable.female_avatar_more_tired -> R.raw.loop_female_avatar_more_tired
    R.drawable.female_avatar_shield -> R.raw.loop_female_avatar_shield
    R.drawable.female_avatar_very_tired -> R.raw.loop_female_avatar_very_tired
    R.drawable.male_avatar_1 -> R.raw.loop_male_avatar_1
    R.drawable.male_avatar_2 -> R.raw.loop_male_avatar_2
    R.drawable.male_avatar_3 -> R.raw.loop_male_avatar_3
    R.drawable.male_avatar_4 -> R.raw.loop_male_avatar_4
    R.drawable.male_avatar_5 -> R.raw.loop_male_avatar_5
    R.drawable.male_avatar_6_1 -> R.raw.loop_male_avatar_6_1
    R.drawable.male_avatar_6 -> R.raw.loop_male_avatar_6
    R.drawable.male_avatar_7 -> R.raw.loop_male_avatar_7
    R.drawable.male_avatar_fresh -> R.raw.loop_male_avatar_fresh
    R.drawable.male_avatar_little_tired -> R.raw.loop_male_avatar_little_tired
    R.drawable.male_avatar_more_tired -> R.raw.loop_male_avatar_more_tired
    R.drawable.male_avatar_shield -> R.raw.loop_male_avatar_shield
    R.drawable.male_avatar_very_tired -> R.raw.loop_male_avatar_very_tired
    R.drawable.seasonal_festival_female -> R.raw.loop_seasonal_festival_female
    R.drawable.seasonal_festival_male -> R.raw.loop_seasonal_festival_male
    R.drawable.seasonal_quest_female -> R.raw.loop_seasonal_quest_female
    R.drawable.seasonal_quest_male -> R.raw.loop_seasonal_quest_male
    else -> 0
}

@Suppress("DEPRECATION")
@Composable
internal fun avatarPainter(id: Int): Painter {
    val loop = loopFor(id)
    if (loop == 0 || !ValueAnimator.areAnimatorsEnabled()) return painterResource(id)
    val resources = LocalContext.current.resources
    val movie = remember(resources, loop) {
        resources.openRawResource(loop).use { Movie.decodeStream(it) }
    } ?: return painterResource(id)
    val time = remember(movie) { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(movie, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            val start = SystemClock.uptimeMillis()
            while (true) {
                time.intValue = ((SystemClock.uptimeMillis() - start) % movie.duration().coerceAtLeast(1)).toInt()
                delay(50)
            }
        }
    }
    return remember(movie) {
        object : Painter() {
            val pixels = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(pixels)
            val frame = pixels.asImageBitmap()
            override val intrinsicSize = Size(movie.width().toFloat(), movie.height().toFloat())
            override fun DrawScope.onDraw() {
                movie.setTime(time.intValue)
                pixels.eraseColor(android.graphics.Color.TRANSPARENT)
                movie.draw(canvas, 0f, 0f)
                scale(size.width / intrinsicSize.width, size.height / intrinsicSize.height,
                    androidx.compose.ui.geometry.Offset.Zero) { drawImage(frame) }
            }
        }
    }
}

@Composable
internal fun AnimatedAvatar(art: Int, modifier: Modifier = Modifier) {
    Image(avatarPainter(art), null, modifier)
}
