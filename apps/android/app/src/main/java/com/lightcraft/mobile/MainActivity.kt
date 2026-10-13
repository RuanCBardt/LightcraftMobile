package com.lightcraft.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlin.math.min

private val Ink = Color(0xFF101110)
private val Panel = Color(0xFF1B1D1A)
private val Muted = Color(0xFF969B90)
private val Paper = Color(0xFFF2F2E8)
private val Lime = Color(0xFFD7FC70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Ink, surface = Panel, onBackground = Paper, onSurface = Paper)) {
                val model: EditorViewModel = viewModel()
                Editor(model)
            }
        }
    }
}

@Composable
fun Editor(model: EditorViewModel) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(model::importPhoto) }
    val presetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) model.importPresets(it) }
    val photoDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importPhoto) }
    var importMenu by remember { mutableStateOf(false) }
    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long); model.dismissMessage() }
    }
    val locked = ui.loading || ui.exporting || !ui.ready
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.hasPhoto) Row(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Presets" to "◫", "Ajustes" to "☷", "Recortar" to "⌗").forEach { (tab, symbol) ->
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !locked) { model.selectTab(tab) }.padding(horizontal = 24.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(symbol, color = if (ui.tab == tab) Lime else Muted, fontSize = 22.sp)
                        Text(tab, color = if (ui.tab == tab) Paper else Muted, fontSize = 11.sp)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("lightcraft", fontSize = 25.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.SemiBold)
                    Text("MOBILE STUDIO", color = Muted, fontSize = 8.sp, letterSpacing = 3.sp)
                }
                Box {
                    TextButton(onClick = { importMenu = true }, enabled = !locked) { Text("＋ Foto", color = Paper) }
                    DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                        DropdownMenuItem(text = { Text("Escolher da galeria") }, onClick = { importMenu = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        DropdownMenuItem(text = { Text("Abrir arquivo / DNG") }, onClick = { importMenu = false; photoDocument.launch(arrayOf("image/*", "application/octet-stream")) })
                    }
                }
                if (ui.hasPhoto) Button(onClick = { model.exportPhoto() }, enabled = !locked, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(if (ui.exporting) "Salvando…" else "Salvar ↗", color = Ink, fontSize = 12.sp)
                }
            }
            if (!ui.hasPhoto) {
                EmptyStudio(Modifier.weight(1f), !locked, onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onPresets = { presetPicker.launch(arrayOf("*/*")) })
            } else {
                Row(Modifier.padding(horizontal = 22.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.photoName, Modifier.weight(1f), color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { model.undo() }, enabled = ui.canUndo && !locked) { Text("↶ Desfazer", fontSize = 11.sp) }
                    TextButton(onClick = { model.reset() }, enabled = !locked) { Text("Redefinir", color = Muted, fontSize = 11.sp) }
                }
                PhotoCanvas(ui, model, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (ui.tab == "Recortar") "ARRASTE O QUADRO OU OS CANTOS" else "SEGURE A FOTO PARA VER O ORIGINAL", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                    Text(if (ui.rendering) "●" else "", color = Lime, fontSize = 10.sp)
                }
                Box(Modifier.heightIn(min = 200.dp, max = 280.dp).fillMaxWidth()) {
                    when (ui.tab) {
                        "Presets" -> PresetPanel(ui, model, !locked) { presetPicker.launch(arrayOf("*/*")) }
                        "Ajustes" -> AdjustPanel(ui, model, !locked)
                        "Recortar" -> CropPanel(ui, model, !locked)
                    }
                }
            }
            if (ui.loading || !ui.ready) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime)
        }
    }
}

