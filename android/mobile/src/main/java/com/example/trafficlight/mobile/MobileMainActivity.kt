package com.example.trafficlight.mobile

import android.app.Activity
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

class MobileMainActivity : Activity() {
    private lateinit var store: MobileMoodEntryStore
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
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
                addView(header("Traffic Light"))
                addView(subtitle("${entries.size} synced check-ins"))
                addView(sectionTitle("Today"))
                addView(MoodDayGraphView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(140),
                ))
                addView(summaryText(todaySummary(entries)))
                addView(sectionTitle("Last 7 Days"))
                addView(WeekSummaryView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(180),
                ))
                addView(sectionTitle("Recent"))
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
        return "Green $green   Yellow $yellow   Red $red"
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
        return "${color.storedValue.replaceFirstChar { it.uppercase() }} at ${dateTime.hour}:$minute"
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
        val barWidth = width / 24f

        paint.color = Color.rgb(24, 27, 27)
        canvas.drawRoundRect(RectF(0f, 0f, width, height), 14f, 14f, paint)

        val grouped = todayEntries.groupBy { entry ->
            Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).hour
        }

        for (hour in 0..23) {
            val hourEntries = grouped[hour].orEmpty()
            paint.color = hourEntries.lastOrNull()?.color?.toUiColor() ?: Color.rgb(54, 58, 58)
            val filledHeight = if (hourEntries.isEmpty()) height * 0.12f else height * 0.78f
            canvas.drawRoundRect(
                RectF(hour * barWidth + 2f, height - filledHeight, (hour + 1) * barWidth - 2f, height - 10f),
                5f,
                5f,
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
}

class WeekSummaryView(
    context: android.content.Context,
    entries: List<MoodEntry>,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val entriesByDay = entries.groupBy { entry ->
        Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val gap = 10f
        val columnWidth = (width - gap * 6f) / 7f
        val today = LocalDate.now()

        for (index in 0..6) {
            val day = today.minusDays((6 - index).toLong())
            val entries = entriesByDay[day].orEmpty()
            val counts = MoodColor.entries.associateWith { color -> entries.count { it.color == color } }
            val total = counts.values.sum().coerceAtLeast(1)
            var bottom = height
            val left = index * (columnWidth + gap)

            for (color in listOf(MoodColor.RED, MoodColor.YELLOW, MoodColor.GREEN)) {
                val segmentHeight = height * (counts[color].orZero().toFloat() / total.toFloat())
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
