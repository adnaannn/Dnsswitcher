package com.example.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.data.AppPreferences

object BatteryOptimizationHelper {

    /**
     * Checks whether battery optimizations are ignored for this application package.
     * When battery optimizations are ignored (whitelisted), Android will not kill the
     * local VPN loopback process or delay Quick Settings tile toggling when the screen is off.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the system dialog prompting the user to whitelist the app from battery optimization.
     * Falls back to general battery optimization settings if direct package prompt is unavailable.
     */
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to general battery optimization settings page
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (fallbackEx: Exception) {
                false
            }
        }
    }

    /**
     * Determines whether the app should present the battery optimization guidance dialog.
     */
    fun shouldPromptForBatteryOptimization(context: Context): Boolean {
        val prefs = AppPreferences.getInstance(context)
        if (prefs.dismissedBatteryOptimizationPrompt) {
            return false
        }
        return !isBatteryOptimizationIgnored(context)
    }

    /**
     * Records user choice to dismiss or suppress the prompt.
     */
    fun dismissPrompt(context: Context) {
        AppPreferences.getInstance(context).dismissedBatteryOptimizationPrompt = true
    }

    /**
     * Resets prompt dismissal so the user can be reminded later or check from settings.
     */
    fun resetPromptDismissal(context: Context) {
        AppPreferences.getInstance(context).dismissedBatteryOptimizationPrompt = false
    }
}