@Composable
private fun EmptyStudio(modifier: Modifier, enabled: Boolean, onPhoto: () -> Unit, onPresets: () -> Unit) {
    Column(modifier.fillMaxWidth().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("01 / SEU ESTÚDIO DE BOLSO", color = Lime, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
        Text("Um novo olhar.\nA cada foto.", fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Canvas(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) {
            drawCircle(Color(0xFF737C56), size.minDimension * 0.65f, Offset(size.width * 0.73f, size.height * 1.1f))
            drawCircle(Lime, size.minDimension * 0.2f, Offset(size.width * 0.7f, size.height * 0.32f))
            drawLine(Paper.copy(alpha = 0.3f), Offset(size.width * 0.13f, size.height * 0.18f), Offset(size.width * 0.13f, size.height * 0.82f), 1f)
            drawRect(Paper.copy(alpha = 0.4f), Offset(size.width * 0.34f, size.height * 0.14f), Size(size.width * 0.4f, size.height * 0.72f), style = Stroke(1f))
        }
        Spacer(Modifier.height(24.dp))
        Text("Seus presets. Suas cores.\nImporte uma foto e encontre seu estilo.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPhoto, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("importPhoto"), shape = RoundedCornerShape(12.dp)) { Text("＋  Importar foto", color = Ink) }
        TextButton(onClick = onPresets, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Importar presets DNG / XMP", color = Paper, fontSize = 12.sp) }
        Text("EDIÇÃO LOCAL · ORIGINAL PRESERVADO", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp))
    }
}

@Composable
private fun PhotoCanvas(ui: EditorUi, model: EditorViewModel, modifier: Modifier) {
    var before by remember { mutableStateOf(false) }
    val bitmap = if (before) ui.original else ui.preview
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height
            val availableRatio = maxWidth / maxHeight
            val imageModifier = if (ratio > availableRatio) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxHeight().aspectRatio(ratio)
            Box(imageModifier.clip(RoundedCornerShape(2.dp))) {
                Image(bitmap.asImageBitmap(), "Foto em edição", Modifier.fillMaxSize().pointerInput(ui.tab) {
                    if (ui.tab != "Recortar") detectTapGestures(onPress = { before = true; try { awaitRelease() } finally { before = false } })
                }, contentScale = ContentScale.Fit)
                if (ui.tab == "Recortar" && !ui.loading && !ui.exporting) CropOverlay(ui.edit.crop, onStart = model::rememberGesture, onChange = { model.change(ui.edit.copy(crop = it), false) })
                if (before) Text("ORIGINAL", color = Ink, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Lime, RoundedCornerShape(3.dp)).padding(6.dp))
            }
        } else CircularProgressIndicator(color = Lime)
    }
}

