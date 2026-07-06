package com.example.trafficlight.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MobileMainActivity : Activity() {
    private lateinit var store: MobileMoodEntryStore
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withGermanLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Locale.setDefault(Locale.GERMAN)
        super.onCreate(savedInstanceState)
        store = MobileMoodEntryStore(this)
        setContentView(buildView())
        syncExistingEntries()
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildView())
        syncExistingEntries()
    }

    private fun buildView(): View {
        val entries = store.all()
        return ScrollView(this).apply {
            setBackgroundColor(BLACK)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(22), dp(20), dp(28))
                addView(header(getString(R.string.app_name)))
                addView(subtitle(resources.getQuantityString(R.plurals.synced_check_ins, entries.size, entries.size)))
                addView(MoodDistributionView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(220),
                ).apply {
                    topMargin = dp(24)
                    bottomMargin = dp(14)
                })
                addView(distributionStats(entries))
                addView(sectionTitle(getString(R.string.today)))
                addView(MoodDayGraphView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(168),
                ))
                addView(summaryText(todaySummary(entries)))
                addView(sectionTitle(getString(R.string.last_7_days)))
                addView(WeekSummaryView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(180),
                ))
                addView(sectionTitle(getString(R.string.recent)))
                recentEntries(entries).forEach { entry ->
                    addView(entryRow(entry))
                }
            })
        }
    }

    private fun syncExistingEntries() {
        requestWatchBackfill()
        mainHandler.postDelayed(::pullDataLayerEntries, 900)
        pullDataLayerEntries()
    }

    private fun requestWatchBackfill() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, PATH_SYNC_REQUEST, ByteArray(0))
                }
            }
    }

    private fun pullDataLayerEntries() {
        Wearable.getDataClient(this).dataItems
            .addOnSuccessListener { items ->
                val localStore = MobileMoodEntryStore(this)
                for (item in items) {
                    if (!item.uri.path.orEmpty().startsWith(PATH_PREFIX)) continue
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val timestamp = dataMap.getLong(KEY_TIMESTAMP)
                    val colorValue = dataMap.getString(KEY_COLOR).orEmpty()
                    val color = MoodColor.entries.firstOrNull { it.storedValue == colorValue } ?: continue
                    localStore.save(MoodEntry(timestamp, color))
                }
                setContentView(buildView())
                items.release()
            }
    }

    private fun todaySummary(entries: List<MoodEntry>): String {
        val today = entries.filterToday()
        val green = today.count { it.color == MoodColor.GREEN }
        val yellow = today.count { it.color == MoodColor.YELLOW }
        val red = today.count { it.color == MoodColor.RED }
        return getString(R.string.today_summary, green, yellow, red)
    }

    private fun distributionStats(entries: List<MoodEntry>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(statPill(getString(R.string.green), entries.count { it.color == MoodColor.GREEN }, MoodColor.GREEN))
            addView(statPill(getString(R.string.yellow), entries.count { it.color == MoodColor.YELLOW }, MoodColor.YELLOW))
            addView(statPill(getString(R.string.red), entries.count { it.color == MoodColor.RED }, MoodColor.RED))
        }
    }

    private fun statPill(label: String, count: Int, color: MoodColor): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.rgb(22, 25, 25))
            addView(View(context).apply {
                setBackgroundColor(color.toUiColor())
            }, LinearLayout.LayoutParams(dp(18), dp(5)).apply {
                bottomMargin = dp(8)
            })
            addView(TextView(context).apply {
                text = count.toString()
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(SOFT_TEXT)
                textSize = 12f
                gravity = Gravity.CENTER
                includeFontPadding = false
            })
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
        }
    }

    private fun recentEntries(entries: List<MoodEntry>): List<MoodEntry> {
        return entries.sortedByDescending { it.timestampMillis }.take(12)
    }

    private fun header(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.START
            includeFontPadding = false
        }
    }

    private fun subtitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(SOFT_TEXT)
            textSize = 14f
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 18f
            includeFontPadding = false
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(26)
                bottomMargin = dp(10)
            }
        }
    }

    private fun summaryText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(SOFT_TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun entryRow(entry: MoodEntry): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
            addView(View(context).apply {
                setBackgroundColor(entry.color.toUiColor())
            }, LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                marginEnd = dp(12)
            })
            addView(TextView(context).apply {
                text = entry.label()
                setTextColor(Color.WHITE)
                textSize = 15f
            })
        }
    }

    private fun MoodEntry.label(): String {
        val dateTime = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
        val minute = dateTime.minute.toString().padStart(2, '0')
        return getString(R.string.entry_label, color.localizedName(), dateTime.hour, minute)
    }

    private fun MoodColor.localizedName(): String {
        return when (this) {
            MoodColor.GREEN -> getString(R.string.green)
            MoodColor.YELLOW -> getString(R.string.yellow)
            MoodColor.RED -> getString(R.string.red)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val PATH_PREFIX = "/mood_entries"
        private const val PATH_SYNC_REQUEST = "/sync_request"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_COLOR = "color"
        private const val BLACK = Color.BLACK
        private val SOFT_TEXT = Color.rgb(176, 185, 185)
    }
}

