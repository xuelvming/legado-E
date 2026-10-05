package io.legado.app

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.help.ProcessTextHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.storage.BackupConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.defaultSharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SelectionTextReceiverActivity : Activity()

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 23)
class TextSelectionAppDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val receiver = ComponentName(
        instrumentation.context.packageName,
        SelectionTextReceiverActivity::class.java.name
    )
    private val keys = listOf(
        PreferKey.selectionApp, PreferKey.autoOpenSelectionApp,
        PreferKey.expandTextMenu, PreferKey.textSelectAble,
        PreferKey.ttsHighlightColor, PreferKey.ttsHighlightBold,
        PreferKey.ttsHighlightUnderline
    )
    private var savedPreferences = emptyMap<String, Any?>()
    private val ReadBookActivity.readView: ReadView
        get() = findViewById(R.id.read_view)

    @Before
    fun savePreferences() {
        check(BuildConfig.DEBUG) { "Selection tests must use the isolated debug application." }
        savedPreferences = context.defaultSharedPreferences.all.filterKeys { it in keys }
        context.defaultSharedPreferences.edit {
            keys.forEach { remove(it) }
            putBoolean(PreferKey.textSelectAble, true)
        }
    }

    @After
    fun restorePreferences() {
        context.defaultSharedPreferences.edit {
            keys.forEach { remove(it) }
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
    }

    @Test
    fun intentPreservesExactTextAndManualReadOnlyBehavior() {
        listOf("dictionary", "  A sentence.\nNext paragraph: \u4f60\u597d \uD83D\uDE00!  ", "word ".repeat(4000))
            .forEach { text ->
                val intent = ProcessTextHelp.createIntent(receiver, text, readOnly = true)
                assertEquals(Intent.ACTION_PROCESS_TEXT, intent.action)
                assertEquals("text/plain", intent.type)
                assertEquals(receiver, intent.component)
                assertEquals(text, intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
                assertTrue(intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
            }

        val manual = ProcessTextHelp.createIntent(receiver)
        assertFalse(manual.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true))
        assertFalse(manual.hasExtra(Intent.EXTRA_PROCESS_TEXT))
        assertEquals(receiver, ComponentName.unflattenFromString(receiver.flattenToString()))
    }

    @Test
    fun readAloudBoldPreservesParagraphGeometryAndMeasurementPaint() {
        withReader { scenario, _ ->
            scenario.onActivity { activity ->
                context.defaultSharedPreferences.edit {
                    putBoolean(PreferKey.ttsHighlightBold, true)
                }
                val previousOptimizeRender = AppConfig.optimizeRender
                AppConfig.optimizeRender = false
                try {
                    val first = textLine("first line", paragraphEnd = false)
                    val second = textLine("second line", paragraphEnd = true)
                    val third = textLine("third line", paragraphEnd = true)
                    val page = TextPage(
                        text = "first linesecond line\nthird line\n",
                        title = "TTS rendering test"
                    ).apply {
                        addLine(first)
                        addLine(second)
                        addLine(third)
                    }

                    page.upPageAloudSpan(first.text.length)
                    assertTrue(first.isReadAloud)
                    assertTrue(second.isReadAloud)
                    assertFalse(third.isReadAloud)

                    val geometry = second.columns.map { it.start to it.end }
                    val paint = ChapterProvider.contentPaint
                    val measuredWidth = paint.measureText(second.text)
                    val wasFakeBold = paint.isFakeBoldText
                    val view = activity.readView.curPage
                        .findViewById<ContentTextView>(R.id.content_text_view)
                    val bitmap = Bitmap.createBitmap(400, 120, Bitmap.Config.ARGB_8888)
                    second.draw(view, Canvas(bitmap))
                    bitmap.recycle()

                    assertEquals(geometry, second.columns.map { it.start to it.end })
                    assertEquals(measuredWidth, paint.measureText(second.text), 0f)
                    assertEquals(wasFakeBold, paint.isFakeBoldText)
                } finally {
                    AppConfig.optimizeRender = previousOptimizeRender
                }
            }
        }
    }

    @Test
    fun pickerFindsExternalReceiverAndFiltersUnsafeActivities() {
        val apps = ProcessTextHelp.getSelectionApps(context)
        assertTrue(apps.any { ProcessTextHelp.component(it) == receiver })
        assertTrue(apps.none { it.activityInfo.packageName == context.packageName })
        assertEquals(apps.size, apps.map { ProcessTextHelp.component(it) }.distinct().size)

        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "test.external"
                name = "test.external.Dictionary"
                exported = true
                enabled = true
                applicationInfo = ApplicationInfo().apply { enabled = true }
            }
        }
        assertTrue(ProcessTextHelp.isSelectionApp(context, info))
        info.activityInfo.exported = false
        assertFalse(ProcessTextHelp.isSelectionApp(context, info))
        info.activityInfo.exported = true
        info.activityInfo.enabled = false
        assertFalse(ProcessTextHelp.isSelectionApp(context, info))
        info.activityInfo.enabled = true
        info.activityInfo.applicationInfo.enabled = false
        assertFalse(ProcessTextHelp.isSelectionApp(context, info))
        info.activityInfo.applicationInfo.enabled = true
        info.activityInfo.permission = "io.legado.app.permission.UNGRANTED_TEST_PERMISSION"
        assertFalse(ProcessTextHelp.isSelectionApp(context, info))
    }

    @Test
    fun launchRevalidatesTargetsAndReportsExpectedFailures() {
        val recording = RecordingContext(context)
        val text = "A complete sentence.\nAnother paragraph."
        assertTrue(ProcessTextHelp.launch(recording, receiver.flattenToString(), text))
        assertEquals(text, recording.intents.single().getStringExtra(Intent.EXTRA_PROCESS_TEXT))

        recording.intents.clear()
        for (target in listOf("malformed", "missing.package/missing.package.Dictionary")) {
            assertFalse(ProcessTextHelp.launch(recording, target, text))
        }
        assertFalse(ProcessTextHelp.launch(recording, receiver.flattenToString(), " \n "))
        assertTrue(recording.intents.isEmpty())

        for (error in listOf(ActivityNotFoundException("Removed"), SecurityException("Denied"))) {
            recording.failure = error
            assertFalse(ProcessTextHelp.launch(recording, receiver.flattenToString(), text))
            assertEquals(error, AppLog.logs.first().third)
            assertFalse(AppLog.logs.first().second.contains(text))
        }
    }

    @Test
    fun preferencesRememberTargetAndRespectReadingBackupExclusions() {
        assertEquals("", AppConfig.selectionApp)
        assertFalse(AppConfig.autoOpenSelectionApp)
        AppConfig.selectionApp = receiver.flattenToString()
        assertFalse(AppConfig.autoOpenSelectionApp)
        AppConfig.autoOpenSelectionApp = true
        AppConfig.autoOpenSelectionApp = false
        assertEquals(receiver.flattenToString(), AppConfig.selectionApp)
        AppConfig.autoOpenSelectionApp = true
        AppConfig.selectionApp = ""
        assertFalse(AppConfig.autoOpenSelectionApp)

        val ignored = BackupConfig.ignoreConfig.toMap()
        try {
            BackupConfig.ignoreConfig.clear()
            assertTrue(BackupConfig.keyIsNotIgnore(PreferKey.selectionApp))
            assertTrue(BackupConfig.keyIsNotIgnore(PreferKey.autoOpenSelectionApp))
            BackupConfig.ignoreKeys.forEach { BackupConfig.ignoreConfig[it] = true }
            assertFalse(BackupConfig.keyIsNotIgnore(PreferKey.selectionApp))
            assertFalse(BackupConfig.keyIsNotIgnore(PreferKey.autoOpenSelectionApp))
        } finally {
            BackupConfig.ignoreConfig.clear()
            BackupConfig.ignoreConfig.putAll(ignored)
        }
    }

    @Test
    fun wordAndSentenceLaunchOnlyOnReleaseAndDoNotRelaunchOnResume() {
        withReader { scenario, monitor ->
            AppConfig.selectionApp = receiver.flattenToString()
            AppConfig.autoOpenSelectionApp = true
            repeat(2) { index ->
                beginSelection(scenario)
                scenario.onActivity { activity ->
                    assertEquals(index, monitor.hits)
                    assertEquals("alpha", activity.selectedText)
                    touch(activity, MotionEvent.ACTION_UP)
                    assertEquals(index + 1, monitor.hits)
                    assertFalse(activity.readView.isTextSelected)
                    assertFalse(activity.textActionMenu.isShowing)
                    touch(activity, MotionEvent.ACTION_UP)
                    activity.onTextSelectionComplete()
                    assertEquals(index + 1, monitor.hits)
                }
            }
            beginSelection(scenario)
            scenario.onActivity { activity ->
                touch(activity, MotionEvent.ACTION_MOVE, column = 15)
                assertEquals("alpha beta gamma", activity.selectedText)
                assertEquals(2, monitor.hits)
                touch(activity, MotionEvent.ACTION_UP, column = 15)
                assertEquals(3, monitor.hits)
                assertFalse(activity.readView.isTextSelected)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(3, monitor.hits)
        }
    }

    @Test
    fun cancellationNeverLaunchesAndCursorReleaseLaunchesOnce() {
        withReader { scenario, monitor ->
            AppConfig.selectionApp = receiver.flattenToString()
            AppConfig.autoOpenSelectionApp = true
            beginSelection(scenario)
            scenario.onActivity { activity ->
                touch(activity, MotionEvent.ACTION_CANCEL)
                assertEquals(0, monitor.hits)
                assertTrue(activity.readView.isTextSelected)
                assertTrue(activity.textActionMenu.isShowing)
                handleTouch(activity, MotionEvent.ACTION_DOWN)
                handleTouch(activity, MotionEvent.ACTION_CANCEL)
                handleTouch(activity, MotionEvent.ACTION_UP)
                assertEquals(0, monitor.hits)
                handleTouch(activity, MotionEvent.ACTION_DOWN)
                handleTouch(activity, MotionEvent.ACTION_UP)
                handleTouch(activity, MotionEvent.ACTION_UP)
                assertEquals(1, monitor.hits)
                assertFalse(activity.readView.isTextSelected)
            }
        }
    }

    @Test
    fun offUnconfiguredAndUnavailableTargetsKeepTheSelectionMenu() {
        withReader { scenario, monitor ->
            for (expanded in listOf(false, true)) {
                context.defaultSharedPreferences.edit { putBoolean(PreferKey.expandTextMenu, expanded) }
                for ((target, enabled) in listOf(
                    receiver.flattenToString() to false,
                    "" to true,
                    "missing.package/missing.package.Dictionary" to true
                )) {
                    AppConfig.selectionApp = target
                    AppConfig.autoOpenSelectionApp = enabled
                    beginSelection(scenario)
                    scenario.onActivity { activity ->
                        activity.textActionMenu.upMenu()
                        touch(activity, MotionEvent.ACTION_UP)
                        assertEquals(0, monitor.hits)
                        assertTrue(activity.readView.isTextSelected)
                        assertEquals("alpha", activity.selectedText)
                        assertTrue(activity.textActionMenu.isShowing)
                    }
                }
            }
        }
    }

    @Test
    fun phraseDraggingSnapsBothDirectionsFromEveryLetter() {
        withReader { scenario, monitor ->
            for ((first, second) in listOf("put" to "down", "get" to "up")) {
                val text = "$first $second"
                for (anchor in text.indices.filter { text[it] != ' ' }) {
                    beginSelection(scenario, text = text, column = anchor)
                    scenario.onActivity { activity ->
                        val targets = if (anchor < first.length) {
                            first.length + 1 until text.length
                        } else {
                            first.indices
                        }
                        for (target in targets) {
                            touch(activity, MotionEvent.ACTION_MOVE, column = target)
                            assertEquals("anchor=$anchor target=$target", text, activity.selectedText)
                            assertHighlightedText(activity, text)
                            touch(activity, MotionEvent.ACTION_MOVE, column = anchor)
                            assertEquals(
                                if (anchor < first.length) first else second,
                                activity.selectedText
                            )
                        }
                        touch(activity, MotionEvent.ACTION_CANCEL)
                        assertEquals(0, monitor.hits)
                    }
                }
            }
        }
    }

    @Test
    fun draggingShrinksReversesAndExcludesOnlyOuterSeparators() {
        withReader { scenario, monitor ->
            beginSelection(scenario, text = "put  down, now", column = 6)
            scenario.onActivity { activity ->
                for ((column, expected) in listOf(
                    1 to "put  down", 12 to "down, now", 6 to "down",
                    9 to "down", 10 to "down", 3 to "down", -1 to "put  down",
                    14 to "down, now"
                )) {
                    touch(activity, MotionEvent.ACTION_MOVE, column)
                    assertEquals("column=$column", expected, activity.selectedText)
                    assertHighlightedText(activity, expected)
                }
                touch(activity, MotionEvent.ACTION_UP, column = 12)
                assertTrue(activity.textActionMenu.isShowing)
                assertEquals(0, monitor.hits)
                val page = activity.readView.curPage
                page.selectStartMove(
                    page.imgBgPaddingStart + 40f + 6 * 20f,
                    page.headerHeight + ChapterProvider.paddingTop + 60f
                )
                assertEquals("own, now", activity.selectedText)
            }
        }
    }

    @Test
    fun wrappedWordsAndUnicodeColumnsUseLogicalWordBoundaries() {
        withReader { scenario, _ ->
            for (html in listOf(false, true)) {
                beginSelection(
                    scenario, text = "\uD83D\uDE00 pu", followingLines = listOf("t down"),
                    paragraphEnds = setOf(1), column = 3, html = html
                )
                scenario.onActivity { activity ->
                    assertEquals("put", activity.selectedText)
                    touch(activity, MotionEvent.ACTION_MOVE, column = 3, line = 1)
                    assertEquals("put down", activity.selectedText)
                    touch(activity, MotionEvent.ACTION_MOVE, column = 3, line = 0)
                    assertEquals("put", activity.selectedText)
                    touch(activity, MotionEvent.ACTION_CANCEL)
                }
            }
            beginSelection(
                scenario, text = "get", followingLines = listOf("up"),
                paragraphEnds = setOf(0, 1), column = 1
            )
            scenario.onActivity { activity ->
                touch(activity, MotionEvent.ACTION_MOVE, column = 0, line = 1)
                assertEquals("get\nup", activity.selectedText)
                touch(activity, MotionEvent.ACTION_CANCEL)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun finalReleaseSendsTheExactSnappedPhraseOnce() {
        val monitor = RecordingSelectionMonitor()
        withReader(monitor) { scenario, _ ->
            AppConfig.selectionApp = receiver.flattenToString()
            AppConfig.autoOpenSelectionApp = true
            beginSelection(scenario, text = "put down,", column = 1)
            scenario.onActivity { activity ->
                assertEquals("put", activity.selectedText)
                assertTrue(monitor.intents.isEmpty())
                touch(activity, MotionEvent.ACTION_UP, column = 5)
                assertEquals(1, monitor.intents.size)
                assertEquals(
                    "put down",
                    monitor.intents.single().getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                )
                assertFalse(activity.readView.isTextSelected)
                touch(activity, MotionEvent.ACTION_UP, column = 5)
                activity.onTextSelectionComplete()
                assertEquals(1, monitor.intents.size)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(1, monitor.intents.size)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun finalReleaseUpdatesTheLastMoveAndOutsideReleaseKeepsSelection() {
        val monitor = RecordingSelectionMonitor()
        withReader(monitor) { scenario, _ ->
            AppConfig.selectionApp = receiver.flattenToString()
            AppConfig.autoOpenSelectionApp = true
            beginSelection(scenario, text = "put down now,", column = 1)
            scenario.onActivity { activity ->
                touch(activity, MotionEvent.ACTION_MOVE, column = 5)
                assertEquals("put down", activity.selectedText)
                assertTrue(monitor.intents.isEmpty())
                touch(activity, MotionEvent.ACTION_UP, column = 12)
                assertEquals(
                    "put down now",
                    monitor.intents.single().getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                )
            }
            beginSelection(scenario, text = "put down now,", column = 1)
            scenario.onActivity { activity ->
                touch(activity, MotionEvent.ACTION_MOVE, column = 5)
                assertHighlightedText(activity, "put down")
                touch(activity, MotionEvent.ACTION_UP, column = 5, line = -10)
                assertEquals(2, monitor.intents.size)
                assertEquals(
                    "put down",
                    monitor.intents.last().getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                )
                assertFalse(activity.readView.isTextSelected)
            }
        }
    }

    private fun assertHighlightedText(activity: ReadBookActivity, expected: String) {
        val highlighted = activity.readView.curPage.textPage.lines.flatMap { it.columns }
            .filterIsInstance<TextBaseColumn>().filter { it.selected }
            .joinToString("") { it.charData }
        assertEquals(expected, highlighted)
    }

    private fun withReader(
        monitor: Instrumentation.ActivityMonitor = Instrumentation.ActivityMonitor(
            IntentFilter(Intent.ACTION_PROCESS_TEXT).apply { addDataType("text/plain") },
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true
        ),
        block: (ActivityScenario<ReadBookActivity>, Instrumentation.ActivityMonitor) -> Unit
    ) {
        val previousBook = ReadBook.book
        instrumentation.addMonitor(monitor)
        val intent = Intent(context, ReadBookActivity::class.java)
            .putExtra("bookUrl", "selection-test://no-book")
            .putExtra("inBookshelf", false)
        ReadBook.book = null
        try {
            ActivityScenario.launch<ReadBookActivity>(intent).use { scenario ->
                val initialized = CountDownLatch(1)
                scenario.onActivity {
                    ViewModelProvider(it)[ReadBookViewModel::class.java]
                        .initData(intent) { initialized.countDown() }
                }
                assertTrue(initialized.await(10, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                block(scenario, monitor)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            ReadBook.book = previousBook
        }
    }

    private fun beginSelection(
        scenario: ActivityScenario<ReadBookActivity>,
        text: String = "alpha beta gamma",
        column: Int = 1,
        followingLines: List<String> = emptyList(),
        paragraphEnds: Set<Int> = setOf(followingLines.size),
        html: Boolean = false
    ) {
        scenario.onActivity { activity ->
            val readView = activity.readView
            readView.cancelSelect()
            val page = TextPage(text = text, title = "Selection test")
            (listOf(text) + followingLines).forEachIndexed { lineIndex, lineText ->
                val line = TextLine(
                    text = lineText,
                    lineTop = ChapterProvider.paddingTop + 30f + lineIndex * 80f,
                    lineBottom = ChapterProvider.paddingTop + 90f + lineIndex * 80f,
                    isParagraphEnd = lineIndex in paragraphEnds
                )
                var offset = 0
                var index = 0
                while (offset < lineText.length) {
                    val end = offset + Character.charCount(lineText.codePointAt(offset))
                    val character = lineText.substring(offset, end)
                    line.addColumn(
                        if (html) {
                            TextHtmlColumn(
                                30f + index * 20f, 50f + index * 20f,
                                character, 20f, null, null
                            )
                        } else {
                            TextColumn(30f + index * 20f, 50f + index * 20f, character)
                        }
                    )
                    offset = end
                    index++
                }
                page.addLine(line)
            }
            readView.curPage.setContent(page)
            touch(activity, MotionEvent.ACTION_DOWN, column)
        }
        SystemClock.sleep(700)
        scenario.onActivity {
            assertTrue("Long press must select the fixture word", it.readView.isTextSelected)
            assertNotNull(it.readView.pageDelegate)
        }
    }

    private fun textLine(text: String, paragraphEnd: Boolean): TextLine {
        return TextLine(
            text = text,
            lineTop = 0f,
            lineBase = 60f,
            lineBottom = 80f,
            isParagraphEnd = paragraphEnd
        ).apply {
            text.forEachIndexed { index, character ->
                addColumn(TextColumn(index * 20f, (index + 1) * 20f, character.toString()))
            }
        }
    }

    private fun touch(activity: ReadBookActivity, action: Int, column: Int = 1, line: Int = 0) {
        val page = activity.readView.curPage
        val event = MotionEvent.obtain(
            0, SystemClock.uptimeMillis(), action,
            page.imgBgPaddingStart + 40f + column * 20f,
            page.headerHeight + ChapterProvider.paddingTop + 60f + line * 80f, 0
        )
        try {
            activity.readView.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun handleTouch(activity: ReadBookActivity, action: Int) {
        val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, 0f, 0f, 0)
        try {
            activity.onTouch(activity.findViewById<View>(R.id.cursor_right), event)
        } finally {
            event.recycle()
        }
    }

    private class RecordingContext(context: Context) : ContextWrapper(context) {
        val intents = arrayListOf<Intent>()
        var failure: RuntimeException? = null

        override fun startActivity(intent: Intent) {
            intents.add(intent)
            failure?.let { throw it }
        }
    }

    @RequiresApi(26)
    private class RecordingSelectionMonitor : Instrumentation.ActivityMonitor() {
        val intents = arrayListOf<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_PROCESS_TEXT) return null
            intents.add(Intent(intent))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }
}
