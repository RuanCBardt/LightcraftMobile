package com.lightcraft.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class EditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun photoPresetAdjustCropUndoAndGalleryExport() {
        val app = compose.activity.application as Application
        val input = File(app.cacheDir, "editor-flow.jpg")
        val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
        for (y in 0 until 480) for (x in 0 until 360) {
            bitmap.setPixel(x, y, Color.rgb(35+x/3, 55+y/4, 80))
        }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        lateinit var model: EditorViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
        compose.waitUntil(30_000) { model.ui.value.ready }
        compose.runOnUiThread { model.importPhoto(Uri.fromFile(input)) }
        compose.waitUntil(60_000) { model.ui.value.hasPhoto && !model.ui.value.loading && !model.ui.value.rendering }
        compose.onNodeWithText("Ferramentas", useUnmergedTree = true).assertExists()
        compose.runOnUiThread { model.selectPreset(model.ui.value.presets.first().id) }
        compose.waitUntil(30_000) { model.ui.value.edit.preset != null && !model.ui.value.rendering }
        compose.onNodeWithTag("preset:${model.ui.value.edit.preset}").performClick()
        compose.onNodeWithTag("presetAmount").assertExists()
        compose.onNodeWithTag("confirmAmount").performClick()
        compose.onNodeWithText("Ferramentas", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Contraste").assertExists()
        compose.runOnUiThread { model.change(model.ui.value.edit.copy(controls = mapOf("light.exposure" to 0.7f))) }
        compose.onNodeWithText("Recortar", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1:1").performClick()
        compose.onNodeWithText("Aplicar recorte").performClick()
        compose.waitUntil(30_000) { !model.ui.value.rendering && model.ui.value.preview?.let { it.width == it.height } == true }
        compose.runOnUiThread { model.undo() }
        compose.waitUntil(30_000) { !model.ui.value.rendering && model.ui.value.preview?.let { it.width != it.height } == true }
        val screenshot = File(app.getExternalFilesDir(null), "editor.png")
        val image = compose.onRoot().captureToImage()
        screenshot.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("exportPhoto").performClick()
        compose.waitUntil(60_000) { !model.ui.value.exporting && model.ui.value.message?.contains("Foto salva") == true }
        assertTrue(File(app.filesDir,"draft.json").isFile)
        input.delete()
    }

    @Test fun photoSwipesFollowVisiblePresetsAndAmountCanBeCancelled() {
        val app = compose.activity.application as Application
        val input = File(app.cacheDir, "swipe-flow.jpg")
        val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
        // An original procedural landscape, never the user's reference photographs.
        for (y in 0 until 480) for (x in 0 until 360) {
            val mountain = 260 - (130 * kotlin.math.sin(x / 100.0)).toInt()
            val color = when {
                (x-270)*(x-270)+(y-100)*(y-100) < 30*30 -> Color.rgb(245,219,164)
                y < mountain -> Color.rgb(120+y/6,166+y/10,184+y/14)
                y < 370-x/5 -> Color.rgb(88+x/7,105+y/10,90)
                else -> Color.rgb(55+y/7,81+y/9,62+x/10)
            }
            bitmap.setPixel(x,y,color)
        }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        lateinit var model: EditorViewModel
        compose.runOnUiThread { model = ViewModelProvider(compose.activity)[EditorViewModel::class.java] }
        compose.waitUntil(30_000) { model.ui.value.ready && !model.ui.value.loading }
        compose.runOnUiThread { model.importPhoto(Uri.fromFile(input)) }
        compose.waitUntil(60_000) { model.ui.value.hasPhoto && !model.ui.value.loading && !model.ui.value.rendering }
        val first = model.ui.value.presets[0].id
        val second = model.ui.value.presets[1].id
        val photo = compose.onNodeWithTag("photoCanvas")
        fun waitPreset(id: String?) = compose.waitUntil(30_000) { model.ui.value.edit.preset == id && !model.ui.value.rendering }
        photo.performTouchInput { swipeLeft() }; waitPreset(first)
        photo.performTouchInput { swipeLeft() }; waitPreset(second)
        photo.performTouchInput { swipeRight() }; waitPreset(first)
        photo.performTouchInput { swipeUp() }; waitPreset(first)
        photo.performTouchInput { swipe(center, center + Offset(8f,0f), durationMillis = 150) }; waitPreset(first)
        photo.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitUntil(3_000) { compose.onAllNodesWithText("ORIGINAL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ORIGINAL").assertExists()
        photo.performTouchInput { up() }
        compose.onNodeWithText("ORIGINAL").assertDoesNotExist()
        assertEquals(first, model.ui.value.edit.preset)
        val importMenuVisible = compose.onNodeWithTag("presetStrip")
        importMenuVisible.assertExists()
        // Tap the active tile to edit intensity. Cancel restores the previous value;
        // confirming a whole slider gesture adds exactly one undo step.
        compose.onNodeWithTag("preset:$first").performClick()
        compose.onNodeWithTag("presetAmount").performTouchInput { click(Offset(width*0.25f,height/2f)) }
        compose.waitUntil(30_000) { model.ui.value.edit.amount < 80f && !model.ui.value.rendering }
        compose.onNodeWithTag("cancelAmount").performClick()
        assertEquals(100f, model.ui.value.edit.amount)
        compose.onNodeWithTag("preset:$first").performClick()
        compose.onNodeWithTag("presetAmount").performTouchInput { click(Offset(width*0.25f,height/2f)) }
        compose.waitUntil(30_000) { model.ui.value.edit.amount < 80f && !model.ui.value.rendering }
        screenshot("intensity.png")
        compose.onNodeWithTag("confirmAmount").performClick()
        compose.waitUntil(30_000) { !model.ui.value.rendering }
        val savedAmount = org.json.JSONObject(File(app.filesDir, "draft.json").readText()).getJSONObject("edit").getDouble("amount")
        assertEquals(model.ui.value.edit.amount.toDouble(), savedAmount, 0.001)
        compose.onNodeWithText("Desfazer").performClick()
        assertEquals(100f, model.ui.value.edit.amount)
        // Swipes use the selected group, preserve manual adjustments and stop at its ends.
        val group = model.ui.value.presets.first().group
        compose.runOnUiThread {
            model.change(model.ui.value.edit.copy(controls = mapOf("light.exposure" to 0.4f)))
            model.selectGroup(group)
            model.selectPreset(null)
        }
        waitPreset(null)
        photo.performTouchInput { swipeRight() }; waitPreset(null)
        val visible = model.ui.value.visiblePresets()
        photo.performTouchInput { swipeLeft() }; waitPreset(visible.first().id)
        assertEquals(0.4f, model.ui.value.edit.controls["light.exposure"])
        compose.runOnUiThread { model.selectPreset(visible.last().id) }; waitPreset(visible.last().id)
        photo.performTouchInput { swipeLeft() }; waitPreset(visible.last().id)
        compose.onNodeWithText("Recortar", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { !model.ui.value.rendering }
        photo.performTouchInput { swipeLeft() }
        assertEquals(visible.last().id, model.ui.value.edit.preset)
        compose.onNodeWithText("Aplicar recorte").performClick()
        compose.runOnUiThread { model.selectGroup("Todos"); model.selectPreset(first) }
        waitPreset(first)
        screenshot("presets.png")
        input.delete()
    }

    private fun screenshot(name: String) {
        val app = compose.activity.application
        val file = File(app.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