private fun Context.withGermanLocale(): Context {
    Locale.setDefault(Locale.GERMAN)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.GERMAN)
    return createConfigurationContext(configuration)
}

class MoodDistributionView(
    context: android.content.Context,
    entries: List<MoodEntry>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val counts = MoodColor.entries.associateWith { color ->
        entries.count { it.color == color }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val diameter = minOf(width, height) * 0.82f
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        bounds.set(left, top, left + diameter, top + diameter)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = diameter * 0.18f
        paint.color = Color.rgb(39, 44, 44)
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), diameter / 2f - paint.strokeWidth / 2f, paint)

        val total = counts.values.sum()
        if (total > 0) {
            var startAngle = -90f
            for (color in listOf(MoodColor.GREEN, MoodColor.YELLOW, MoodColor.RED)) {
                val sweep = 360f * counts[color].orZero().toFloat() / total.toFloat()
                if (sweep <= 0f) continue
                paint.color = color.toUiColor()
                canvas.drawArc(bounds, startAngle, sweep, false, paint)
                startAngle += sweep
            }
        }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = diameter * 0.18f
        canvas.drawText(total.toString(), bounds.centerX(), bounds.centerY() - diameter * 0.02f, paint)

        paint.color = Color.rgb(176, 185, 185)
        paint.textSize = diameter * 0.07f
        canvas.drawText(context.getString(R.string.check_ins), bounds.centerX(), bounds.centerY() + diameter * 0.12f, paint)
    }

    private fun Int?.orZero(): Int = this ?: 0
}

class MoodDayGraphView(
    context: android.content.Context,
    entries: List<MoodEntry>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayEntries = entries.filterToday()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val labelHeight = 26f
        val graphBottom = height - labelHeight
        val barWidth = width / 24f

        paint.color = Color.rgb(24, 27, 27)
        canvas.drawRoundRect(RectF(0f, 0f, width, graphBottom), 14f, 14f, paint)

        val grouped = todayEntries.groupBy { entry ->
            Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).hour
        }

        for (hour in 0..23) {
            val hourEntries = grouped[hour].orEmpty()
            paint.color = hourEntries.lastOrNull()?.color?.toUiColor() ?: Color.rgb(54, 58, 58)
            val filledHeight = if (hourEntries.isEmpty()) graphBottom * 0.12f else graphBottom * 0.78f
            canvas.drawRoundRect(
                RectF(hour * barWidth + 2f, graphBottom - filledHeight, (hour + 1) * barWidth - 2f, graphBottom - 10f),
                5f,
                5f,
                paint,
            )
        }

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        paint.color = Color.rgb(176, 185, 185)
        listOf(3, 6, 9, 12, 15, 18, 21, 0).forEach { hour ->
            val xHour = if (hour == 0) 24 else hour
            val x = (xHour.toFloat() / 24f) * width
            canvas.drawText(hour.toString().padStart(2, '0'), x.coerceIn(12f, width - 12f), height - 4f, paint)
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
}

class WeekSummaryView(
    context: android.content.Context,
    entries: List<MoodEntry>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val entriesByDay = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    private val dayFormatter = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
    private val dateFormatter = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val labelHeight = 42f
        val graphBottom = height - labelHeight
        val gap = 10f
        val columnWidth = (width - gap * 6f) / 7f
        val today = LocalDate.now()

        for (index in 0..6) {
            val day = today.minusDays((6 - index).toLong())
            val entries = entriesByDay[day].orEmpty()
            val counts = MoodColor.entries.associateWith { color -> entries.count { it.color == color } }
            val total = counts.values.sum().coerceAtLeast(1)
            var bottom = graphBottom
            val left = index * (columnWidth + gap)

            for (color in listOf(MoodColor.RED, MoodColor.YELLOW, MoodColor.GREEN)) {
                val segmentHeight = graphBottom * (counts[color].orZero().toFloat() / total.toFloat())
                paint.color = if (entries.isEmpty()) Color.rgb(54, 58, 58) else color.toUiColor()
                canvas.drawRoundRect(
                    RectF(left, bottom - segmentHeight, left + columnWidth, bottom),
                    8f,
                    8f,
                    paint,
                )
                bottom -= segmentHeight
                if (entries.isEmpty()) break
            }

            val centerX = left + columnWidth / 2f
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.rgb(232, 239, 235)
            paint.textSize = 16f
            canvas.drawText(day.format(dayFormatter), centerX, graphBottom + 17f, paint)
            paint.color = Color.rgb(176, 185, 185)
            paint.textSize = 14f
            canvas.drawText(day.format(dateFormatter), centerX, graphBottom + 36f, paint)
        }
    }

    private fun Int?.orZero(): Int = this ?: 0
}

fun MoodColor.toUiColor(): Int {
    return when (this) {
        MoodColor.GREEN -> Color.rgb(34, 197, 94)
        MoodColor.YELLOW -> Color.rgb(250, 204, 21)
        MoodColor.RED -> Color.rgb(239, 68, 68)
    }
}
