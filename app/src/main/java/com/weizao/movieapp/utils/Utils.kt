package com.weizao.movieapp.utils

import android.content.Context
import android.media.AudioManager
import android.view.Window
import kotlin.math.max
import kotlin.math.min

object BrightnessUtil {
    /** 设置窗口亮度，范围 0.01 ~ 1.0 */
    fun set(window: Window, value: Float) {
        val lp = window.attributes
        lp.screenBrightness = max(0.01f, min(1f, value))
        window.attributes = lp
    }
    fun get(window: Window): Float {
        val v = window.attributes.screenBrightness
        return if (v < 0) 0.5f else v
    }

    /** 恢复跟随系统亮度 */
    fun reset(window: Window) {
        val lp = window.attributes
        lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }
}

object VolumeUtil {
    fun set(context: Context, percent: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (percent.coerceIn(0, 100) * max / 100f).toInt(), 0)
    }
    fun get(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 50
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 50
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }
}

object TimeUtil {
    fun fmt(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}

object ValidUtil {
    fun isUrl(s: String): Boolean =
        s.startsWith("http://") || s.startsWith("https://")
}
