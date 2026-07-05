package com.example.trafficlight

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var moodStore: MoodEntryStore
    private lateinit var reminderStore: ReminderSettingsStore
    private var dashboardScrollView: ScrollView? = null
    private var dashboardScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moodStore = MoodEntryStore(this)
        reminderStore = ReminderSettingsStore(this)
        CheckInAlarmScheduler.schedule(this)
        requestNotificationPermissionIfNeeded()
        getSystemService(NotificationManager::class.java).cancel(100)

        val isPrompt = intent?.getBooleanExtra(EXTRA_PROMPT_MODE, false) == true
        setContentView(if (isPrompt) buildPromptView() else buildDashboardView())
    }

    private fun buildPromptView(): View {
        return FrameLayout(this).apply {
            setBackgroundColor(BLACK)
            addView(MoodWheelView(context) { color ->
                saveMoodThenExit(color, this)
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun buildDashboardView(): View {
        return ScrollView(this).apply {
            dashboardScrollView = this
            setBackgroundColor(BLACK)
            isFillViewport = true
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    scrollTo(0, dashboardScrollY)
                    return true
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(buildFullScreenCheckIn())
                addView(title("Today"))
                addView(TodayGraphView(context, moodStore.all()), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(92),
                ).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(10)
                })
                addView(todaySummary())
                addView(title("Reminders"))
                addView(reminderSummary())
                addView(rangeControl(isStart = true))
                addView(rangeControl(isStart = false))
                addView(settingButton("Every 60 min") {
                    val current = reminderStore.load()
                    saveSettings(
                        current.copy(
                            mode = ReminderMode.INTERVAL,
                            intervalMinutes = 60,
                        ),
                    )
                })
                addView(settingButton("Every 90 min") {
                    val current = reminderStore.load()
                    saveSettings(
                        current.copy(
                            mode = ReminderMode.INTERVAL,
                            intervalMinutes = 90,
                        ),
                    )
                })
                addView(settingButton("Fixed: 9 13 17 21") {
                    saveSettings(
                        ReminderSettings(
                            mode = ReminderMode.FIXED_TIMES,
                            intervalMinutes = 60,
                            startMinuteOfDay = 9 * 60,
                            endMinuteOfDay = 21 * 60,
                            fixedHours = listOf(9, 13, 17, 21),
                        ),
                    )
                })
                addView(settingButton("Off") {
                    val current = reminderStore.load()
                    saveSettings(current.copy(mode = ReminderMode.OFF))
                })
            })
        }
    }

    private fun buildFullScreenCheckIn(): View {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.heightPixels,
            )
            setBackgroundColor(BLACK)
            addView(MoodWheelView(context) { color ->
                saveMoodWithFeedback(color, this)
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(8)
            }
        }
    }

    private fun todaySummary(): TextView {
        val entries = todayEntries()
        val green = entries.count { it.color == MoodColor.GREEN }
        val yellow = entries.count { it.color == MoodColor.YELLOW }
        val red = entries.count { it.color == MoodColor.RED }
        return smallText("G $green   Y $yellow   R $red")
    }

    private fun reminderSummary(): TextView {
        val settings = reminderStore.load()
        val text = when (settings.mode) {
            ReminderMode.INTERVAL -> "Every ${settings.intervalMinutes} min, ${settings.startMinuteOfDay.formatTime()}-${settings.endMinuteOfDay.formatTime()}"
            ReminderMode.FIXED_TIMES -> "At ${settings.fixedHours.joinToString(" ")}"
            ReminderMode.OFF -> "Off"
        }
        return smallText(text)
    }

    private fun rangeControl(isStart: Boolean): View {
        val settings = reminderStore.load()
        val currentValue = if (isStart) settings.startMinuteOfDay else settings.endMinuteOfDay
        val label = if (isStart) "From" else "Until"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(14), 0)
            addView(adjustButton("-30") {
                updateRange(isStart, currentValue - 30)
            })
            addView(TextView(context).apply {
                text = "$label ${currentValue.formatTime()}"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                includeFontPadding = false
            }, LinearLayout.LayoutParams(0, dp(34), 1f))
            addView(adjustButton("+30") {
                updateRange(isStart, currentValue + 30)
            })
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
    }

    private fun adjustButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(DARK_BUTTON)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
                refreshDashboardPreservingScroll()
            }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(dp(48), dp(34)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }
    }

    private fun smallText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(SOFT_TEXT)
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun settingButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(DARK_BUTTON)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onClick()
                refreshDashboardPreservingScroll()
            }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38),
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
    }

    private fun updateRange(isStart: Boolean, newValue: Int) {
        val current = reminderStore.load()
        val normalized = newValue.coerceIn(0, 23 * 60 + 30)
        val next = if (isStart) {
            current.copy(startMinuteOfDay = normalized.coerceAtMost(current.endMinuteOfDay - 30))
        } else {
            current.copy(endMinuteOfDay = normalized.coerceAtLeast(current.startMinuteOfDay + 30))
        }
        saveSettings(next.copy(mode = ReminderMode.INTERVAL))
    }

    private fun saveSettings(settings: ReminderSettings) {
        reminderStore.save(settings)
        CheckInAlarmScheduler.schedule(this)
    }

    private fun refreshDashboardPreservingScroll() {
        dashboardScrollY = dashboardScrollView?.scrollY ?: dashboardScrollY
        setContentView(buildDashboardView())
    }

    private fun saveMoodWithFeedback(color: MoodColor, overlayHost: FrameLayout) {
        val entry = moodStore.save(color)
        MoodSyncPublisher.publish(this, entry)
        vibrateLightly()
        showConfirmation(overlayHost, color) {
            refreshDashboardPreservingScroll()
        }
    }

    private fun todayEntries(): List<MoodEntry> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return moodStore.all().filter { entry ->
            Instant.ofEpochMilli(entry.timestampMillis).atZone(zone).toLocalDate() == today
        }
    }

    private fun saveMoodThenExit(color: MoodColor, overlayHost: FrameLayout) {
        val entry = moodStore.save(color)
        MoodSyncPublisher.publish(this, entry)
        vibrateLightly()
        showConfirmation(overlayHost, color) {
            finishAndRemoveTask()
        }
    }

    private fun vibrateLightly() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }

    private fun showConfirmation(host: FrameLayout, color: MoodColor, afterShown: () -> Unit) {
        val confirmation = ConfirmationView(this, color).apply {
            alpha = 0f
        }
        host.addView(confirmation, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        confirmation.animate()
            .alpha(1f)
            .setDuration(90)
            .withEndAction {
                confirmation.postDelayed(afterShown, 260)
            }
            .start()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun Int.formatTime(): String {
        val hour = this / 60
        val minute = this % 60
        return if (minute == 0) {
            hour.toString()
        } else {
            "%d:%02d".format(hour, minute)
        }
    }

    companion object {
        const val EXTRA_PROMPT_MODE = "com.example.trafficlight.PROMPT_MODE"
        private const val BLACK = Color.BLACK
        private val GREEN = Color.rgb(34, 197, 94)
        private val YELLOW = Color.rgb(250, 204, 21)
        private val RED = Color.rgb(239, 68, 68)
        private val SOFT_TEXT = Color.rgb(176, 185, 185)
        private val DARK_BUTTON = Color.rgb(32, 35, 35)
    }
}

class ConfirmationView(
    context: android.content.Context,
    private val color: MoodColor,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.22f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(224, 0, 0, 0)
        canvas.drawRect(0f, 0f, width, height, paint)

        paint.color = color.toConfirmationColor()
        canvas.drawCircle(centerX, centerY - radius * 0.35f, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = radius * 0.16f
        paint.color = Color.WHITE
        canvas.drawLine(centerX - radius * 0.42f, centerY - radius * 0.36f, centerX - radius * 0.12f, centerY - radius * 0.06f, paint)
        canvas.drawLine(centerX - radius * 0.12f, centerY - radius * 0.06f, centerX + radius * 0.48f, centerY - radius * 0.62f, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = radius * 0.48f
        paint.color = Color.rgb(232, 239, 235)
        canvas.drawText("Logged", centerX, centerY + radius * 1.12f, paint)
    }

    private fun MoodColor.toConfirmationColor(): Int {
        return when (this) {
            MoodColor.GREEN -> Color.rgb(34, 197, 94)
            MoodColor.YELLOW -> Color.rgb(250, 204, 21)
            MoodColor.RED -> Color.rgb(239, 68, 68)
        }
    }
}

class MoodWheelView(
    context: android.content.Context,
    private val onMoodSelected: (MoodColor) -> Unit,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height).toFloat()
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        bounds.set(left, top, left + diameter, top + diameter)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(34, 197, 94)
        canvas.drawArc(bounds, -90f, 120f, true, paint)
        paint.color = Color.rgb(250, 204, 21)
        canvas.drawArc(bounds, 30f, 120f, true, paint)
        paint.color = Color.rgb(239, 68, 68)
        canvas.drawArc(bounds, 150f, 120f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.BLACK
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), diameter / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f
        val dx = event.x - centerX
        val dy = event.y - centerY
        if (dx * dx + dy * dy > radius * radius) return true

        val angle = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0)
        val color = when {
            angle >= 270.0 || angle < 30.0 -> MoodColor.GREEN
            angle < 150.0 -> MoodColor.YELLOW
            else -> MoodColor.RED
        }
        onMoodSelected(color)
        return true
    }
}

