package com.alarmissimo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioTrack
import com.alarmissimo.AlarmissimoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcast receiver that plays a 3-second silent audio clip to keep a Bluetooth
 * A2DP device from auto-shutting down due to inactivity.
 *
 * Self-reschedules the next occurrence via [AlarmRepository.rescheduleKeepAlive]
 * so that the interval repeats as long as the feature is enabled.
 */
class BtKeepAliveReceiver : BroadcastReceiver() {

    companion object {
        /** Stable request code for the AlarmManager PendingIntent. */
        const val REQUEST_CODE = 0x7FFE0000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext as AlarmissimoApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val async = goAsync()

        scope.launch {
            try {
                val config = appContext.repository.getSoundDeviceConfig()
                if (!config.btKeepAliveEnabled) return@launch

                playSilence()

                // Reschedule next keep-alive
                appContext.repository.rescheduleKeepAlive(config)
            } finally {
                async.finish()
            }
        }
    }

    /**
     * Plays ~3 seconds of silent stereo 16-bit PCM audio via [AudioTrack] using
     * USAGE_MEDIA to keep the A2DP connection alive.
     */
    private fun playSilence() {
        val sampleRate = 44100
        val durationSecs = 3
        val numSamples = sampleRate * durationSecs * 2 // stereo
        val buffer = ShortArray(numSamples) // all zeros = silence

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        Thread.sleep((durationSecs * 1000 + 100).toLong())
        track.stop()
        track.release()
    }
}
