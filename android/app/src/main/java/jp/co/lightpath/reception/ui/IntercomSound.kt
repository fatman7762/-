package jp.co.lightpath.reception.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import jp.co.lightpath.reception.R

/**
 * Reception success chimes.
 * Default: Intercom01 Ding Dong Close.
 * Nakahara (クビ): Explosion01 Short.
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

    private var intercomId: Int = 0
    private var explosionId: Int = 0
    private val loaded = mutableSetOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded += sampleId
            }
        }
        intercomId = soundPool.load(appContext, R.raw.intercom, 1)
        explosionId = soundPool.load(appContext, R.raw.explosion, 1)
    }

    fun play(nakahara: Boolean = false) {
        val id = if (nakahara) explosionId else intercomId
        if (id == 0 || id !in loaded) return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
