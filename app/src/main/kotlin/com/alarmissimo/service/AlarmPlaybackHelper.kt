package com.alarmissimo.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import com.alarmissimo.R
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.VoiceProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Handles the full alarm playback sequence for a triggered alarm:
 * 1. Gong sound via [MediaPlayer] at [AlarmEvent.gongVolume] level
 * 2. Time announcement via [TextToSpeech] (if [AlarmEvent.timePlayback] is true)
 * 3. Custom message via [TextToSpeech] (if [AlarmEvent.message] is non-empty)
 *
 * TTS uses the settings from [VoiceProfile]. Audio is routed through [AudioManager.STREAM_ALARM].
 *
 * @param context Application context.
 */
class AlarmPlaybackHelper(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * Plays the full alarm sequence synchronously (call from a coroutine).
     *
     * @param event The specific alarm-event to play.
     * @param voiceProfile The [VoiceProfile] used for TTS settings.
     * @param endEventName Name of the end event (alarm-set relative mode only),
     *   used for [AlarmEvent.durationPlayback] utterance.
     */
    suspend fun play(event: AlarmEvent, voiceProfile: VoiceProfile, endEventName: String = "", useBluetooth: Boolean = false) {
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "alarmissimo:alarm_wakelock"
        )
        wakeLock.acquire(60_000L)
        try {
            if (event.gong != "none") {
                playGong(event.gong, useBluetooth)
            }
            if (event.timePlayback || event.message.isNotEmpty() || event.durationPlayback) {
                val utterances = buildList {
                    if (event.timePlayback) add(buildTimeUtterance(event.time))
                    if (event.durationPlayback && endEventName.isNotEmpty()) {
                        add("Es sind noch ${event.offsetMinutes} Minuten bis $endEventName.")
                    }
                    if (event.message.isNotEmpty()) add(event.message)
                }
                speakUtterances(utterances, voiceProfile, useBluetooth)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun playGong(gongId: String, useBluetooth: Boolean) {
        when {
            gongId.startsWith("system:") -> {
                val uri = Uri.parse(gongId.removePrefix("system:"))
                playGongUri(uri, useBluetooth)
            }
            else -> {
                val rawResId = gongRawRes(gongId) ?: return
                playGongRaw(rawResId, useBluetooth)
            }
        }
    }

    private suspend fun playGongRaw(rawResId: Int, useBluetooth: Boolean = false) {
        val usage = if (useBluetooth) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val audioSessionId = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .generateAudioSessionId()
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer.create(
                context, rawResId, alarmAttrs, audioSessionId
            ) ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }
            mp.setVolume(1f, 1f)
            mp.setOnInfoListener { _, _, _ -> true } // consume info events to avoid "unhandled events" warning
            mp.setOnCompletionListener { it.release(); cont.resume(Unit) }
            mp.setOnErrorListener { it, _, _ -> it.release(); cont.resume(Unit); true }
            cont.invokeOnCancellation { runCatching { mp.stop() }; runCatching { mp.release() } }
            mp.start()
        }
    }

    private suspend fun playGongUri(uri: Uri, useBluetooth: Boolean = false) {
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer()
            val done = AtomicBoolean(false)
            fun finish(player: MediaPlayer) {
                if (done.compareAndSet(false, true)) {
                    runCatching {
                        player.setOnCompletionListener(null)
                        player.setOnErrorListener(null)
                        player.setOnInfoListener(null)
                        if (player.isPlaying) player.stop()
                    }
                    runCatching { player.release() }
                    cont.resume(Unit)
                }
            }
            cont.invokeOnCancellation { finish(mp) }
            try {
                val usage = if (useBluetooth) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(context, uri)
                mp.isLooping = false
                mp.setVolume(1f, 1f)
                mp.setOnPreparedListener { player ->
                    player.isLooping = false
                    val durationMs = player.duration.coerceIn(500, 30_000).toLong()
                    player.start()
                    Handler(Looper.getMainLooper()).postDelayed({ finish(player) }, durationMs + 100L)
                }
                mp.setOnCompletionListener { finish(it) }
                mp.setOnErrorListener    { it, _, _ -> finish(it); true }
                mp.prepareAsync()
            } catch (e: Exception) {
                finish(mp)
            }
        }
    }

    private suspend fun speakUtterances(utterances: List<String>, profile: VoiceProfile, useBluetooth: Boolean = false) {
        suspendCancellableCoroutine<Unit> { cont ->
            var tts: TextToSpeech? = null
            val initCb = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.ERROR) { cont.resume(Unit); return@OnInitListener }

                // Route TTS through appropriate stream: STREAM_MUSIC for BT A2DP, STREAM_ALARM otherwise
                val ttsStream = if (useBluetooth) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setLegacyStreamType(ttsStream)
                        .build()
                )

                // Language
                val locale = if (profile.language == "system") Locale.getDefault()
                             else Locale.forLanguageTag(profile.language)
                tts?.setLanguage(locale)

                // Specific voice
                if (profile.voiceName != null) {
                    tts?.voices?.find { it.name == profile.voiceName }?.let { tts?.setVoice(it) }
                }

                tts?.setSpeechRate(profile.speechRate)
                tts?.setPitch(profile.pitch)

                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "last") { tts?.shutdown(); cont.resume(Unit) }
                    }
                    override fun onError(utteranceId: String?) { tts?.shutdown(); cont.resume(Unit) }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, profile.pan)
                }
                utterances.forEachIndexed { i, text ->
                    val id = if (i == utterances.lastIndex) "last" else "utt_$i"
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, params, id)
                }
            }
            tts = if (profile.enginePackage != null) {
                TextToSpeech(context, initCb, profile.enginePackage)
            } else {
                TextToSpeech(context, initCb)
            }
        }
    }

    private fun buildTimeUtterance(time: String): String {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return time
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return if (m == 0) "Es ist $h Uhr." else "Es ist $h Uhr $m."
    }

    private fun gongRawRes(id: String): Int? = when (id) {
        "bikebell1x" -> R.raw.bikebell1x
        "bikebell2x" -> R.raw.bikebell2x
        "doorbell"   -> R.raw.doorbell
        "gong1x"     -> R.raw.gong1x
        "gong2x"     -> R.raw.gong2x
        "gong3x"     -> R.raw.gong3x
        "gong4x"     -> R.raw.gong4x
        "kettle"     -> R.raw.kettle
        else         -> R.raw.gong1x  // fallback for unknown identifiers
    }
}
