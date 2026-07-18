package com.flip.galaxydeskclock

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject


data class WorldClockConfig(
    val enabled: Boolean,
    val zoneId: String,
    val label: String,
    val analog: Boolean
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var use24Hour: Boolean
        get() = prefs.getBoolean(KEY_24_HOUR, true)
        set(value) = put(KEY_24_HOUR, value)

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE, true)
        set(value) = put(KEY_SHOW_DATE, value)

    var showWeekday: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WEEKDAY, true)
        set(value) = put(KEY_SHOW_WEEKDAY, value)

    var showSeconds: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SECONDS, false)
        set(value) = put(KEY_SHOW_SECONDS, value)

    var mainClockScalePercent: Int
        get() = prefs.getInt(KEY_MAIN_SCALE, 100)
        set(value) = put(KEY_MAIN_SCALE, value.coerceIn(65, 140))

    var textColor: Int
        get() = prefs.getInt(KEY_TEXT_COLOR, Color.rgb(232, 229, 220))
        set(value) = put(KEY_TEXT_COLOR, value)

    var backgroundDimPercent: Int
        get() = prefs.getInt(KEY_BACKGROUND_DIM, 32)
        set(value) = put(KEY_BACKGROUND_DIM, value.coerceIn(0, 85))

    var microShiftEnabled: Boolean
        get() = prefs.getBoolean(KEY_MICRO_SHIFT, true)
        set(value) = put(KEY_MICRO_SHIFT, value)

    var microShiftMinutes: Int
        get() = prefs.getInt(KEY_MICRO_SHIFT_MINUTES, 3)
        set(value) = put(KEY_MICRO_SHIFT_MINUTES, value.coerceIn(1, 15))

    var restEnabled: Boolean
        get() = prefs.getBoolean(KEY_REST_ENABLED, true)
        set(value) = put(KEY_REST_ENABLED, value)

    var restStartMinute: Int
        get() = prefs.getInt(KEY_REST_START, 58)
        set(value) = put(KEY_REST_START, value.coerceIn(45, 59))

    var restMoveSeconds: Int
        get() = prefs.getInt(KEY_REST_MOVE_SECONDS, 15)
        set(value) = put(KEY_REST_MOVE_SECONDS, value.coerceIn(5, 60))

    var fadeSeconds: Int
        get() = prefs.getInt(KEY_FADE_SECONDS, 5)
        set(value) = put(KEY_FADE_SECONDS, value.coerceIn(1, 20))

    var dayBrightnessPercent: Int
        get() = prefs.getInt(KEY_DAY_BRIGHTNESS, 42)
        set(value) = put(KEY_DAY_BRIGHTNESS, value.coerceIn(5, 100))

    var nightBrightnessPercent: Int
        get() = prefs.getInt(KEY_NIGHT_BRIGHTNESS, 16)
        set(value) = put(KEY_NIGHT_BRIGHTNESS, value.coerceIn(1, 100))

    var nightStartHour: Int
        get() = prefs.getInt(KEY_NIGHT_START, 22)
        set(value) = put(KEY_NIGHT_START, value.coerceIn(0, 23))

    var nightEndHour: Int
        get() = prefs.getInt(KEY_NIGHT_END, 7)
        set(value) = put(KEY_NIGHT_END, value.coerceIn(0, 23))

    var chargingControlEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHARGING_CONTROL, true)
        set(value) = put(KEY_CHARGING_CONTROL, value)

    var wakeOnCharge: Boolean
        get() = prefs.getBoolean(KEY_WAKE_ON_CHARGE, true)
        set(value) = put(KEY_WAKE_ON_CHARGE, value)

    var immediateLockOnDisconnect: Boolean
        get() = prefs.getBoolean(KEY_IMMEDIATE_LOCK, false)
        set(value) = put(KEY_IMMEDIATE_LOCK, value)

    var startAfterBoot: Boolean
        get() = prefs.getBoolean(KEY_START_AFTER_BOOT, true)
        set(value) = put(KEY_START_AFTER_BOOT, value)

    var showWorldOffset: Boolean
        get() = prefs.getBoolean(KEY_WORLD_OFFSET, true)
        set(value) = put(KEY_WORLD_OFFSET, value)

    var showWorldDayDifference: Boolean
        get() = prefs.getBoolean(KEY_WORLD_DAY_DIFFERENCE, true)
        set(value) = put(KEY_WORLD_DAY_DIFFERENCE, value)

    var enabledLayoutMask: Int
        get() = prefs.getInt(KEY_LAYOUT_MASK, DEFAULT_LAYOUT_MASK)
        set(value) = put(KEY_LAYOUT_MASK, if (value == 0) DEFAULT_LAYOUT_MASK else value)

    fun worldClocks(): List<WorldClockConfig> {
        val defaults = defaultWorldClocks()
        return (0 until 4).map { index ->
            WorldClockConfig(
                enabled = prefs.getBoolean("world_${index}_enabled", defaults[index].enabled),
                zoneId = prefs.getString("world_${index}_zone", defaults[index].zoneId) ?: defaults[index].zoneId,
                label = prefs.getString("world_${index}_label", defaults[index].label) ?: defaults[index].label,
                analog = prefs.getBoolean("world_${index}_analog", defaults[index].analog)
            )
        }
    }

    fun setWorldClock(index: Int, config: WorldClockConfig) {
        require(index in 0..3)
        prefs.edit()
            .putBoolean("world_${index}_enabled", config.enabled)
            .putString("world_${index}_zone", config.zoneId)
            .putString("world_${index}_label", config.label)
            .putBoolean("world_${index}_analog", config.analog)
            .apply()
    }

    fun isNightHour(hour: Int): Boolean {
        return if (nightStartHour == nightEndHour) {
            false
        } else if (nightStartHour < nightEndHour) {
            hour in nightStartHour until nightEndHour
        } else {
            hour >= nightStartHour || hour < nightEndHour
        }
    }

    fun brightnessForHour(hour: Int): Float {
        val percent = if (isNightHour(hour)) nightBrightnessPercent else dayBrightnessPercent
        return (percent / 100f).coerceIn(0.01f, 1f)
    }

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("use24Hour", use24Hour)
        root.put("showDate", showDate)
        root.put("showWeekday", showWeekday)
        root.put("showSeconds", showSeconds)
        root.put("mainClockScalePercent", mainClockScalePercent)
        root.put("textColor", textColor)
        root.put("backgroundDimPercent", backgroundDimPercent)
        root.put("microShiftEnabled", microShiftEnabled)
        root.put("microShiftMinutes", microShiftMinutes)
        root.put("restEnabled", restEnabled)
        root.put("restStartMinute", restStartMinute)
        root.put("restMoveSeconds", restMoveSeconds)
        root.put("fadeSeconds", fadeSeconds)
        root.put("dayBrightnessPercent", dayBrightnessPercent)
        root.put("nightBrightnessPercent", nightBrightnessPercent)
        root.put("nightStartHour", nightStartHour)
        root.put("nightEndHour", nightEndHour)
        root.put("chargingControlEnabled", chargingControlEnabled)
        root.put("wakeOnCharge", wakeOnCharge)
        root.put("immediateLockOnDisconnect", immediateLockOnDisconnect)
        root.put("startAfterBoot", startAfterBoot)
        root.put("showWorldOffset", showWorldOffset)
        root.put("showWorldDayDifference", showWorldDayDifference)
        root.put("enabledLayoutMask", enabledLayoutMask)

        val clocks = JSONArray()
        worldClocks().forEach { clock ->
            clocks.put(JSONObject().apply {
                put("enabled", clock.enabled)
                put("zoneId", clock.zoneId)
                put("label", clock.label)
                put("analog", clock.analog)
            })
        }
        root.put("worldClocks", clocks)
        return root
    }

    fun importJson(root: JSONObject) {
        use24Hour = root.optBoolean("use24Hour", use24Hour)
        showDate = root.optBoolean("showDate", showDate)
        showWeekday = root.optBoolean("showWeekday", showWeekday)
        showSeconds = root.optBoolean("showSeconds", showSeconds)
        mainClockScalePercent = root.optInt("mainClockScalePercent", mainClockScalePercent)
        textColor = root.optInt("textColor", textColor)
        backgroundDimPercent = root.optInt("backgroundDimPercent", backgroundDimPercent)
        microShiftEnabled = root.optBoolean("microShiftEnabled", microShiftEnabled)
        microShiftMinutes = root.optInt("microShiftMinutes", microShiftMinutes)
        restEnabled = root.optBoolean("restEnabled", restEnabled)
        restStartMinute = root.optInt("restStartMinute", restStartMinute)
        restMoveSeconds = root.optInt("restMoveSeconds", restMoveSeconds)
        fadeSeconds = root.optInt("fadeSeconds", fadeSeconds)
        dayBrightnessPercent = root.optInt("dayBrightnessPercent", dayBrightnessPercent)
        nightBrightnessPercent = root.optInt("nightBrightnessPercent", nightBrightnessPercent)
        nightStartHour = root.optInt("nightStartHour", nightStartHour)
        nightEndHour = root.optInt("nightEndHour", nightEndHour)
        chargingControlEnabled = root.optBoolean("chargingControlEnabled", chargingControlEnabled)
        wakeOnCharge = root.optBoolean("wakeOnCharge", wakeOnCharge)
        immediateLockOnDisconnect = root.optBoolean("immediateLockOnDisconnect", immediateLockOnDisconnect)
        startAfterBoot = root.optBoolean("startAfterBoot", startAfterBoot)
        showWorldOffset = root.optBoolean("showWorldOffset", showWorldOffset)
        showWorldDayDifference = root.optBoolean("showWorldDayDifference", showWorldDayDifference)
        enabledLayoutMask = root.optInt("enabledLayoutMask", enabledLayoutMask)

        val clocks = root.optJSONArray("worldClocks")
        if (clocks != null) {
            for (index in 0 until minOf(4, clocks.length())) {
                val item = clocks.optJSONObject(index) ?: continue
                val current = worldClocks()[index]
                setWorldClock(
                    index,
                    WorldClockConfig(
                        enabled = item.optBoolean("enabled", current.enabled),
                        zoneId = item.optString("zoneId", current.zoneId),
                        label = item.optString("label", current.label),
                        analog = item.optBoolean("analog", current.analog)
                    )
                )
            }
        }
    }

    private fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun put(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    companion object {
        private const val PREFS_NAME = "desk_clock_settings"
        private const val KEY_24_HOUR = "use_24_hour"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_SHOW_WEEKDAY = "show_weekday"
        private const val KEY_SHOW_SECONDS = "show_seconds"
        private const val KEY_MAIN_SCALE = "main_scale"
        private const val KEY_TEXT_COLOR = "text_color"
        private const val KEY_BACKGROUND_DIM = "background_dim"
        private const val KEY_MICRO_SHIFT = "micro_shift"
        private const val KEY_MICRO_SHIFT_MINUTES = "micro_shift_minutes"
        private const val KEY_REST_ENABLED = "rest_enabled"
        private const val KEY_REST_START = "rest_start"
        private const val KEY_REST_MOVE_SECONDS = "rest_move_seconds"
        private const val KEY_FADE_SECONDS = "fade_seconds"
        private const val KEY_DAY_BRIGHTNESS = "day_brightness"
        private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
        private const val KEY_NIGHT_START = "night_start"
        private const val KEY_NIGHT_END = "night_end"
        private const val KEY_CHARGING_CONTROL = "charging_control"
        private const val KEY_WAKE_ON_CHARGE = "wake_on_charge"
        private const val KEY_IMMEDIATE_LOCK = "immediate_lock"
        private const val KEY_START_AFTER_BOOT = "start_after_boot"
        private const val KEY_WORLD_OFFSET = "world_offset"
        private const val KEY_WORLD_DAY_DIFFERENCE = "world_day_difference"
        private const val KEY_LAYOUT_MASK = "layout_mask"
        const val LAYOUT_COUNT = 6
        const val DEFAULT_LAYOUT_MASK = (1 shl LAYOUT_COUNT) - 1

        fun defaultWorldClocks(): List<WorldClockConfig> = listOf(
            WorldClockConfig(true, "Europe/Berlin", "Berlin", false),
            WorldClockConfig(true, "America/New_York", "New York", false),
            WorldClockConfig(false, "Asia/Tokyo", "Tokyo", false),
            WorldClockConfig(false, "Australia/Sydney", "Sydney", false)
        )
    }
}
