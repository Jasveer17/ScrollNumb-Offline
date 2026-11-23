package com.scrollnumb.app.core.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.scrollnumb.app.R

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int
) {
    data object Home : BottomNavItem(Screen.Dashboard.route, R.string.nav_home, R.drawable.br_ic_home)
    data object Stats : BottomNavItem(Screen.Analytics.route, R.string.nav_stats, R.drawable.br_ic_chart)
    data object Profile : BottomNavItem(Screen.Profile.route, R.string.nav_profile, R.drawable.br_ic_user)
}
