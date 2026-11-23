package com.scrollnumb.app.core.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Analytics : Screen("analytics")
    data object Settings : Screen("settings")
    data object Legal : Screen("legal")
    data object PrivacyPolicy : Screen("privacy_policy")
    data object TermsOfService : Screen("terms_of_service")
    data object Relaxation : Screen("relaxation")
    data object RelaxationBreathe : Screen("relaxation_breathe")
    data object Auth : Screen("auth")
    data object Login : Screen("login")
    data object EditProfile : Screen("edit_profile")
    data object Leaderboard : Screen("leaderboard")
    data object Shield : Screen("shield")
    data object Profile : Screen("profile")
    data object XpDetail : Screen("xp_detail")
    data object Paywall : Screen("paywall")
}
