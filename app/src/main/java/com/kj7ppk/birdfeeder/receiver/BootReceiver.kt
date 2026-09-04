package com.kj7ppk.birdfeeder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kj7ppk.birdfeeder.MainActivity
import com.kj7ppk.birdfeeder.audio.AudioStreamingService
import com.kj7ppk.birdfeeder.data.SettingsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val settings = SettingsManager(context)
            if (settings.autoStreamOnLaunch.value) {
                // Start RTSP foreground audio streaming service
                AudioStreamingService.start(
                    context = context,
                    port = 8554,
                    device = null,
                    audioSource = settings.audioSource.value
                )

                // Launch MainActivity
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
