package com.example.trafficlight.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import android.widget.EditText
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
    private var syncStatus = "Sync bereit"

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
                setPadding(dp(20), dp(44), dp(20), dp(28))
                addView(MoodDistributionView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(220),
                ).apply {
                    bottomMargin = dp(14)
                })
                addView(sectionTitle(getString(R.string.today)))
                addView(MoodDayGraphView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(188),
                ))
                addView(summaryText(todaySummary(entries)))
                addView(sectionTitle("Einblicke"))
                addView(summaryText(insights(entries)))
                addView(sectionTitle(getString(R.string.last_7_days)))
                addView(WeekSummaryView(context, entries), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(250),
                ))
                addView(sectionTitle(getString(R.string.recent)))
                recentEntries(entries).forEach { entry ->
                    addView(entryRow(entry))
                }
                addView(sectionTitle("Daten"))
                addView(syncStatusView())
                addView(dataActions())
            })
        }
    }

    private fun syncExistingEntries() {
        syncStatus = "Sync läuft..."
        setContentView(buildView())
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
                var imported = 0
                for (item in items) {
                    if (!item.uri.path.orEmpty().startsWith(PATH_PREFIX)) continue
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val timestamp = dataMap.getLong(KEY_TIMESTAMP)
                    val colorValue = dataMap.getString(KEY_COLOR).orEmpty()
                    val color = MoodColor.entries.firstOrNull { it.storedValue == colorValue } ?: continue
                    localStore.save(MoodEntry(
                        timestampMillis = timestamp,
                        color = color,
                        isConflict = dataMap.getBoolean(KEY_CONFLICT),
                        note = dataMap.getString(KEY_NOTE).orEmpty(),
                        tags = dataMap.getStringArrayList(KEY_TAGS).orEmpty(),
                    ))
                    imported++
                }
                syncStatus = "Zuletzt synchronisiert: ${java.time.LocalTime.now().hour}:${java.time.LocalTime.now().minute.toString().padStart(2, '0')} ($imported gesehen)"
                setContentView(buildView())
                items.release()
            }
            .addOnFailureListener {
                syncStatus = "Sync fehlgeschlagen"
                setContentView(buildView())
            }
    }

    private fun todaySummary(entries: List<MoodEntry>): String {
        val today = entries.filterToday()
        val green = today.count { it.color == MoodColor.GREEN }
        val yellow = today.count { it.color == MoodColor.YELLOW }
        val red = today.count { it.color == MoodColor.RED }
        return getString(R.string.today_summary, green, yellow, red)
    }

    private fun insights(entries: List<MoodEntry>): String {
        if (entries.isEmpty()) return "Noch keine Daten."
        val byHour = entries.groupBy {
            Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).hour
        }
        val greenHour = byHour
            .filterValues { it.size >= 2 }
            .maxByOrNull { (_, hourEntries) ->
                hourEntries.count { it.color == MoodColor.GREEN }.toFloat() / hourEntries.size.toFloat()
            }?.key
        val redHour = byHour
            .filterValues { it.size >= 2 }
            .maxByOrNull { (_, hourEntries) ->
                hourEntries.count { it.color == MoodColor.RED }.toFloat() / hourEntries.size.toFloat()
            }?.key
        val conflicts = entries.count { it.isConflict }
        return "Grünster Zeitraum: ${greenHour?.formatHour() ?: "-"}   Rotester Zeitraum: ${redHour?.formatHour() ?: "-"}   Konflikte: $conflicts"
    }

    private fun syncStatusView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = syncStatus
                setTextColor(SOFT_TEXT)
                textSize = 12f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(actionButton("Sync") {
                syncExistingEntries()
            })
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun dataActions(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(actionButton("CSV") {
                shareCsv()
            })
            addView(actionButton("Löschen") {
                confirmClear()
            })
        }
    }

    private fun recentEntries(entries: List<MoodEntry>): List<MoodEntry> {
        return entries.sortedByDescending { it.timestampMillis }.take(7)
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
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(11), 0, dp(11))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
            })
            val details = listOfNotNull(
                "Konflikt".takeIf { entry.isConflict },
                entry.note.takeIf { it.isNotBlank() },
                entry.tags.joinToString(", ").takeIf { entry.tags.isNotEmpty() },
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                addView(TextView(context).apply {
                    text = details
                    setTextColor(SOFT_TEXT)
                    textSize = 13f
                    setPadding(dp(26), dp(4), 0, 0)
                })
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { showEntryActions(entry) }
        }
    }

    private fun showEntryActions(entry: MoodEntry) {
        val actions = arrayOf("Notiz", "Tag", "Konflikt umschalten", "Löschen")
        AlertDialog.Builder(this)
            .setTitle(entry.label())
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showNoteDialog(entry)
                    1 -> showTagDialog(entry)
                    2 -> toggleConflict(entry)
                    3 -> deleteEntry(entry)
                }
            }
            .show()
    }

    private fun actionButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(32, 35, 35))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }
    }

    private fun showNoteDialog(entry: MoodEntry) {
        val input = EditText(this).apply {
            setText(entry.note)
            setTextColor(Color.BLACK)
            hint = "Notiz"
        }
        AlertDialog.Builder(this)
            .setTitle("Notiz")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                store.update(entry.copy(note = input.text.toString()))
                setContentView(buildView())
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showTagDialog(entry: MoodEntry) {
        val tags = arrayOf("Arbeit", "Familie", "Beziehung", "Müde", "Hunger", "Konflikt")
        AlertDialog.Builder(this)
            .setTitle("Tag hinzufügen")
            .setItems(tags) { _, which ->
                store.update(entry.copy(tags = (entry.tags + tags[which]).distinct()))
                setContentView(buildView())
            }
            .show()
    }

    private fun toggleConflict(entry: MoodEntry) {
        store.update(entry.copy(isConflict = !entry.isConflict))
        setContentView(buildView())
    }

    private fun deleteEntry(entry: MoodEntry) {
        store.delete(entry.timestampMillis)
        setContentView(buildView())
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Alle Einträge löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                store.clear()
                setContentView(buildView())
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun shareCsv() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_SUBJECT, "Traffic Light CSV")
            .putExtra(Intent.EXTRA_TEXT, store.csv())
        startActivity(Intent.createChooser(intent, "CSV teilen"))
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

    private fun Int.formatHour(): String = "${toString().padStart(2, '0')}:00"

    companion object {
        private const val PATH_PREFIX = "/mood_entries"
        private const val PATH_SYNC_REQUEST = "/sync_request"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_COLOR = "color"
        private const val KEY_CONFLICT = "conflict"
        private const val KEY_NOTE = "note"
        private const val KEY_TAGS = "tags"
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
                val count = counts[color].orZero()
                val sweep = 360f * count.toFloat() / total.toFloat()
                if (sweep <= 0f) continue
                paint.color = color.toUiColor()
                canvas.drawArc(bounds, startAngle, sweep, false, paint)
                drawSegmentCount(canvas, bounds, startAngle, sweep, count, color)
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

    private fun drawSegmentCount(
        canvas: Canvas,
        bounds: RectF,
        startAngle: Float,
        sweep: Float,
        count: Int,
        color: MoodColor,
    ) {
        if (count <= 0 || sweep < 22f) return

        val angle = Math.toRadians((startAngle + sweep / 2f).toDouble())
        val radius = bounds.width() * 0.41f
        val x = bounds.centerX() + kotlin.math.cos(angle).toFloat() * radius
        val y = bounds.centerY() + kotlin.math.sin(angle).toFloat() * radius

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = bounds.width() * 0.11f
        paint.color = if (color == MoodColor.YELLOW) Color.rgb(30, 28, 15) else Color.WHITE
        canvas.drawText(count.toString(), x, y + bounds.width() * 0.04f, paint)
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
        val labelHeight = 38f
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
        paint.textSize = 28f
        paint.color = Color.rgb(176, 185, 185)
        listOf(3, 6, 9, 12, 15, 18, 21, 0).forEach { hour ->
            val xHour = if (hour == 0) 24 else hour
            val x = (xHour.toFloat() / 24f) * width
            canvas.drawText(hour.toString().padStart(2, '0'), x.coerceIn(18f, width - 18f), height - 6f, paint)
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
        val labelHeight = 96f
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

            if (entries.isEmpty()) {
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(54, 58, 58)
                canvas.drawRoundRect(
                    RectF(left, 0f, left + columnWidth, graphBottom),
                    8f,
                    8f,
                    paint,
                )
            }

            for (color in listOf(MoodColor.RED, MoodColor.YELLOW, MoodColor.GREEN)) {
                val count = counts[color].orZero()
                val segmentHeight = graphBottom * (count.toFloat() / total.toFloat())
                if (count == 0) continue
                paint.color = if (entries.isEmpty()) Color.rgb(54, 58, 58) else color.toUiColor()
                val top = bottom - segmentHeight
                canvas.drawRoundRect(
                    RectF(left, top, left + columnWidth, bottom),
                    8f,
                    8f,
                    paint,
                )
                if (count > 0 && segmentHeight >= 54f) {
                    paint.style = Paint.Style.FILL
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 42f
                    paint.color = if (color == MoodColor.YELLOW) Color.rgb(30, 28, 15) else Color.WHITE
                    canvas.drawText(count.toString(), left + columnWidth / 2f, top + segmentHeight / 2f + 14f, paint)
                }
                bottom -= segmentHeight
            }

            val centerX = left + columnWidth / 2f
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.rgb(232, 239, 235)
            paint.textSize = 44f
            canvas.drawText(day.format(dayFormatter), centerX, graphBottom + 47f, paint)
            paint.color = Color.rgb(176, 185, 185)
            paint.textSize = 32f
            canvas.drawText(day.format(dateFormatter), centerX, graphBottom + 88f, paint)
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
