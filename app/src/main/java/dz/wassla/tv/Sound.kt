package dz.wassla.tv

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/** Light "tick" played when focus moves between items. Generated in code, no audio file needed. */
object NavSound {
    @Volatile var enabled = true
    private var track: AudioTrack? = null
    private var last = 0L

    private fun build(): AudioTrack {
        val rate = 22050
        val n = (rate * 0.045).toInt()
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (Math.sin(2 * Math.PI * 1400 * t) * Math.exp(-t * 90.0) * 9000).toInt().toShort()
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack(attrs, fmt, n * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
        t.write(pcm, 0, n)
        t.setVolume(0.35f)
        return t
    }

    fun tick() {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - last < 45) return
        last = now
        try {
            val t = track ?: build().also { track = it }
            t.stop()
            t.setPlaybackHeadPosition(0)
            t.play()
        } catch (_: Exception) {
        }
    }
}
