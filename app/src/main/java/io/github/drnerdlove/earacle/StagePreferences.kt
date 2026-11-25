package io.github.drnerdlove.earacle

import android.content.Context
import io.github.drnerdlove.earacle.lyrics.StageSettings
import io.github.drnerdlove.earacle.lyrics.StageStore
import io.github.drnerdlove.earacle.lyrics.clampTextSize

/** Keeps the stage mode settings in SharedPreferences, so they survive closing the app. */
class StagePreferences(context: Context) : StageStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): StageSettings {
        val defaults = StageSettings()
        return StageSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            textSizeSp = clampTextSize(prefs.getInt(KEY_TEXT_SIZE, defaults.textSizeSp)),
        )
    }

    override fun save(settings: StageSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putInt(KEY_TEXT_SIZE, settings.textSizeSp)
            .apply()
    }

    private companion object {
        const val FILE = "stage_mode"
        const val KEY_ENABLED = "enabled"
        const val KEY_TEXT_SIZE = "text_size_sp"
    }
}
