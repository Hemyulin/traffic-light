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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : Activity() {
    private lateinit var moodStore: MoodEntryStore
    private lateinit var reminderStore: ReminderSettingsStore

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
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(colorZone(MoodColor.GREEN, GREEN))
            addView(colorZone(MoodColor.YELLOW, YELLOW))
            addView(colorZone(MoodColor.RED, RED))
        }
    }

    private fun colorZone(color: MoodColor, backgroundColor: Int): View {
        return View(this).apply {
            setBackgroundColor(backgroundColor)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                saveMoodAndExit(color)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
    }

    private fun buildDashboardView(): View {
        return ScrollView(this).apply {
            setBackgroundColor(BLACK)
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(14), dp(14), dp(14), dp(22))
                addView(title("Now"))
                addView(buildCompactCheckIn())
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
                addView(settingButton("Every hour, 9-21") {
                    saveSettings(
                        ReminderSettings(
                            mode = ReminderMode.INTERVAL,
                            intervalMinutes = 60,
                            startHour = 9,
                            endHour = 21,
                            fixedHours = listOf(9, 13, 17, 21),
                        ),
                    )
                })
                addView(settingButton("Every 90 min, 9-21") {
                    saveSettings(
                        ReminderSettings(
                            mode = ReminderMode.INTERVAL,
                            intervalMinutes = 90,
                            startHour = 9,
                            endHour = 21,
                            fixedHours = listOf(9, 13, 17, 21),
                        ),
                    )
                })
                addView(settingButton("Fixed: 9 13 17 21") {
                    saveSettings(
                        ReminderSettings(
                            mode = ReminderMode.FIXED_TIMES,
                            intervalMinutes = 60,
                            startHour = 9,
                            endHour = 21,
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

    private fun buildCompactCheckIn(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(compactColorButton(MoodColor.GREEN, GREEN))
            addView(compactColorButton(MoodColor.YELLOW, YELLOW))
            addView(compactColorButton(MoodColor.RED, RED))
        }
    }

    private fun compactColorButton(color: MoodColor, backgroundColor: Int): View {
        return View(this).apply {
            setBackgroundColor(backgroundColor)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                moodStore.save(color)
                setContentView(buildDashboardView())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34),
            ).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
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
            ReminderMode.INTERVAL -> "Every ${settings.intervalMinutes} min, ${settings.startHour}-${settings.endHour}"
            ReminderMode.FIXED_TIMES -> "At ${settings.fixedHours.joinToString(" ")}"
            ReminderMode.OFF -> "Off"
        }
        return smallText(text)
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
                setContentView(buildDashboardView())
            }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38),
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
    }

    private fun saveSettings(settings: ReminderSettings) {
        reminderStore.save(settings)
        CheckInAlarmScheduler.schedule(this)
    }

    private fun todayEntries(): List<MoodEntry> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        return moodStore.all().filter { entry ->
            Instant.ofEpochMilli(entry.timestampMillis).atZone(zone).toLocalDate() == today
        }
    }

    private fun saveMoodAndExit(color: MoodColor) {
        moodStore.save(color)
        finishAndRemoveTask()
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
