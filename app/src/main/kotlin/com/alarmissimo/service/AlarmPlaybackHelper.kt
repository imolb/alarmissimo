package com.alarmissimo.service

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import java.util.concurrent.atomic.AtomicBoolean
import com.alarmissimo.R
import com.alarmissimo.data.model.AlarmEvent
import com.alarmissimo.data.model.AlarmSet
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Handles the full alarm playback sequence for a triggered alarm:
 * 1. Gong sound via [MediaPlayer]
 * 2. Time announcement via [TextToSpeech] (German, if [AlarmEvent.timePlayback] is true)
 * 3. Message via [TextToSpeech] (if [AlarmEvent.message] is non-empty)
 *
 * Acquires a [PowerManager.WakeLock] for the duration of playback.
 *
 * @param context Application context.
 */
class AlarmPlaybackHelper(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /**
     * Plays the full alarm sequence synchronously (designed to be called from a coroutine).
     *
     * @param alarmSet The alarm-set (provides volume).
     * @param event The specific alarm-event to play.
     */
    suspend fun play(alarmSet: AlarmSet, event: AlarmEvent) {
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "alarmissimo:alarm_wakelock"
        )
        wakeLock.acquire(60_000L) // 60 s safety timeout
        try {
            // 1. Gong
            if (event.gong != "none") {
                playGong(event.gong, alarmSet.audioVolume)
            }
            // 2. Time announcement + 3. Message
            if (event.timePlayback || event.message.isNotEmpty()) {
                val utterances = buildList {
                    if (event.timePlayback) add(buildTimeUtterance(event.time))
                    if (event.message.isNotEmpty()) add(event.message)
                }
                speakUtterances(utterances, alarmSet.audioVolume)  // item 24: respect configured volume
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun playGong(gongId: String, volume: Int) {
        when {
            // Item 3 — system ringtone stored as "system:<uri>"
            gongId.startsWith("system:") -> {
                val uri = Uri.parse(gongId.removePrefix("system:"))
                playGongUri(uri, volume)
            }
            else -> {
                val rawResId = gongRawRes(gongId) ?: return
                playGongRaw(rawResId, volume)
            }
        }
    }

    private suspend fun playGongRaw(rawResId: Int, volume: Int) {
        val vol = volume / 100f
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer.create(context, rawResId)
                ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }
            mp.setVolume(vol, vol)
            mp.setOnCompletionListener { it.release(); cont.resume(Unit) }
            mp.setOnErrorListener { it, _, _ -> it.release(); cont.resume(Unit); true }
            mp.start()
        }
    }

    private suspend fun playGongUri(uri: Uri, volume: Int) {
        val vol = volume / 100f
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer()
            // Item 18: guard against double-resume (onCompletion vs postDelayed)
            val done = AtomicBoolean(false)
            fun finish(player: MediaPlayer) {
                if (done.compareAndSet(false, true)) {
                    runCatching {
                        // Clear listeners before stop/release to prevent
                        // "mediaplayer went away with unhandled events" warning
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
                mp.setDataSource(context, uri)
                mp.isLooping = false
                mp.setVolume(vol, vol)
                mp.setOnPreparedListener { player ->
                    player.isLooping = false
                    // Item 18: some system alarm URIs embed OGG loop tags that the
                    // codec honours regardless of isLooping, so onCompletion never
                    // fires.  Schedule a hard-stop based on the actual track duration.
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

    // item 24: volume controls TTS output level via KEY_PARAM_VOLUME bundle
    private suspend fun speakUtterances(utterances: List<String>, volume: Int) {
        suspendCancellableCoroutine<Unit> { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.ERROR) { cont.resume(Unit); return@TextToSpeech }
                val locale = Locale("de", "DE")
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "last") { tts?.shutdown(); cont.resume(Unit) }
                    }
                    override fun onError(utteranceId: String?) { tts?.shutdown(); cont.resume(Unit) }
                })
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume / 100f)
                }
                utterances.forEachIndexed { i, text ->
                    val id = if (i == utterances.lastIndex) "last" else "utt_$i"
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, params, id)
                }
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
        "bikebell" -> R.raw.bikebell
        "doorbell" -> R.raw.doorbell
        "kettle"   -> R.raw.kettle
        "gong"     -> R.raw.gong
        else       -> null
    }
}
