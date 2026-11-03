package dev.joeherbert.availabliss.core.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose context (Play's purchase sheet and permission rationale checks need one). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
