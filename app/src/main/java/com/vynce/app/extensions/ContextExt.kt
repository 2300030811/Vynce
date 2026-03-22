package com.vynce.app.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import com.vynce.app.constants.TabletUiKey
import com.vynce.app.utils.dataStore
import com.vynce.app.utils.get


fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

fun Context.supportsWideScreen() : Boolean {
    val config = resources.configuration
    return config.screenWidthDp >= 600
}

/**
 * If screen is large enough to support tablet UI mode.
 * Current screen must be at least 600dp.
 */
fun Context.tabMode(): Boolean {
    val config = resources.configuration
    val isTablet = config.smallestScreenWidthDp >= 600
    return (isTablet || dataStore.get(TabletUiKey, false)) && config.screenWidthDp >= 600
}

fun Context.isPowerSaver(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isPowerSaveMode
}
