package com.adhdkiller.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.adhdkiller.app.AppContainer

/** The dependency container, available to every screen without plumbing it through navigation. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}