class TodayGraphView(
    context: android.content.Context,
    entries: List<MoodEntry>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayEntries = entries.filterToday()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / 24f

        paint.color = Color.rgb(28, 30, 30)
        canvas.drawRoundRect(RectF(0f, 0f, width, height), 12f, 12f, paint)

        val grouped = todayEntries.groupBy { entry ->
            Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).hour
        }

        for (hour in 0..23) {
            val hourEntries = grouped[hour].orEmpty()
            val color = hourEntries.lastOrNull()?.color?.toGraphColor() ?: Color.rgb(54, 58, 58)
            val left = hour * barWidth + 1f
            val right = (hour + 1) * barWidth - 1f
            val filledHeight = if (hourEntries.isEmpty()) height * 0.16f else height * 0.82f
            paint.color = color
            canvas.drawRoundRect(
                RectF(left, height - filledHeight, right, height),
                4f,
                4f,
                paint,
            )
        }
    }

    private fun List<MoodEntry>.filterToday(): List<MoodEntry> {
        val today = LocalDate.now()
        return filter { entry ->
            Instant.ofEpochMilli(entry.timestampMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate() == today
        }
    }

    private fun MoodColor.toGraphColor(): Int {
        return when (this) {
            MoodColor.GREEN -> Color.rgb(34, 197, 94)
            MoodColor.YELLOW -> Color.rgb(250, 204, 21)
            MoodColor.RED -> Color.rgb(239, 68, 68)
        }
    }
}
