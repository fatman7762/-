package jp.co.lightpath.reception.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import jp.co.lightpath.reception.R

/**
 * Plays Intercom01 Ding Dong Close on successful reception.
 */
class IntercomSound(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private var soundId: Int = 0
    private var loaded: Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == soundId) {
                loaded = true
            }
        }
        soundId = soundPool.load(appContext, R.raw.intercom, 1)
    }

    fun play() {
        if (!loaded || soundId == 0) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
