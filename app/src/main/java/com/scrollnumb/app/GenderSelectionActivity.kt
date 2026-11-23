package com.scrollnumb.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.scrollnumb.app.core.datastore.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GenderSelectionActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private var videoMale: VideoView? = null
    private var videoFemale: VideoView? = null
    private var shimmerAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        lifecycleScope.launch {
            val completed = userPreferences.onboardingCompleted.first()
            if (completed) {
                navigateToMain()
                return@launch
            }

            setContentView(R.layout.activity_gender_selection)
            val root = findViewById<View>(R.id.rootLayout)
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(root)
            applyHolographicTitle()
            playEntranceAnimation()
            setupVideos()
            setupClickListeners()
        }
    }

    /**
     * Holographic title: an iridescent gradient shader swept across the text
     * by animating the shader matrix. Saffron → cream → coral → sage cycle.
     */
    private fun applyHolographicTitle() {
        val title = findViewById<TextView>(R.id.tvTitle) ?: return
        title.post {
            val w = title.width.toFloat().coerceAtLeast(1f)
            val shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    0xFFF5F0E8.toInt(), // cream
                    0xFFFFD642.toInt(), // saffron
                    0xFFFF6B4A.toInt(), // coral
                    0xFF5BC0A0.toInt(), // sage
                    0xFFF5F0E8.toInt()  // cream
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f),
                Shader.TileMode.MIRROR
            )
            title.paint.shader = shader
            val matrix = Matrix()
            shimmerAnimator = ValueAnimator.ofFloat(0f, 2f * w).apply {
                duration = 6000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    matrix.setTranslate(anim.animatedValue as Float, 0f)
                    shader.setLocalMatrix(matrix)
                    title.invalidate()
                }
                start()
            }
        }
    }

    private fun playEntranceAnimation() {
        val eyebrow = findViewById<View>(R.id.tvEyebrow)
        val rule = findViewById<View>(R.id.headerRule)
        val title = findViewById<View>(R.id.tvTitle)
        val subtitle = findViewById<View>(R.id.tvSubtitle)
        val cardMale = findViewById<View>(R.id.cardMale)
        val cardFemale = findViewById<View>(R.id.cardFemale)
        val hint = findViewById<View>(R.id.tvHint)

        // Entrance offsets in dp, not raw px — px offsets made the slide-in
        // distance vary with screen density (device-portability invariant).
        val density = resources.displayMetrics.density
        eyebrow?.apply { alpha = 0f; translationY = -12f * density }
        rule?.apply { alpha = 0f; scaleX = 0f; pivotX = 0f }
        title?.apply { alpha = 0f; translationY = 28f * density }
        subtitle?.apply { alpha = 0f; translationY = 16f * density }
        cardMale?.apply { alpha = 0f; translationX = -160f * density }
        cardFemale?.apply { alpha = 0f; translationX = 160f * density }
        hint?.apply { alpha = 0f }

        val decel = DecelerateInterpolator(2.2f)
        val overshoot = OvershootInterpolator(0.9f)

        eyebrow?.animate()?.alpha(1f)?.translationY(0f)
            ?.setDuration(420)?.setStartDelay(60)?.setInterpolator(decel)?.start()
        rule?.animate()?.alpha(1f)?.scaleX(1f)
            ?.setDuration(560)?.setStartDelay(140)?.setInterpolator(decel)?.start()
        title?.animate()?.alpha(1f)?.translationY(0f)
            ?.setDuration(540)?.setStartDelay(180)?.setInterpolator(decel)?.start()
        subtitle?.animate()?.alpha(1f)?.translationY(0f)
            ?.setDuration(480)?.setStartDelay(280)?.setInterpolator(decel)?.start()
        cardMale?.animate()?.alpha(1f)?.translationX(0f)
            ?.setDuration(620)?.setStartDelay(380)?.setInterpolator(overshoot)?.start()
        cardFemale?.animate()?.alpha(1f)?.translationX(0f)
            ?.setDuration(620)?.setStartDelay(500)?.setInterpolator(overshoot)
            ?.withEndAction { triggerHaptic(soft = true) }?.start()
        hint?.animate()?.alpha(1f)
            ?.setDuration(420)?.setStartDelay(820)?.start()
    }

    private fun setupVideos() {
        videoMale = findViewById(R.id.videoMale)
        videoFemale = findViewById(R.id.videoFemale)

        val maleUri = Uri.parse("android.resource://$packageName/${R.raw.male_avatar}")
        val femaleUri = Uri.parse("android.resource://$packageName/${R.raw.female_avatar}")

        videoMale?.apply {
            setVideoURI(maleUri)
            setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.start()
            }
        }

        videoFemale?.apply {
            setVideoURI(femaleUri)
            setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.start()
            }
        }
    }

    private fun setupClickListeners() {
        val cardMale = findViewById<MaterialCardView>(R.id.cardMale)
        val cardFemale = findViewById<MaterialCardView>(R.id.cardFemale)

        cardMale.setOnClickListener { selectAndNavigate(cardMale, cardFemale, "Male") }
        cardFemale.setOnClickListener { selectAndNavigate(cardFemale, cardMale, "Female") }
    }

    private fun selectAndNavigate(chosen: MaterialCardView, other: MaterialCardView, gender: String) {
        chosen.isClickable = false
        other.isClickable = false

        triggerHaptic(soft = false)

        // Persist gender selection
        getSharedPreferences("scroll_numb_prefs", Context.MODE_PRIVATE)
            .edit().putString("USER_GENDER", gender).apply()
        lifecycleScope.launch { userPreferences.setProfileGender(gender) }

        // Chosen: saffron border + press-release pop. Other: slides off and fades.
        chosen.strokeColor = android.graphics.Color.parseColor("#FFD642")
        chosen.strokeWidth = (2 * resources.displayMetrics.density).toInt()

        val slideOut = if (gender == "Male") 220f else -220f
        other.animate()
            .alpha(0f).translationX(slideOut)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()

        chosen.animate()
            .scaleX(0.96f).scaleY(0.96f)
            .setDuration(90)
            .withEndAction {
                chosen.animate()
                    .scaleX(1.04f).scaleY(1.04f)
                    .setDuration(160)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction {
                        chosen.animate()
                            .alpha(0f)
                            .setDuration(180)
                            .withEndAction { navigateToMain() }
                            .start()
                    }.start()
            }.start()
    }

    private fun triggerHaptic(soft: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                if (soft) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_HEAVY_CLICK
            )
        } else {
            VibrationEffect.createOneShot(if (soft) 20 else 50, if (soft) 80 else 200)
        }
        vibrator.vibrate(effect)
    }

    override fun onResume() {
        super.onResume()
        videoMale?.start()
        videoFemale?.start()
    }

    override fun onPause() {
        super.onPause()
        videoMale?.pause()
        videoFemale?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        shimmerAnimator?.cancel()
        shimmerAnimator = null
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