@Composable
private fun PresetPanel(ui: EditorUi, model: EditorViewModel, enabled: Boolean, onImport: () -> Unit) {
    Column {
        val groups = listOf("Todos", "Favoritos") + ui.presets.map { it.group }.distinct()
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            items(groups) { group -> Text(group, color = if (group == ui.group) Lime else Muted, fontSize = 11.sp, modifier = Modifier.clickable(enabled) { model.selectGroup(group) }.padding(vertical = 10.dp)) }
            item { Text("＋ Importar", color = Paper, fontSize = 11.sp, modifier = Modifier.clickable(enabled, onClick = onImport).padding(vertical = 10.dp)) }
        }
        val filtered = ui.presets.filter { ui.group == "Todos" || (ui.group == "Favoritos" && it.id in ui.favorites) || it.group == ui.group }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                PresetCard("Original", "00", ui.original, ui.edit.preset == null, false, enabled, { model.selectPreset(null) }, {})
            }
            items(filtered, key = { it.id }) { preset ->
                val index = ui.presets.indexOf(preset) + 1
                PresetCard(preset.name, "%02d".format(index), ui.thumbnails[preset.id], ui.edit.preset == preset.id, preset.id in ui.favorites, enabled, { model.selectPreset(preset.id) }, { model.toggleFavorite(preset.id) })
            }
        }
        if (ui.edit.preset != null) {
            EditorSlider("Intensidade", ui.edit.amount, 0f..200f, enabled, model::rememberGesture, { model.change(ui.edit.copy(amount = it), false) }, modifier = Modifier.padding(horizontal = 20.dp), suffix = "%")
        } else Text("Escolha um preset para começar", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(name: String, code: String, bitmap: android.graphics.Bitmap?, selected: Boolean, favorite: Boolean, enabled: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Column(Modifier.width(82.dp).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onFavorite)) {
        Box(Modifier.size(82.dp).clip(RoundedCornerShape(7.dp)).background(Panel).border(if (selected) 2.dp else 0.dp, if (selected) Lime else Color.Transparent, RoundedCornerShape(7.dp))) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Prévia: $name", Modifier.fillMaxSize().padding(if (selected) 4.dp else 0.dp), contentScale = ContentScale.Crop)
            else Text(code, color = Muted, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
            Text(code, color = Paper, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Ink.copy(alpha = 0.65f), RoundedCornerShape(2.dp)).padding(3.dp))
            if (favorite) Text("★", color = Lime, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Text(name, color = if (selected) Lime else Paper, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
    }
}

private data class Adjustment(val id: String, val label: String, val range: ClosedFloatingPointRange<Float>)
private val adjustments = listOf(
    Adjustment("light.exposure", "Exposição", -5f..5f), Adjustment("light.contrast", "Contraste", -100f..100f),
    Adjustment("light.highlights", "Realces", -100f..100f), Adjustment("light.shadows", "Sombras", -100f..100f),
    Adjustment("light.whites", "Brancos", -100f..100f), Adjustment("light.blacks", "Pretos", -100f..100f),
    Adjustment("color.saturation", "Saturação", -100f..100f), Adjustment("color.vibrance", "Vibração", -100f..100f),
    Adjustment("wb.temp", "Temperatura", 2000f..12000f), Adjustment("wb.tint", "Matiz", -100f..100f),
    Adjustment("effects.clarity", "Clareza", -100f..100f), Adjustment("effects.texture", "Textura", -100f..100f),
    Adjustment("effects.dehaze", "Desembaçar", -100f..100f), Adjustment("vignette.amount", "Vinheta", -100f..100f),
    Adjustment("grain.amount", "Granulação", 0f..100f),
)

@Composable
private fun AdjustPanel(ui: EditorUi, model: EditorViewModel, enabled: Boolean) {
    var selected by remember { mutableStateOf(adjustments.first()) }
    Column(Modifier.padding(top = 14.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(adjustments) { adjustment ->
                FilterChip(selected = selected == adjustment, onClick = { selected = adjustment }, enabled = enabled, label = { Text(adjustment.label, fontSize = 11.sp) })
            }
        }
        val value = ui.edit.controls[selected.id] ?: ui.effectiveControls[selected.id] ?: if (selected.id == "wb.temp") 6500f else 0f
        Spacer(Modifier.height(20.dp))
        EditorSlider(selected.label, value, selected.range, enabled, model::rememberGesture, { model.change(ui.edit.copy(controls = ui.edit.controls + (selected.id to it)), false) }, Modifier.padding(horizontal = 24.dp), precision = if (selected.id == "light.exposure") 2 else 0)
        TextButton(onClick = { model.change(ui.edit.copy(controls = ui.edit.controls - selected.id)) }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Restaurar ajuste do preset", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onStart: () -> Unit, onValue: (Float) -> Unit, modifier: Modifier = Modifier, suffix: String = "", precision: Int = 0) {
    var changing by remember { mutableStateOf(false) }
    Column(modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Paper, fontSize = 12.sp)
            Text(("%.${precision}f".format(value)) + suffix, color = Lime, fontSize = 12.sp)
        }
        Slider(value.coerceIn(range.start, range.endInclusive), onValueChange = {
            if (!changing) { onStart(); changing = true }
            onValue(it)
        }, onValueChangeFinished = { changing = false }, valueRange = range, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CropPanel(ui: EditorUi, model: EditorViewModel, enabled: Boolean) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text("ENQUADRAMENTO", fontSize = 9.sp, color = Muted, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Livre" to 0f, "1:1" to 1f, "4:5" to 0.8f, "3:2" to 1.5f, "16:9" to (16f/9))) { (label, ratio) ->
                OutlinedButton(enabled = enabled, onClick = {
                    val image = ui.preview ?: return@OutlinedButton
                    val aspect = image.width.toFloat() / image.height
                    val w = if (ratio == 0f) 1f else min(1f, ratio/aspect)
                    val h = if (ratio == 0f) 1f else min(1f, aspect/ratio)
                    model.change(ui.edit.copy(crop = CropRect((1-w)/2, (1-h)/2, (1+w)/2, (1+h)/2)))
                }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Text(label, color = Paper, fontSize = 11.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = enabled, onClick = { model.change(ui.edit.copy(rotation = (ui.edit.rotation + 1) % 4, crop = CropRect())) }) { Text("↻  Girar 90°", color = Paper) }
            Button(enabled = enabled, onClick = { model.selectTab("Presets") }, shape = RoundedCornerShape(10.dp)) { Text("Aplicar recorte", color = Ink) }
        }
    }
}

@Composable
private fun CropOverlay(crop: CropRect, onStart: () -> Unit, onChange: (CropRect) -> Unit) {
    val latestCrop by rememberUpdatedState(crop)
    val latestOnChange by rememberUpdatedState(onChange)
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        var corner = -1
        var dragging = CropRect()
        detectDragGestures(
            onDragStart = { position ->
                onStart(); dragging = latestCrop
                val points = listOf(Offset(dragging.left, dragging.top), Offset(dragging.right, dragging.top), Offset(dragging.right, dragging.bottom), Offset(dragging.left, dragging.bottom))
                corner = points.indexOfFirst { abs(it.x*size.width-position.x) < 44.dp.toPx() && abs(it.y*size.height-position.y) < 44.dp.toPx() }
            },
            onDrag = { change, delta ->
                change.consume()
                val dx = delta.x / size.width; val dy = delta.y / size.height
                dragging = when (corner) {
                    0 -> dragging.copy(left = (dragging.left+dx).coerceIn(0f, dragging.right-0.08f), top = (dragging.top+dy).coerceIn(0f, dragging.bottom-0.08f))
                    1 -> dragging.copy(right = (dragging.right+dx).coerceIn(dragging.left+0.08f, 1f), top = (dragging.top+dy).coerceIn(0f, dragging.bottom-0.08f))
                    2 -> dragging.copy(right = (dragging.right+dx).coerceIn(dragging.left+0.08f, 1f), bottom = (dragging.bottom+dy).coerceIn(dragging.top+0.08f, 1f))
                    3 -> dragging.copy(left = (dragging.left+dx).coerceIn(0f, dragging.right-0.08f), bottom = (dragging.bottom+dy).coerceIn(dragging.top+0.08f, 1f))
                    else -> {
                        val x = dx.coerceIn(-dragging.left, 1f-dragging.right); val y = dy.coerceIn(-dragging.top, 1f-dragging.bottom)
                        dragging.copy(left = dragging.left+x, right = dragging.right+x, top = dragging.top+y, bottom = dragging.bottom+y)
                    }
                }
                latestOnChange(dragging)
            },
        )
    }) {
        val x0 = crop.left*size.width; val y0 = crop.top*size.height
        val x1 = crop.right*size.width; val y1 = crop.bottom*size.height
        val shade = Ink.copy(alpha = 0.65f)
        drawRect(shade, size = Size(size.width, y0))
        drawRect(shade, Offset(0f,y1), Size(size.width,size.height-y1))
        drawRect(shade, Offset(0f,y0), Size(x0,y1-y0))
        drawRect(shade, Offset(x1,y0), Size(size.width-x1,y1-y0))
        drawRect(Paper, Offset(x0,y0), Size(x1-x0,y1-y0), style = Stroke(1.dp.toPx()))
        for (i in 1..2) {
            val x = x0+(x1-x0)*i/3; val y = y0+(y1-y0)*i/3
            drawLine(Paper.copy(alpha = 0.45f), Offset(x,y0), Offset(x,y1), 1f)
            drawLine(Paper.copy(alpha = 0.45f), Offset(x0,y), Offset(x1,y), 1f)
        }
        listOf(Offset(x0,y0),Offset(x1,y0),Offset(x1,y1),Offset(x0,y1)).forEach { drawCircle(Lime, 4.dp.toPx(), it) }
    }
}
