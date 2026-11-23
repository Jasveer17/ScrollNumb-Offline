package com.scrollnumb.app.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Connectivity helpers for offline/empty UI states.
 *
 * A network only counts as "online" when it has both INTERNET and VALIDATED
 * capabilities, so a Wi-Fi with no real internet (captive portal, router with
 * no upstream) reads as offline — which matches what the user experiences.
 */
private fun hasRealInternet(caps: NetworkCapabilities?): Boolean =
    caps != null &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true // can't tell → assume online rather than nag the user
    val active = cm.activeNetwork ?: return false
    return hasRealInternet(cm.getNetworkCapabilities(active))
}

/** Live connectivity as Compose state — recomposes when the device goes on/offline. */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = isOnline(context) }
            override fun onLost(network: Network) { state.value = isOnline(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = hasRealInternet(caps)
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return state
}
