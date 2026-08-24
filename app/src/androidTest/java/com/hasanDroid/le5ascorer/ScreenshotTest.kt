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
        waitFor(id("recyclerViewInProgress"))
        shot("01-match-list")

        // Completed tab: empty state.
        click(By.text("Completed"))
        waitFor(id("layoutCompleted"))
        shot("02-empty-state")
        click(By.text("In Progress"))
        waitFor(id("recyclerViewInProgress"))

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
        waitFor(id("recyclerViewInProgress"))

        // New match: recent-player chips plus the four name fields.
        click(id("fab"))
        waitFor(id("editPlayer1"))
        shot("05-new-match")
        device.pressBack()
        waitFor(id("recyclerViewInProgress"))

        // Scoreboard — the screen whose column alignment and tabular figures
        // this redesign specifically claims to have fixed.
        click(By.textContains("Ahmad"))
        waitFor(id("recyclerView"))
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
        waitFor(id("recyclerView"))
        device.pressBack()
        waitFor(id("recyclerViewInProgress"))

        // Settings — the share icon here used to be invisible.
        click(By.desc("Settings"))
        waitFor(id("layoutContactSupport"))
        shot("11-settings")
        device.pressBack()
        waitFor(id("recyclerViewInProgress"))

        // End of match: banner, confetti and dialog.
        ScreenshotSeed.seedToGameOver(context, matchId)
        click(By.textContains("Ahmad"))
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

    private fun waitFor(selector: BySelector, timeout: Long = TIMEOUT) {
        val found = device.wait(Until.hasObject(selector), timeout)
        check(found) { "timed out waiting for $selector" }
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
