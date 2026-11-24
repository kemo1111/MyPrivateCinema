package com.hexated

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.lagradost.cloudstream3.ErrorLoadingException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, qualifiers = "w640dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TmdbSettingsLandscapeTest {
    private val validKey = "0123456789abcdef0123456789ABCDEF"

    @Before fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        TmdbKey.init(activity)
        TmdbKey.clear()
    }

    @Test fun formatCheck() {
        assertTrue(TmdbKey.isValid(validKey))
        assertTrue(TmdbKey.isValid("  $validKey \n"))
        assertFalse(TmdbKey.isValid(""))
        assertFalse(TmdbKey.isValid("short"))
        assertFalse(TmdbKey.isValid(validKey + "0"))
        assertFalse(TmdbKey.isValid("g123456789abcdef0123456789ABCDEF"))
        // a v4 read token is a long JWT, not accepted by the v3 api_key parameter
        assertFalse(TmdbKey.isValid("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.abc"))
    }

    @Test fun missingKeyGivesReadableErrorNotEmptyRequest() {
        assertEquals("", TmdbKey.get())
        assertFalse(TmdbKey.hasKey())
        val e = try { TmdbKey.require(); null } catch (e: ErrorLoadingException) { e }
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("TMDB API key"))
    }

    @Test fun invalidKeyIsNotSavedValidKeyIsSavedAndClears() {
        assertFalse(TmdbKey.set("nope"))
        assertEquals("", TmdbKey.get())
        assertTrue(TmdbKey.set(" $validKey "))
        assertEquals(validKey, TmdbKey.get())
        TmdbKey.require() // no throw
        TmdbKey.clear()
        assertEquals("", TmdbKey.get())
    }

    @Test fun dialogSaveFlowAndAttribution() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dialog = TmdbSettingsDialog.build(activity)
        dialog.show()
        ShadowLooper.idleMainLooper()
        val root = dialog.window!!.decorView
        layout(root, 560, 300)
        val input = root.findViewWithTag<EditText>("tmdb_key_input")
        val status = root.findViewWithTag<TextView>("tmdb_key_status")
        val logo = root.findViewWithTag<ImageView>("tmdb_logo")
        val attribution = root.findViewWithTag<TextView>("tmdb_attribution")
        assertNotNull(logo); assertNotNull(attribution)
        assertEquals(TmdbSettingsDialog.ATTRIBUTION, attribution.text.toString())
        assertTrue("logo has drawable", logo.drawable != null && logo.width > 0 && logo.height > 0)
        assertTrue(status.text.toString().startsWith("No key saved"))
        shot(root, "land-settings-empty.png", 560, 300)

        input.setText("bad")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(status.text.toString().contains("not a valid"))
        assertEquals("", TmdbKey.get())

        input.setText(validKey)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(validKey, TmdbKey.get())
        assertEquals("", input.text.toString())
        assertTrue(status.text.toString().startsWith("A key is saved"))
        // the saved key itself is never shown in the dialog
        assertFalse(allText(root).contains(validKey))
        layout(root, 560, 300)
        shot(root, "land-settings-saved.png", 560, 300)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        assertEquals("", TmdbKey.get())
        dialog.dismiss()
    }

    private fun layout(v: View, w: Int, h: Int) {
        val d = v.resources.displayMetrics.density
        v.measure(View.MeasureSpec.makeMeasureSpec((w * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((h * d).toInt(), View.MeasureSpec.AT_MOST))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }
    private fun allText(v: View): String {
        val sb = StringBuilder()
        if (v is TextView) sb.append(v.text)
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) sb.append(allText(v.getChildAt(i)))
        return sb.toString()
    }
    private fun shot(v: View, name: String, w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(v.measuredWidth.coerceAtLeast(1), v.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(android.graphics.Color.WHITE); v.draw(c)
        val dir = File("build/ui-evidence"); dir.mkdirs()
        FileOutputStream(File(dir, "api${android.os.Build.VERSION.SDK_INT}-$name")).use { assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
