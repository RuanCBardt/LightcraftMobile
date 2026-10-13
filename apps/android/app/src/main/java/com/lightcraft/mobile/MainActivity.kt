package com.lightcraft.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val Ink = Color.Black
private val Panel = Color(0xFF242424)
private val Muted = Color(0xFF777777)
private val Paper = Color(0xFFF8F8F8)
private val Lime = Color(0xFFE1E365)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Paper, secondary = Paper, primaryContainer = Panel, secondaryContainer = Panel, onPrimary = Ink, background = Ink, surface = Panel, onBackground = Paper, onSurface = Paper)) {
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
    var moreMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = ui.editingAmount) { model.finishAmount(false) }
    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Long); model.dismissMessage() }
    }
    val locked = ui.loading || ui.exporting || !ui.ready
    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.hasPhoto && ui.editingAmount) {
                AmountActions(ui, model, !locked)
            } else if (ui.hasPhoto) {
                Row(Modifier.fillMaxWidth().background(Ink).navigationBarsPadding().padding(top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Presets" to "Predefinições", "Ajustes" to "Ferramentas", "Recortar" to "Recortar", "Undo" to "Desfazer").forEach { (tab, label) ->
                        val active = ui.tab == tab
                        val enabled = !locked && (tab != "Undo" || ui.canUndo)
                        Column(Modifier.weight(1f).clickable(enabled = enabled) { if (tab == "Undo") model.undo() else model.selectTab(tab) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            StudioIcon(tab, if (active) Paper else Muted.copy(alpha = if (enabled) 1f else 0.4f), Modifier.size(24.dp))
                            Text(label, color = if (active) Paper else Muted, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!ui.editingAmount) {
                Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        IconButton(onClick = { importMenu = true }, enabled = !locked, modifier = Modifier.semantics { contentDescription = "Abrir foto" }) { StudioIcon("Back", Paper, Modifier.size(23.dp)) }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(text = { Text("Escolher da galeria") }, onClick = { importMenu = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            DropdownMenuItem(text = { Text("Abrir arquivo / DNG") }, onClick = { importMenu = false; photoDocument.launch(arrayOf("image/*", "application/octet-stream")) })
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }, enabled = !locked, modifier = Modifier.semantics { contentDescription = "Opções do editor" }) { StudioIcon("Wand", Paper, Modifier.size(26.dp)) }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Importar presets DNG / XMP") }, onClick = { moreMenu = false; presetPicker.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text("Redefinir edição") }, enabled = ui.hasPhoto, onClick = { moreMenu = false; model.reset() })
                        }
                    }
                    if (ui.hasPhoto) TextButton(onClick = { model.exportPhoto() }, enabled = !locked, modifier = Modifier.align(Alignment.CenterEnd).testTag("exportPhoto")) {
                        Text(if (ui.exporting) "Salvando…" else "Próximo", color = Paper, fontSize = 14.sp)
                    }
                }
            } else Spacer(Modifier.height(36.dp))
            if (!ui.hasPhoto) {
                EmptyStudio(Modifier.weight(1f), !locked, onPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onPresets = { presetPicker.launch(arrayOf("*/*")) })
            } else {
                PhotoCanvas(ui, model, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 9.dp, vertical = 12.dp))
                if (ui.editingAmount) {
                    AmountPanel(ui, model, !locked)
                } else Box(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
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

/** Original line icons, drawn locally rather than copied from another editor. */
@Composable
private fun StudioIcon(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x*unit,y*unit), Offset(x2*unit,y2*unit), 1.6f*unit)
        when (kind) {
            "Back" -> { line(15f,3f,6f,12f); line(6f,12f,15f,21f) }
            "Close" -> { line(5f,5f,19f,19f); line(5f,19f,19f,5f) }
            "Check" -> { line(3f,12f,9f,18f); line(9f,18f,21f,5f) }
            "Presets" -> drawRect(color, Offset(2f*unit,2f*unit), Size(20f*unit,20f*unit), style = Stroke(1.7f*unit))
            "Ajustes" -> for ((y,x) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                line(2f,y,22f,y); drawCircle(Ink, 2.6f*unit, Offset(x*unit,y*unit)); drawCircle(color, 2.6f*unit, Offset(x*unit,y*unit), style = Stroke(1.5f*unit))
            }
            "Recortar" -> { line(6f,1f,6f,18f); line(6f,18f,23f,18f); line(1f,6f,18f,6f); line(18f,6f,18f,23f) }
            "Undo" -> { drawArc(color, -160f, 290f, false, Offset(4f*unit,4f*unit), Size(17f*unit,17f*unit), style = Stroke(1.7f*unit)); line(1f,8f,6f,10f); line(6f,10f,8f,5f) }
            else -> { line(4f,21f,17f,8f); line(3f,8f,7f,8f); line(5f,6f,5f,10f); line(18f,1f,18f,5f); line(16f,3f,20f,3f); line(20f,12f,23f,15f); line(10f,2f,12f,4f) }
        }
    }
}

