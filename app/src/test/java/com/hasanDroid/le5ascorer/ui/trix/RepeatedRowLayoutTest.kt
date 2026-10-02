package com.hasanDroid.le5ascorer.ui.trix

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The queen and place rows are each included four times, so their buttons share
 * ids. If those buttons saved their own state, a rotation would restore the last
 * row's checked state into every row and fire the checked listeners, rewriting
 * the answers held in the ViewModel. The ViewModel is the source of truth, so
 * these buttons must not save state at all.
 */
class RepeatedRowLayoutTest {

    private val buttonTags = Regex("""<(Button|com\.google\.android\.material\.button\.MaterialButton)\b[^>]*>""")

    private fun buttonsIn(layout: String): List<String> {
        val file = File("src/main/res/layout/$layout.xml")
        return buttonTags.findAll(file.readText()).map { it.value }.toList()
    }

    @Test
    fun `buttons in rows included more than once do not save their own state`() {
        listOf("view_trix_queen_row", "view_trix_place_row").forEach { layout ->
            val buttons = buttonsIn(layout)
            assertTrue("$layout has no buttons", buttons.isNotEmpty())
            buttons.forEach { tag ->
                assertTrue("$layout: $tag", tag.contains("""android:saveEnabled="false""""))
            }
        }
    }
}
