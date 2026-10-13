package com.lightcraft.mobile

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.onNodeWithText("Ajustes", useUnmergedTree = true).assertExists()
        compose.runOnUiThread { model.selectPreset(model.ui.value.presets.first().id) }
        compose.waitUntil(30_000) { model.ui.value.edit.preset != null && !model.ui.value.rendering }
        compose.onNodeWithText("Intensidade").assertExists()
        compose.onNodeWithText("Ajustes", useUnmergedTree = true).performClick()
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
        compose.onNodeWithText("Salvar ↗").performClick()
        compose.waitUntil(60_000) { !model.ui.value.exporting && model.ui.value.message?.contains("Foto salva") == true }
        assertTrue(File(app.filesDir,"draft.json").isFile)
        input.delete()
    }
}
