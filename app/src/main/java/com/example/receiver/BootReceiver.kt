package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.manager.DnsManager

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Device booted / package replaced. Refreshing system DNS state.")
            val currentProvider = DnsManager.getCurrentProvider(context)
            Log.d(TAG, "Current DNS provider on boot is ${currentProvider.displayName}")
            com.example.service.DnsTileService.requestTileUpdate(context)
        }
    }
}
