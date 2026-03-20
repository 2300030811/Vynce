package com.vynce.app.ui.utils

import androidx.navigation.NavController

val NavController.canNavigateUp: Boolean
    get() = currentBackStackEntry?.destination?.parent?.route != null

fun NavController.backToMain() {
    do { navigateUp() } while (canNavigateUp)
}

