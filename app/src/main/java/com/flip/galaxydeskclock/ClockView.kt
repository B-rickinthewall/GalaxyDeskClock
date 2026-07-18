package com.flip.galaxydeskclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onOpenSettings: (() -> Unit)? = null

    private val settings = AppSettings(context)
    private val backgrounds = BackgroundRepository(context)
    private var backgroundFiles: List<File> = backgrounds.list()
    private var powerConnected = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint()
    private val analogPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onOpenSettings?.invoke()
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    fun reload() {
        backgroundFiles = backgrounds.list()
        bitmapCache.evictAll()
        invalidate()
    }

    fun setPowerConnected(connected: Boolean) {
        powerConnected = connected
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmapCache.evictAll()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        if (settings.chargingControlEnabled && !powerConnected) {
            postInvalidateDelayed(1_000L)
            return
        }

        val nowMillis = System.currentTimeMillis()
        val localZone = ZoneId.systemDefault()
        val moment = TimeLogic.resolve(
            epochMillis = nowMillis,
            zoneId = localZone,
            restEnabled = settings.restEnabled,
            restStartMinute = settings.restStartMinute,
            fadeSeconds = settings.fadeSeconds
        )

        when (moment.phase) {
            DisplayPhase.NORMAL -> drawScene(canvas, nowMillis, moment.activeHourSeed, 1f)
            DisplayPhase.REST -> drawRestMode(canvas, nowMillis, 1f)
            DisplayPhase.FADE_IN -> {
                drawScene(canvas, nowMillis, moment.nextHourSeed, moment.nextLayoutAlpha)
                drawRestMode(canvas, nowMillis, 1f - moment.nextLayoutAlpha)
            }
        }

        postInvalidateDelayed(if (moment.phase == DisplayPhase.FADE_IN) 80L else 500L)
    }

    private fun drawScene(canvas: Canvas, nowMillis: Long, hourSeed: Long, alpha: Float) {
        if (alpha <= 0f) return
        val layer = canvas.saveLayerAlpha(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            (alpha.coerceIn(0f, 1f) * 255).toInt()
        )

        drawBackground(canvas, hourSeed)
        dimPaint.color = Color.argb(
            (settings.backgroundDimPercent / 100f * 255).toInt().coerceIn(0, 255),
            0,
            0,
            0
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val enabledLayouts = TimeLogic.enabledLayoutIndices(settings.enabledLayoutMask, AppSettings.LAYOUT_COUNT)
        val layoutIndex = TimeLogic.layoutIndex(hourSeed, enabledLayouts)
        val layout = layouts[layoutIndex % layouts.size]
        val shift = if (settings.microShiftEnabled) microShift(nowMillis) else Pair(0f, 0f)

        canvas.save()
        canvas.translate(shift.first, shift.second)
        drawMainClock(canvas, nowMillis, layout)
        drawWorldClocks(canvas, nowMillis, layout)
        canvas.restore()
        canvas.restoreToCount(layer)
    }

    private fun drawBackground(canvas: Canvas, hourSeed: Long) {
        if (backgroundFiles.isEmpty()) {
            val gradient = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(Color.rgb(10, 13, 18), Color.rgb(28, 31, 38), Color.BLACK),
                null,
                Shader.TileMode.CLAMP
            )
            backgroundPaint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            backgroundPaint.shader = null
            return
        }

        val index = TimeLogic.backgroundIndex(hourSeed, backgroundFiles.size)
        val file = backgroundFiles[index.coerceAtLeast(0)]
        val bitmap = bitmapCache[file.absolutePath] ?: backgrounds.decodeFile(file, width, height)?.also {
            bitmapCache.put(file.absolutePath, it)
        }

        if (bitmap == null || bitmap.isRecycled) {
            canvas.drawColor(Color.rgb(10, 12, 16))
            return
        }

        val scale = max(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val overflowX = max(0f, drawWidth - width)
        val overflowY = max(0f, drawHeight - height)

        val cropX = deterministicFraction(hourSeed xor 0x2A5A5A5A) * overflowX
        val cropY = deterministicFraction(hourSeed xor 0x6B6B6B6B) * overflowY
        val destination = RectF(
            -cropX,
            -cropY,
            drawWidth - cropX,
            drawHeight - cropY
        )
        canvas.drawBitmap(bitmap, null, destination, backgroundPaint)
    }

    private fun drawMainClock(canvas: Canvas, nowMillis: Long, layout: LayoutSpec) {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val mainSize = height * layout.mainSizeFraction * (settings.mainClockScalePercent / 100f)

        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textAlign = layout.mainAlign
        paint.textSize = mainSize
        paint.color = settings.textColor
        paint.alpha = 255
        paint.setShadowLayer(max(2f, mainSize * 0.025f), 0f, mainSize * 0.015f, Color.argb(180, 0, 0, 0))

        val timePattern = when {
            settings.use24Hour && settings.showSeconds -> "HH:mm:ss"
            settings.use24Hour -> "HH:mm"
            settings.showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        val time = DateTimeFormatter.ofPattern(timePattern).format(now)
        val x = width * layout.mainX
        val y = height * layout.mainY
        canvas.drawText(time, x, y, paint)

        val detailParts = mutableListOf<String>()
        if (settings.showWeekday) detailParts += DateTimeFormatter.ofPattern("EEEE").format(now)
        if (settings.showDate) detailParts += DateTimeFormatter.ofPattern("d MMMM yyyy").format(now)

        if (detailParts.isNotEmpty()) {
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textSize = max(18f, height * 0.045f)
            paint.alpha = 225
            canvas.drawText(detailParts.joinToString("  ·  "), x, y + mainSize * 0.42f, paint)
        }
        paint.clearShadowLayer()
    }

    private fun drawWorldClocks(canvas: Canvas, nowMillis: Long, layout: LayoutSpec) {
        val localNow = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val active = settings.worldClocks().filter { it.enabled }.take(4)
        if (active.isEmpty()) return

        active.forEachIndexed { index, config ->
            val position = layout.worldPositions[index.coerceAtMost(layout.worldPositions.lastIndex)]
            val zone = runCatching { ZoneId.of(config.zoneId) }.getOrElse { ZoneId.of("UTC") }
            val worldNow = localNow.withZoneSameInstant(zone)
            if (config.analog) {
                drawAnalogClock(canvas, position, worldNow, config.label)
            } else {
                drawDigitalWorldClock(canvas, position, localNow, worldNow, config.label)
            }
        }
    }

    private fun drawDigitalWorldClock(
        canvas: Canvas,
        position: NormalizedPoint,
        localNow: ZonedDateTime,
        worldNow: ZonedDateTime,
        label: String
    ) {
        val x = width * position.x
        val y = height * position.y
        val align = when {
            position.x < 0.35f -> Paint.Align.LEFT
            position.x > 0.65f -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }

        paint.textAlign = align
        paint.color = settings.textColor
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.setShadowLayer(3f, 0f, 2f, Color.argb(190, 0, 0, 0))
        paint.textSize = max(16f, height * 0.031f)
        paint.alpha = 220
        canvas.drawText(label.ifBlank { worldNow.zone.id }, x, y - height * 0.052f, paint)

        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textSize = max(26f, height * 0.073f)
        paint.alpha = 245
        val pattern = if (settings.use24Hour) "HH:mm" else "h:mm a"
        canvas.drawText(DateTimeFormatter.ofPattern(pattern).format(worldNow), x, y + height * 0.018f, paint)

        val info = mutableListOf<String>()
        if (settings.showWorldOffset) info += "UTC${worldNow.offset.id.replace("Z", "+00:00")}" 
        if (settings.showWorldDayDifference) {
            val dayDifference = ChronoUnit.DAYS.between(localNow.toLocalDate(), worldNow.toLocalDate()).toInt()
            when {
                dayDifference > 0 -> info += "+${dayDifference} day"
                dayDifference < 0 -> info += "${dayDifference} day"
            }
        }
        if (info.isNotEmpty()) {
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            paint.textSize = max(13f, height * 0.024f)
            paint.alpha = 180
            canvas.drawText(info.joinToString("  ·  "), x, y + height * 0.058f, paint)
        }
        paint.clearShadowLayer()
    }

    private fun drawAnalogClock(
        canvas: Canvas,
        position: NormalizedPoint,
        now: ZonedDateTime,
        label: String
    ) {
        val x = width * position.x
        val y = height * position.y
        val radius = min(width, height) * 0.085f
        val color = settings.textColor

        analogPaint.style = Paint.Style.STROKE
        analogPaint.strokeWidth = max(2f, radius * 0.035f)
        analogPaint.color = color
        analogPaint.alpha = 205
        canvas.drawCircle(x, y, radius, analogPaint)

        for (tick in 0 until 12) {
            val angle = Math.toRadians((tick * 30 - 90).toDouble())
            val inner = if (tick % 3 == 0) radius * 0.78f else radius * 0.86f
            val outer = radius * 0.94f
            canvas.drawLine(
                x + cos(angle).toFloat() * inner,
                y + sin(angle).toFloat() * inner,
                x + cos(angle).toFloat() * outer,
                y + sin(angle).toFloat() * outer,
                analogPaint
            )
        }

        val minuteAngle = Math.toRadians((now.minute * 6 - 90).toDouble())
        val hourAngle = Math.toRadians((((now.hour % 12) + now.minute / 60f) * 30f - 90f).toDouble())
        analogPaint.strokeCap = Paint.Cap.ROUND
        analogPaint.strokeWidth = max(3f, radius * 0.055f)
        analogPaint.alpha = 235
        canvas.drawLine(
            x,
            y,
            x + cos(hourAngle).toFloat() * radius * 0.50f,
            y + sin(hourAngle).toFloat() * radius * 0.50f,
            analogPaint
        )
        analogPaint.strokeWidth = max(2f, radius * 0.035f)
        canvas.drawLine(
            x,
            y,
            x + cos(minuteAngle).toFloat() * radius * 0.72f,
            y + sin(minuteAngle).toFloat() * radius * 0.72f,
            analogPaint
        )
        analogPaint.strokeCap = Paint.Cap.BUTT

        paint.textAlign = Paint.Align.CENTER
        paint.color = color
        paint.alpha = 220
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        paint.textSize = max(14f, height * 0.027f)
        paint.setShadowLayer(3f, 0f, 2f, Color.BLACK)
        canvas.drawText(label.ifBlank { now.zone.id }, x, y + radius + height * 0.035f, paint)
        paint.clearShadowLayer()
    }

    private fun drawRestMode(canvas: Canvas, nowMillis: Long, alpha: Float) {
        if (alpha <= 0f) return
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val index = TimeLogic.restPositionIndex(nowMillis, settings.restMoveSeconds, restPositions.size)
        val position = restPositions[index]

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        paint.textSize = max(20f, height * 0.048f)
        paint.color = Color.rgb(150, 150, 150)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 150).toInt()
        val pattern = if (settings.use24Hour) "HH:mm" else "h:mm"
        canvas.drawText(
            DateTimeFormatter.ofPattern(pattern).format(now),
            width * position.x,
            height * position.y,
            paint
        )
    }

    private fun microShift(nowMillis: Long): Pair<Float, Float> {
        val index = TimeLogic.microShiftIndex(nowMillis, settings.microShiftMinutes, microShifts.size)
        return microShifts[index]
    }

    private fun deterministicFraction(seed: Long): Float {
        var value = seed
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        val positive = value and Long.MAX_VALUE
        return (positive % 10_000L) / 10_000f
    }

    private data class LayoutSpec(
        val mainX: Float,
        val mainY: Float,
        val mainAlign: Paint.Align,
        val mainSizeFraction: Float,
        val worldPositions: List<NormalizedPoint>
    )

    private data class NormalizedPoint(val x: Float, val y: Float)

    companion object {
        private val layouts = listOf(
            LayoutSpec(
                mainX = 0.08f,
                mainY = 0.48f,
                mainAlign = Paint.Align.LEFT,
                mainSizeFraction = 0.25f,
                worldPositions = listOf(
                    NormalizedPoint(0.72f, 0.26f), NormalizedPoint(0.92f, 0.26f),
                    NormalizedPoint(0.72f, 0.72f), NormalizedPoint(0.92f, 0.72f)
                )
            ),
            LayoutSpec(
                mainX = 0.92f,
                mainY = 0.48f,
                mainAlign = Paint.Align.RIGHT,
                mainSizeFraction = 0.25f,
                worldPositions = listOf(
                    NormalizedPoint(0.08f, 0.26f), NormalizedPoint(0.28f, 0.26f),
                    NormalizedPoint(0.08f, 0.72f), NormalizedPoint(0.28f, 0.72f)
                )
            ),
            LayoutSpec(
                mainX = 0.50f,
                mainY = 0.42f,
                mainAlign = Paint.Align.CENTER,
                mainSizeFraction = 0.27f,
                worldPositions = listOf(
                    NormalizedPoint(0.14f, 0.80f), NormalizedPoint(0.38f, 0.80f),
                    NormalizedPoint(0.62f, 0.80f), NormalizedPoint(0.86f, 0.80f)
                )
            ),
            LayoutSpec(
                mainX = 0.50f,
                mainY = 0.78f,
                mainAlign = Paint.Align.CENTER,
                mainSizeFraction = 0.23f,
                worldPositions = listOf(
                    NormalizedPoint(0.14f, 0.24f), NormalizedPoint(0.38f, 0.24f),
                    NormalizedPoint(0.62f, 0.24f), NormalizedPoint(0.86f, 0.24f)
                )
            ),
            LayoutSpec(
                mainX = 0.08f,
                mainY = 0.78f,
                mainAlign = Paint.Align.LEFT,
                mainSizeFraction = 0.22f,
                worldPositions = listOf(
                    NormalizedPoint(0.12f, 0.22f), NormalizedPoint(0.38f, 0.22f),
                    NormalizedPoint(0.66f, 0.22f), NormalizedPoint(0.90f, 0.22f)
                )
            ),
            LayoutSpec(
                mainX = 0.92f,
                mainY = 0.28f,
                mainAlign = Paint.Align.RIGHT,
                mainSizeFraction = 0.22f,
                worldPositions = listOf(
                    NormalizedPoint(0.10f, 0.72f), NormalizedPoint(0.34f, 0.72f),
                    NormalizedPoint(0.62f, 0.72f), NormalizedPoint(0.88f, 0.72f)
                )
            )
        )

        private val microShifts = listOf(
            Pair(0f, 0f), Pair(8f, 0f), Pair(-8f, 0f), Pair(0f, 8f), Pair(0f, -8f),
            Pair(12f, 7f), Pair(-12f, 7f), Pair(12f, -7f), Pair(-12f, -7f)
        )

        private val restPositions = listOf(
            NormalizedPoint(0.10f, 0.14f), NormalizedPoint(0.32f, 0.12f),
            NormalizedPoint(0.55f, 0.14f), NormalizedPoint(0.80f, 0.12f),
            NormalizedPoint(0.16f, 0.34f), NormalizedPoint(0.42f, 0.31f),
            NormalizedPoint(0.68f, 0.34f), NormalizedPoint(0.90f, 0.32f),
            NormalizedPoint(0.09f, 0.58f), NormalizedPoint(0.34f, 0.60f),
            NormalizedPoint(0.61f, 0.57f), NormalizedPoint(0.86f, 0.59f),
            NormalizedPoint(0.13f, 0.84f), NormalizedPoint(0.40f, 0.86f),
            NormalizedPoint(0.66f, 0.83f), NormalizedPoint(0.89f, 0.85f)
        )
    }
}
