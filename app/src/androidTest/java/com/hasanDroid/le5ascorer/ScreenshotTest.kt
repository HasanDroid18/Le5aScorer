package com.hasanDroid.le5ascorer

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

/**
 * Walks the app and captures a PNG of every redesigned screen.
 *
 * Run by .github/workflows/screenshots.yml on an emulator; the images are pulled
 * off the device and committed to docs/screenshots/. This is the only way to see
 * the redesign, since the container this was developed in cannot reach Google's
 * Maven and so has no Android toolchain at all.
 *
 * Driven with UiAutomator rather than Espresso on purpose: Espresso waits for the
 * main looper to go idle, and the splash and end-game screens run Lottie
 * animations that would keep it busy until the test timed out.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val pkg = context.packageName

    private val outputDir: File by lazy {
        File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
    }

    @Before
    fun setUp() {
        device.setOrientationNatural()
    }

    @Test
    fun captureAllScreens() {
        // The CI emulator is created fresh for each run, so the database starts
        // empty. Deliberately not calling deleteDatabase() here: the application
        // has already started and VersionManager touches the database on a
        // background coroutine, so pulling the file out from under it would be a
        // race rather than a reset.
        val matchId = ScreenshotSeed.seed(context)

        launchApp()
        // Anchor on the tab container, never on recyclerViewInProgress: the
        // fragment sets the list to GONE while it is empty, so waiting on the
        // list means waiting on data as well as on the screen — and a GONE view
        // is absent from the hierarchy, so the failure reads as "screen never
        // appeared" when the screen was there all along.
        waitForList()
        shot("01-match-list")

        // Completed tab: empty state.
        click(By.text("Completed"))
        waitFor(id("layoutCompleted"))
        shot("02-empty-state")
        click(By.text("In Progress"))
        waitForList()

        // The overflow menu and the delete dialog both rendered as white boxes
        // before the theme was reparented to Material3 Dark, so they are the two
        // most important surfaces in this run.
        click(id("buttonMenu"))
        waitFor(By.text("Duplicate"))
        shot("03-overflow-menu")

        click(By.text("Delete"))
        waitFor(By.textContains("permanently removed"))
        shot("04-delete-dialog")
        click(By.text("Cancel"))
        waitForList()

        // New match: recent-player chips plus the four name fields.
        click(id("fab"))
        waitFor(id("editPlayer1"))
        shot("05-new-match")
        device.pressBack()
        waitForList()

        // Scoreboard — the screen whose column alignment and tabular figures
        // this redesign specifically claims to have fixed.
        click(By.textContains("Ahmad"))
        waitFor(id("cardPlayerNames"))
        shot("06-scoreboard")

        // Round entry, nothing dealt: the heart pool full, both honour cards
        // unclaimed, Save disabled.
        click(id("buttonAddRound"))
        waitFor(id("layoutHeartPool"))
        shot("07-round-entry-empty")

        // Every player row carries the same four button ids, so findObject
        // returns the first row's — which is what we want: this deals to
        // player 1 and leaves the other three rows untouched for contrast.
        repeat(6) { click(id("buttonRowHearts")) }
        shot("08-round-entry-hearts")

        click(id("buttonRowQueen"))
        click(id("buttonRowTen"))
        device.waitForIdle(IDLE_MS)
        shot("09-round-entry-dealt")

        // Landscape. There is no layout-land for this screen any more — the
        // rebuilt design is a single scrolling column, so landscape is the same
        // layout scrolled. This shot is what proves that claim.
        device.setOrientationLeft()
        device.waitForIdle(IDLE_MS)
        waitFor(id("layoutHeartPool"))
        shot("10-round-entry-landscape")
        device.setOrientationNatural()
        device.waitForIdle(IDLE_MS)

        device.pressBack()
        waitFor(id("cardPlayerNames"))
        device.pressBack()
        waitForList()

        // Settings — the share icon here used to be invisible.
        click(By.desc("Settings"))
        waitFor(id("layoutContactSupport"))
        shot("11-settings")
        device.pressBack()
        waitForList()

        // End of match: banner, confetti and dialog.
        //
        // Open the scoreboard first and push the match over the line while it is
        // on screen. Seeding first would finish the match before we navigate,
        // and a finished match leaves the In Progress tab — so the click that
        // opens it would be looking on the wrong tab. This way the banner and
        // dialog also arrive live, through the Flow, which is what they do in
        // real use.
        click(By.textContains("Ahmad"))
        waitFor(id("cardPlayerNames"))
        ScreenshotSeed.seedToGameOver(context, matchId)
        waitFor(By.text("Game Over"), timeout = LONG_TIMEOUT)
        shot("12-end-game")
    }

    // ================================================================
    // Helpers
    // ================================================================

    private fun launchApp() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(pkg)
            ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), LONG_TIMEOUT)
        // The splash runs a Lottie animation before handing off to MainActivity.
        device.wait(Until.hasObject(id("toolbar")), LONG_TIMEOUT)
    }

    private fun id(name: String): BySelector = By.res(pkg, name)

    /**
     * Waits for the In Progress tab, then for the seeded card actually to be on
     * it.
     *
     * Two waits rather than one because they fail for different reasons and the
     * distinction is the whole diagnosis: layoutInProgress is the container and
     * is present whatever the data does, so a miss there means the screen never
     * arrived. The card text only appears once the seeded matches have reached
     * the UI, so a miss there means seeding did not land — which is worth a
     * longer timeout, since it waits on a Room write propagating through a Flow
     * rather than on a layout pass.
     */
    private fun waitForList() {
        waitFor(id("layoutInProgress"))
        waitFor(By.textContains("Ahmad"), timeout = LONG_TIMEOUT)
    }

    /**
     * Waits for a selector, and on failure writes the window hierarchy next to
     * the screenshots before throwing.
     *
     * Without the dump, a miss says only which id was not found — and a view
     * that is merely GONE is indistinguishable from one that was never
     * inflated, since neither appears in the hierarchy. The dump makes the
     * difference readable from CI instead of guessable.
     */
    private fun waitFor(selector: BySelector, timeout: Long = TIMEOUT) {
        val found = device.wait(Until.hasObject(selector), timeout)
        if (!found) {
            runCatching {
                device.dumpWindowHierarchy(File(outputDir, "hierarchy-on-failure.xml"))
            }
            error("timed out waiting for $selector")
        }
    }

    private fun click(selector: BySelector) {
        waitFor(selector)
        device.findObject(selector).click()
        device.waitForIdle(IDLE_MS)
    }

    private fun shot(name: String) {
        // Let any transition settle before grabbing the frame.
        device.waitForIdle(IDLE_MS)
        Thread.sleep(SETTLE_MS)
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("uiAutomation returned no screenshot for $name")
        FileOutputStream(File(outputDir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val LONG_TIMEOUT = 30_000L
        const val IDLE_MS = 3_000L
        const val SETTLE_MS = 600L
    }
}
