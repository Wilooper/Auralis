package app.auralis.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal class AudioFocus(context: Context, private val engine: VlcEngine) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var held = false
    private var resume = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    engine.setDucking(1f)
                    if (resume) { resume = false; engine.setPlaying(true) }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.setDucking(0.2f)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resume = engine.playWhenReady
                    engine.setPlaying(false)
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resume = false
                    held = false
                    engine.setPlaying(false)
                }
            }
        }, Handler(Looper.getMainLooper()))
        .build()

    fun play(): Boolean {
        resume = false
        if (!held) held = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (held) engine.setDucking(1f)
        return held
    }
    fun pause() { resume = false; engine.setPlaying(false); abandon() }
    fun onPlaybackStateChanged() {
        if (!engine.playWhenReady && !resume) abandon()
    }
    fun abandon() {
        resume = false
        if (held) manager.abandonAudioFocusRequest(request)
        held = false
    }
}
