package com.lightcraft.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the real packaged Rust engine across JNI on an Android device. */
class NativeEditorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun call(request: JSONObject): Any {
        val result = JSONObject(NativeEngine.execute(request.toString()))
        assertTrue(result.toString(), result.getBoolean("ok"))
        return result.get("data")
    }
    @Test fun dngPresetImportApplyCropAndPersistence() {
        val folder = File(context.cacheDir, "native-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            call(JSONObject().put("method", "init").put("folder", folder.absolutePath))
            val input = File(folder, "source.jpg")
            val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) bitmap.setPixel(x, y, Color.rgb(40+x, 40+y, 80))
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
            call(JSONObject().put("method", "open").put("path", input.absolutePath))
            // Synthetic mobile DNG-preset payload: the extractor deliberately reads the
            // embedded packet independently of the RAW pixel container.
            val preset = File(folder, "Warm test.dng")
            preset.writeText("""II synthetic container <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/" crs:Exposure2012="1.0" crs:Saturation="30" crs:CameraProfile="Unsupported test profile" crs:CropLeft="0.2" crs:Temperature="4000" /></rdf:RDF></x:xmpmeta>""")
            val imported = call(JSONObject().put("method", "importPresets").put("path", preset.absolutePath)) as JSONObject
            val report = imported.getJSONObject("report").getJSONArray("imported")
            assertEquals(1, report.length())
            val id = report.getJSONObject(0).getString("id")
            assertTrue(report.getJSONObject(0).getJSONArray("unmapped").toString().contains("CameraProfile"))
            fun render(name: String, edit: EditState): Bitmap {
                val output = File(folder, name)
                call(JSONObject().put("method", "render").put("path", output.absolutePath).put("edge", 120).put("edit", edit.json()))
                return BitmapFactory.decodeFile(output.absolutePath)
            }
            val original = render("original.jpg", EditState())
            val edited = render("edited.jpg", EditState(preset = id))
            assertEquals(120, edited.width)
            assertEquals(80, edited.height) // The donor photo's crop is not copied.
            assertTrue(Color.red(edited.getPixel(60,40)) > Color.red(original.getPixel(60,40)))
            val zero = render("zero.jpg", EditState(preset = id, amount = 0f))
            assertTrue(original.sameAs(zero))
            val manualOutput = File(folder, "manual.jpg")
            val manual = call(JSONObject().put("method", "render").put("path", manualOutput.absolutePath).put("edge", 120)
                .put("edit", EditState(preset = id, controls = mapOf("light.exposure" to 0.3f)).json())) as JSONObject
            assertEquals(0.3, manual.getJSONObject("controls").getDouble("light.exposure"), 0.0001)
            val crop = render("crop.jpg", EditState(preset = id, crop = CropRect(0.25f,0f,0.75f,1f)))
            assertTrue(crop.width < crop.height)
            val rotated = render("rotate.jpg", EditState(rotation = 1))
            assertEquals(80, rotated.width); assertEquals(120, rotated.height)
            val persisted = call(JSONObject().put("method", "init").put("folder", folder.absolutePath)) as JSONArray
            assertTrue((0 until persisted.length()).any { persisted.getJSONObject(it).getString("id") == id })
            val failed = JSONObject(NativeEngine.execute(JSONObject().put("method", "open").put("path", File(folder,"missing.jpg").absolutePath).toString()))
            assertFalse(failed.getBoolean("ok"))
        } finally { folder.deleteRecursively() }
    }
}
