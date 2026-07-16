package com.ashcroft.ripple

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; hosts the Hilt dependency graph. */
@HiltAndroidApp
class RippleApplication : Application()
