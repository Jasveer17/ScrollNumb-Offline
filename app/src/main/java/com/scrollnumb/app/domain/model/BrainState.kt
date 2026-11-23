package com.scrollnumb.app.domain.model

import com.scrollnumb.app.core.util.Constants

enum class BrainState {
    FRESH, HEALTHY, MILD, WARNING, CRITICAL, NUMB;

    companion object {
        fun fromScrollCount(count: Int): BrainState = when {
            count == 0 -> FRESH
            count < Constants.THRESHOLD_MILD -> HEALTHY
            count < Constants.THRESHOLD_WARNING -> MILD
            count < Constants.THRESHOLD_CRITICAL -> WARNING
            count < Constants.THRESHOLD_NUMB -> CRITICAL
            else -> NUMB
        }
    }
}
