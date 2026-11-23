package com.scrollnumb.app.feature.relaxation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scrollnumb.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class MathProblem(val num1: Int, val num2: Int, val operator: String, val answer: Int)

enum class BreathingPhase { IDLE, INHALE, HOLD_IN, EXHALE, HOLD_OUT }

// One guided breathing session. 60s (was 30) — same 4s-per-phase box pattern.
const val BREATHING_TOTAL_SEC = 60

data class RelaxationUiState(
    val focusXp: Int = 0,
    val currentMathProblem: MathProblem? = null,
    val isMathCorrect: Boolean? = null, // null = not answered, true = correct, false = wrong
    val mathFeedbackVisible: Boolean = false,
    val breathingTimeLeft: Int = BREATHING_TOTAL_SEC,
    val isBreathingActive: Boolean = false,
    val breathingPhase: BreathingPhase = BreathingPhase.IDLE,
    val showRewardAnimation: Boolean = false,
    val rewardAmount: Int = 0,
    val rewardIsBreathing: Boolean = false
)

@HiltViewModel
class RelaxationViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val streakRepository: com.scrollnumb.app.data.repository.StreakRepository,
    private val scrollRepository: com.scrollnumb.app.data.repository.ScrollRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RelaxationUiState())
    val uiState = _uiState.asStateFlow()

    private var breathingJob: Job? = null
    private var feedbackJob: Job? = null

    init {
        viewModelScope.launch {
            userPreferences.focusXp.collect { xp ->
                _uiState.value = _uiState.value.copy(focusXp = xp)
            }
        }
        generateMathProblem()
    }

    fun generateMathProblem() {
        val isAddition = Random.nextBoolean()
        val num1 = Random.nextInt(10, 50)
        val num2 = Random.nextInt(10, 50)

        val problem = if (isAddition) {
            MathProblem(num1, num2, "+", num1 + num2)
        } else {
            val max = maxOf(num1, num2)
            val min = minOf(num1, num2)
            MathProblem(max, min, "-", max - min)
        }

        _uiState.value = _uiState.value.copy(
            currentMathProblem = problem,
            isMathCorrect = null,
            mathFeedbackVisible = false
        )
    }

    fun checkMathAnswer(answerStr: String) {
        val answer = answerStr.toIntOrNull() ?: return
        if (_uiState.value.showRewardAnimation || _uiState.value.isMathCorrect == true) return

        val state = _uiState.value
        val problem = state.currentMathProblem ?: return
        if (answer == problem.answer) {
            com.scrollnumb.app.core.util.SoundFx.playSuccess()
            _uiState.value = state.copy(
                isMathCorrect = true,
                mathFeedbackVisible = true
            )
            viewModelScope.launch {
                val gained = grantAndMeasure {
                    userPreferences.addMathFocusMinutes(1)
                    recomputeXp()
                }
                userPreferences.markMathDoneToday()
                _uiState.value = _uiState.value.copy(
                    showRewardAnimation = true,
                    rewardAmount = gained,
                    rewardIsBreathing = false
                )
                delay(2000)
                _uiState.value = _uiState.value.copy(showRewardAnimation = false)
                generateMathProblem()
            }
        } else {
            _uiState.value = state.copy(
                isMathCorrect = false,
                mathFeedbackVisible = true
            )
            feedbackJob?.cancel()
            feedbackJob = viewModelScope.launch {
                delay(1800)
                _uiState.value = _uiState.value.copy(
                    isMathCorrect = null,
                    mathFeedbackVisible = false
                )
            }
        }
    }

    fun toggleBreathing() {
        val currentState = _uiState.value
        if (currentState.isBreathingActive) {
            stopBreathing()
        } else {
            startBreathing()
        }
    }

    private fun startBreathing() {
        _uiState.value = _uiState.value.copy(
            isBreathingActive = true,
            breathingTimeLeft = BREATHING_TOTAL_SEC,
            breathingPhase = BreathingPhase.INHALE
        )
        breathingJob?.cancel()
        breathingJob = viewModelScope.launch {
            var remaining = BREATHING_TOTAL_SEC
            var phase = BreathingPhase.INHALE
            var phaseTick = 0
            val phaseDuration = 4
            while (remaining > 0) {
                delay(1000)
                phaseTick++
                if (phaseTick >= phaseDuration) {
                    phaseTick = 0
                    phase = nextPhase(phase)
                }
                remaining--
                _uiState.value = _uiState.value.copy(
                    breathingTimeLeft = remaining,
                    breathingPhase = phase
                )
            }
            val gained = grantAndMeasure {
                userPreferences.addBreatheMinutes(1)
                recomputeXp()
            }
            userPreferences.markBreatheDoneToday()
            _uiState.value = _uiState.value.copy(
                isBreathingActive = false,
                showRewardAnimation = true,
                rewardAmount = gained,
                rewardIsBreathing = true,
                breathingPhase = BreathingPhase.IDLE,
                breathingTimeLeft = BREATHING_TOTAL_SEC
            )
            delay(2000)
            _uiState.value = _uiState.value.copy(showRewardAnimation = false)
        }
    }

    private fun stopBreathing() {
        breathingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isBreathingActive = false,
            breathingPhase = BreathingPhase.IDLE,
            breathingTimeLeft = BREATHING_TOTAL_SEC
        )
    }

    /** Bank a completed dark-room rest (shares the daily decompression budget,
     *  same as breathing). Returns the XP actually granted (0 past the cap). */
    suspend fun awardDarkRoomXp(): Int {
        val gained = grantAndMeasure {
            userPreferences.addBreatheMinutes(1)
            recomputeXp()
        }
        userPreferences.markDarkRoomDoneToday()
        return gained
    }

    private fun nextPhase(phase: BreathingPhase): BreathingPhase = when (phase) {
        BreathingPhase.INHALE -> BreathingPhase.HOLD_IN
        BreathingPhase.HOLD_IN -> BreathingPhase.EXHALE
        BreathingPhase.EXHALE -> BreathingPhase.HOLD_OUT
        BreathingPhase.HOLD_OUT -> BreathingPhase.INHALE
        BreathingPhase.IDLE -> BreathingPhase.INHALE
    }

    /** Run [block] (mutates an XP input + recomputes) and return the XP actually
     *  banked — 0 once a daily cap is hit, so the reward can tell the truth. */
    private suspend fun grantAndMeasure(block: suspend () -> Unit): Int {
        val before = userPreferences.focusXp.first()
        block()
        return (userPreferences.focusXp.first() - before).coerceAtLeast(0)
    }

    /** Re-bank today's XP from the real counters (single source of truth). */
    private suspend fun recomputeXp() {
        val streak = streakRepository.getStreakStatus().first()?.currentStreak ?: 0
        val reels = scrollRepository.getTodayStats()?.totalScrolls ?: 0
        val limit = userPreferences.dailyLimit.first()
        com.scrollnumb.app.core.util.XpLedger.recompute(userPreferences, streak, reels, limit)
    }

    override fun onCleared() {
        breathingJob?.cancel()
        feedbackJob?.cancel()
        super.onCleared()
    }
}
