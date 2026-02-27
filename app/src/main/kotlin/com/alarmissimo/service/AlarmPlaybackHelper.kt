package com.alarmissimo.service

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import android.speech.tts.TextToSpeech
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
                speakUtterances(utterances)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun playGong(gongId: String, volume: Int) {
        val rawResId = gongRawRes(gongId) ?: return
        val vol = volume / 100f
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer.create(context, rawResId) ?: run { cont.resume(Unit); return@suspendCancellableCoroutine }
            mp.setVolume(vol, vol)
            mp.setOnCompletionListener { it.release(); cont.resume(Unit) }
            mp.setOnErrorListener { it, _, _ -> it.release(); cont.resume(Unit); true }
            mp.start()
        }
    }

    private suspend fun speakUtterances(utterances: List<String>) {
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
                utterances.forEachIndexed { i, text ->
                    val id = if (i == utterances.lastIndex) "last" else "utt_$i"
                    tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
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