@Composable
private fun AmountActions(ui: EditorUi, model: EditorViewModel, enabled: Boolean) {
    val name = ui.presets.find { it.id == ui.edit.preset }?.name.orEmpty()
    Row(Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { model.finishAmount(false) }, enabled = enabled, modifier = Modifier.testTag("cancelAmount").semantics { contentDescription = "Cancelar intensidade" }) { StudioIcon("Close", Paper, Modifier.size(24.dp)) }
        Text("● $name", color = Paper, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 230.dp).background(Panel, CircleShape).padding(horizontal = 18.dp, vertical = 12.dp))
        IconButton(onClick = { model.finishAmount(true) }, enabled = enabled, modifier = Modifier.testTag("confirmAmount").semantics { contentDescription = "Confirmar intensidade" }) { StudioIcon("Check", Paper, Modifier.size(24.dp)) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AmountPanel(ui: EditorUi, model: EditorViewModel, enabled: Boolean) {
    Column(Modifier.fillMaxWidth().height(124.dp).padding(horizontal = 26.dp), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Intensidade", color = Muted, fontSize = 11.sp)
            Text("${ui.edit.amount.toInt()}%", color = Paper, fontSize = 14.sp)
        }
        Slider(ui.edit.amount, onValueChange = model::previewAmount, valueRange = 0f..200f, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Paper, activeTrackColor = Paper, inactiveTrackColor = Muted),
            thumb = { Box(Modifier.size(24.dp).background(Paper, CircleShape)) },
            track = { Canvas(Modifier.fillMaxWidth().height(2.dp)) { drawLine(Muted, Offset(0f, size.height/2), Offset(size.width, size.height/2), 2.dp.toPx()) } },
            modifier = Modifier.fillMaxWidth().testTag("presetAmount"))
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
    val gesturesEnabled = ui.tab != "Recortar" && !ui.loading && !ui.exporting
    val swipeEnabled = gesturesEnabled && !ui.editingAmount
    val bitmap = if (before) ui.original else ui.preview
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            val ratio = bitmap.width.toFloat() / bitmap.height
            val availableRatio = maxWidth / maxHeight
            val imageModifier = if (ratio > availableRatio) Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxHeight().aspectRatio(ratio)
            Box(imageModifier) {
                Image(bitmap.asImageBitmap(), "Foto em edição", Modifier.fillMaxSize().testTag("photoCanvas")
                    .pointerInput(gesturesEnabled) {
                        if (gesturesEnabled) detectTapGestures(onLongPress = { before = true }, onPress = {
                            try { awaitRelease() } finally { before = false }
                        })
                    }
                    .pointerInput(swipeEnabled) {
                        var distance = 0f
                        if (swipeEnabled) detectHorizontalDragGestures(
                            onDragStart = { distance = 0f; before = false },
                            onDragEnd = {
                                if (abs(distance) >= 40.dp.toPx()) model.stepPreset(if (distance < 0f) 1 else -1)
                                distance = 0f
                            },
                            onDragCancel = { distance = 0f },
                            onHorizontalDrag = { change, delta -> change.consume(); distance += delta },
                        )
                    }, contentScale = ContentScale.Fit)
                if (ui.tab == "Recortar" && !ui.loading && !ui.exporting) CropOverlay(ui.edit.crop, onStart = model::rememberGesture, onChange = { model.change(ui.edit.copy(crop = it), false) })
                if (before) Text("ORIGINAL", color = Ink, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Paper, RoundedCornerShape(3.dp)).padding(6.dp))
                if (ui.rendering) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(10.dp).size(12.dp), color = Paper, strokeWidth = 1.dp)
            }
        } else CircularProgressIndicator(color = Lime)
    }
}

