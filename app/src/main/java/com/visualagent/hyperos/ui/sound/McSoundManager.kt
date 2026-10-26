package com.visualagent.hyperos.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.visualagent.hyperos.R

/**
 * 原汁原味 Minecraft 音效池管理器：
 * 直接从 res/raw 加载真实的木石按键声 (mc_click)、经验球拾取叮当声 (mc_orb_xp) 与低音方块反馈声 (mc_anvil_land)
 */
object McSoundManager {

    private var soundPool: SoundPool? = null
    private var clickSoundId: Int = 0
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0

    fun init(context: Context) {
        if (soundPool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()

        clickSoundId = pool.load(context, R.raw.mc_click, 1)
        successSoundId = pool.load(context, R.raw.mc_orb_xp, 1)
        errorSoundId = pool.load(context, R.raw.mc_anvil_land, 1)
        soundPool = pool
    }

    fun playClick() {
        soundPool?.play(clickSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
    }

    fun playSuccess() {
        soundPool?.play(successSoundId, 0.9f, 0.9f, 1, 0, 1.0f)
    }

    fun playError() {
        soundPool?.play(errorSoundId, 0.9f, 0.9f, 1, 0, 1.0f)
    }
}
