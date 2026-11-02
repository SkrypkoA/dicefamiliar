package com.skrypko.dicefamiliar.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.skrypko.dicefamiliar.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Short UI sound effects available to every feature. */
enum class Sound(@param:RawRes val resId: Int) {
    DICE_ROLL(R.raw.dice_roll),
}

interface SoundPlayer {
    fun play(sound: Sound)
}

@Singleton
class SoundPoolSoundPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : SoundPlayer {
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val soundIds: Map<Sound, Int>

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        soundIds = Sound.entries.associateWith { pool.load(context, it.resId, 1) }
    }

    override fun play(sound: Sound) {
        val id = soundIds[sound] ?: return
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    private companion object {
        const val MAX_STREAMS = 3
    }
}