@Composable
private fun PresetPanel(ui: EditorUi, model: EditorViewModel, enabled: Boolean, onImport: () -> Unit) {
    val strip = rememberLazyListState()
    val filtered = ui.visiblePresets()
    val selected = filtered.indexOfFirst { it.id == ui.edit.preset } + 1
    LaunchedEffect(ui.edit.preset, ui.group, filtered.map { it.id }) {
        if (selected >= 0 && (ui.edit.preset == null || selected > 0)) {
            val viewport = strip.layoutInfo.viewportSize.width
            val tile = strip.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            strip.animateScrollToItem(selected, -((viewport - tile) / 2).coerceAtLeast(0))
        }
    }
    Column(Modifier.padding(top = 10.dp)) {
        val groups = listOf("Todos", "Favoritos") + ui.presets.map { it.group }.distinct()
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = ui.presets.find { it.id == ui.edit.preset }?.name ?: "Original"
            Text(name, color = Lime, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 92.dp).clickable(enabled && ui.edit.preset != null) { model.beginAmount() }.padding(horizontal = 14.dp, vertical = 12.dp))
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                items(groups) { group -> Text(if (group == "Todos") "Todas as predefinições" else group, color = if (group == ui.group) Paper else Muted, fontSize = 11.sp, modifier = Modifier.clickable(enabled) { model.selectGroup(group) }.padding(vertical = 12.dp)) }
            }
            Text("＋", color = Paper, fontSize = 22.sp, modifier = Modifier.clickable(enabled, onClick = onImport).padding(horizontal = 12.dp, vertical = 8.dp).semantics { contentDescription = "Importar presets" })
        }
        LazyRow(state = strip, modifier = Modifier.fillMaxWidth().testTag("presetStrip"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                PresetCard("Original", "original", 0, ui.original, ui.edit.preset == null, false, enabled, { model.selectPreset(null) }, {})
            }
            items(filtered, key = { it.id }) { preset ->
                val index = ui.presets.indexOf(preset) + 1
                PresetCard(preset.name, preset.id, index, ui.thumbnails[preset.id], ui.edit.preset == preset.id, preset.id in ui.favorites, enabled,
                    { if (ui.edit.preset == preset.id) model.beginAmount() else model.selectPreset(preset.id) }, { model.toggleFavorite(preset.id) })
            }
        }
        if (ui.group == "Favoritos" && filtered.isEmpty()) Text("Segure um preset para adicioná-lo aos favoritos", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(name: String, id: String, index: Int, bitmap: android.graphics.Bitmap?, selected: Boolean, favorite: Boolean, enabled: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val colors = listOf(Color(0xFF777467), Color(0xFF967C6A), Color(0xFF83865B), Color(0xFF777790), Color(0xFF507A72))
    Column(Modifier.width(68.dp).testTag("preset:$id").combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onFavorite).semantics { contentDescription = if (selected && id != "original") "Ajustar intensidade de $name" else "Aplicar $name" }) {
        Box(Modifier.fillMaxWidth().height(62.dp).background(Panel)) {
            if (selected && id != "original") StudioIcon("Ajustes", Paper, Modifier.size(23.dp).align(Alignment.Center))
            else if (bitmap != null) Image(bitmap.asImageBitmap(), "Prévia: $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(if (index == 0) "—" else "%02d".format(index), color = Muted, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
            if (favorite) Text("★", color = Paper, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp))
        }
        Box(Modifier.fillMaxWidth().height(23.dp).background(if (selected) Panel else colors[index % colors.size]), contentAlignment = Alignment.Center) {
            Text(name, color = Paper, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp))
        }
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
