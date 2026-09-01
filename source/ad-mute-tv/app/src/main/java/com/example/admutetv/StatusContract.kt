package com.example.admutetv

import android.content.Context
import android.content.Intent

internal object StatusContract {
    const val ACTION_STATUS_CHANGED = "com.example.admutetv.STATUS_CHANGED"
    const val ACTION_RESTORE_NOW = "com.example.admutetv.RESTORE_NOW"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_MUTED = "muted"
    const val EXTRA_PACKAGE_NAME = "package_name"

    fun intent(
        context: Context,
        message: String,
        muted: Boolean,
        packageName: String?
    ): Intent = Intent(ACTION_STATUS_CHANGED)
        .setPackage(context.packageName)
        .putExtra(EXTRA_MESSAGE, message)
        .putExtra(EXTRA_MUTED, muted)
        .putExtra(EXTRA_PACKAGE_NAME, packageName)

    fun restoreIntent(context: Context): Intent = Intent(ACTION_RESTORE_NOW)
        .setPackage(context.packageName)
}
