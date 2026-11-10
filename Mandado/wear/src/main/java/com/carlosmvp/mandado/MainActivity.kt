package com.carlosmvp.mandado

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private val MandadoWatchColors = Colors(
    primary = Color(0xFF9BD8BE), primaryVariant = Color(0xFF286B57),
    secondary = Color(0xFFF0B58F), secondaryVariant = Color(0xFFAA6038),
    error = Color(0xFFFFB4AB), onPrimary = Color(0xFF12382D), onSecondary = Color(0xFF492612),
    onBackground = Color(0xFFE1E9E2), onSurface = Color(0xFFE1E9E2), onSurfaceVariant = Color(0xFFC0CDC4), onError = Color(0xFF690005),
    surface = Color(0xFF19221D), background = Color(0xFF111914)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); Store.init(this); setContent { MaterialTheme(colors = MandadoWatchColors) { WatchApp() } } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Tile(text: String, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D2B24))
        .combinedClickable(onClick = onClick, onLongClick = {
            onLong?.invoke() ?: Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        }).animateContentSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(text, style = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    touchSize: Dp = 44.dp,
    iconSize: Dp = 25.dp,
    tint: Color = MaterialTheme.colors.primary
) {
    val ctx = LocalContext.current
    Box(modifier.size(touchSize).combinedClickable(onClick = onClick, onLongClick = {
        Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show()
    }), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
fun ActivityResult.spoken() = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

@Composable
private fun rememberWatchPhoto(cacheKey: String, path: String, source: String, cache: MutableMap<String, ImageBitmap>): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, cacheKey, path, source) {
        // produceState keeps its State object when keys change. Always replace
        // the old bitmap with the bitmap for the new key; only initializing
        // while value == null left stale photos visible until the screen reopened.
        value = cache[cacheKey] ?: withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val local = path.takeIf { it.isNotBlank() }?.let { filePath -> BitmapFactory.decodeFile(filePath, options) }
                val bitmap = local ?: source.takeIf { it.startsWith("http") }?.let { remote ->
                    val connection = URL(remote).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                    connection.getInputStream().use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
    LaunchedEffect(cacheKey, image) { image?.let { cache[cacheKey] = it } }
    return image
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchListTile(
    list: ShopList,
    mode: Int,
    onClick: () -> Unit,
    onOptions: () -> Unit,
    imageCache: MutableMap<String, ImageBitmap>,
    modifier: Modifier = Modifier
) {
    val image = rememberWatchPhoto("list:${list.id}:${list.storePhoto}:${list.storePhotoSource}:${list.storePhotoUpdatedAt}", list.storePhoto, list.storePhotoSource, imageCache)
    val height = when (mode) { 1 -> 112.dp; 2 -> 108.dp; else -> 76.dp }
    Box(modifier.fillMaxWidth().height(height).padding(3.dp).clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colors.surface).combinedClickable(onClick = onClick, onLongClick = onOptions)) {
        if (image != null) Image(image, contentDescription = "Foto de ${list.store}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (image == null) 0f else 0.58f)))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(list.name, style = MaterialTheme.typography.title3, color = if (image != null) Color.White else MaterialTheme.colors.onSurface,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (mode != 0) Text(list.store.ifBlank { "Sin tienda" }, style = MaterialTheme.typography.caption2,
                color = if (image != null) Color.White else MaterialTheme.colors.onSurfaceVariant)
            Text("${list.items.size} objetos", style = MaterialTheme.typography.caption2,
                color = if (image != null) Color.White else MaterialTheme.colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchItemTile(
    item: Item,
    mode: Int,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggleCheck: () -> Unit,
    onEditName: () -> Unit,
    onEditPrice: () -> Unit,
    onOptions: () -> Unit,
    imageCache: MutableMap<String, ImageBitmap>,
    animateRemoval: Boolean,
    modifier: Modifier = Modifier
) {
    val image = rememberWatchPhoto("item:${item.id}:${item.photo}:${item.photoSource}:${item.photoUpdatedAt}", item.photo, item.photoSource, imageCache)
    val checkScale by animateFloatAsState(
        targetValue = if (item.checked) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 360f),
        label = "watch-check-scale"
    )
    val checkTint by animateColorAsState(
        targetValue = if (item.checked) MaterialTheme.colors.primary else if (image != null) Color.White else MaterialTheme.colors.onSurfaceVariant,
        animationSpec = tween(260),
        label = "watch-check-color"
    )
    val fadeAlpha by animateFloatAsState(
        targetValue = if (animateRemoval && item.checked) 0f else 1f,
        animationSpec = tween(520),
        label = "watch-checked-fade"
    )
    val cardHeight = when (mode) {
        1 -> if (expanded) 142.dp else 118.dp
        2 -> if (expanded) 124.dp else 100.dp
        else -> if (expanded) 104.dp else 78.dp
    }
    Box(modifier.fillMaxWidth().height(cardHeight).padding(3.dp).graphicsLayer { alpha = fadeAlpha }.clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colors.surface).combinedClickable(onClick = onExpand, onLongClick = onOptions)) {
        if (image != null) Image(image, contentDescription = "Foto de ${item.name}", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (image == null) 0f else 0.60f)))
        val checkIcon = if (item.checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
        val checkLabel = if (item.checked) "Marcar como pendiente" else "Marcar como comprado"
        if (mode == 1) {
            Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Text(item.name, modifier = Modifier.fillMaxWidth().clickable { if (expanded) onEditName() else onExpand() },
                    style = MaterialTheme.typography.title3, color = if (image != null) Color.White else MaterialTheme.colors.onSurface,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (expanded) {
                    Text("$${"%.2f".format(item.price)}", modifier = Modifier.clickable(onClick = onEditPrice),
                        style = MaterialTheme.typography.body2, color = if (image != null) Color.White else MaterialTheme.colors.primary)
                    Text("Toca para editar", style = MaterialTheme.typography.caption2,
                        color = if (image != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colors.onSurfaceVariant)
                }
                WatchActionIcon(checkIcon, checkLabel, onToggleCheck,
                    modifier = Modifier.graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                    touchSize = 48.dp, iconSize = 42.dp, tint = checkTint)
            }
        } else {
            Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(if (mode == 2) 0.64f else 0.68f)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(item.name, modifier = Modifier.clickable { if (expanded) onEditName() else onExpand() },
                    style = MaterialTheme.typography.title3, color = if (image != null) Color.White else MaterialTheme.colors.onSurface,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (expanded) {
                    Text("$${"%.2f".format(item.price)}", modifier = Modifier.clickable(onClick = onEditPrice).padding(top = 4.dp),
                        style = MaterialTheme.typography.body2, color = if (image != null) Color.White else MaterialTheme.colors.primary)
                    Text("Pulsa nombre o precio para editar", style = MaterialTheme.typography.caption2,
                        color = if (image != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colors.onSurfaceVariant)
                }
            }
            WatchActionIcon(checkIcon, checkLabel, onToggleCheck,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale },
                touchSize = 54.dp, iconSize = 46.dp, tint = checkTint)
        }
    }
}

@Composable fun WatchApp() {
    val ctx = LocalContext.current
    val lists by Store.lists.collectAsState()
    val phoneConnected by Store.watchPhoneConnected.collectAsState()
    val watchSyncReady by Store.watchSyncReady.collectAsState()
    val blockedEdits by Store.blockedWatchEdits.collectAsState()
    val canEditLists = phoneConnected && watchSyncReady
    LaunchedEffect(Unit) {
        while (true) {
            Store.refreshWatchConnection()
            delay(2500)
        }
    }
    LaunchedEffect(blockedEdits) {
        if (blockedEdits > 0) Toast.makeText(ctx, "Conecta tu teléfono y espera a que se sincronicen las listas para hacer cambios", Toast.LENGTH_LONG).show()
    }
    fun requirePhoneForChanges(action: () -> Unit) {
        if (canEditLists) action()
        else Toast.makeText(ctx, if (phoneConnected) "Sincronizando las listas del teléfono…" else "Conecta tu teléfono para hacer cambios", Toast.LENGTH_SHORT).show()
    }
    var sel by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var showAmount by remember(sel) { mutableStateOf(false) }
    var showVoiceMode by remember { mutableStateOf(false) }
    var listeningMulti by remember { mutableStateOf(false) }
    var multiFallbackMode by remember { mutableStateOf(false) }
    var voiceAudioLevel by remember { mutableStateOf(-2f) }
    var recognizedVoiceItems by remember { mutableStateOf<List<String>>(emptyList()) }
    val listsViewMode by Store.mainListsViewMode.collectAsState()
    var itemViewMode by remember(sel, lists.find { it.id == sel }?.viewMode) {
        mutableIntStateOf(lists.find { it.id == sel }?.viewMode?.coerceIn(0, 2) ?: 0)
    }
    var showCheckedItems by remember(sel) { mutableStateOf(false) }
    var showCompletionPrompt by remember(sel) { mutableStateOf(false) }
    var expandedItemIds by remember(sel) { mutableStateOf<Set<String>>(emptySet()) }
    var fadingCheckedIds by remember(sel) { mutableStateOf<Set<String>>(emptySet()) }
    val imageCache = remember(sel) { mutableStateMapOf<String, ImageBitmap>() }
    val scope = rememberCoroutineScope()
    var priceTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var priceDraft by remember { mutableStateOf("") }
    var addingItemText by remember { mutableStateOf(false) }
    var itemNameDraft by remember { mutableStateOf("") }
    var itemPriceDraft by remember { mutableStateOf("0") }
    var textEditTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var multiSession by remember { mutableStateOf<MultiItemSpeechSession?>(null) }
    val l = lists.find { it.id == sel }
    val selectedStorePhoto = l?.let { current ->
        rememberWatchPhoto(
            "list-detail:${current.id}:${current.storePhoto}:${current.storePhotoSource}:${current.storePhotoUpdatedAt}",
            current.storePhoto,
            current.storePhotoSource,
            imageCache
        )
    }
    val visibleLists = lists.filter { it.active }
    val orderedItems = l?.let { current -> sortItems(current.items, current.itemSortOrder) }.orEmpty()
    val visibleItems = orderedItems.filter { showCheckedItems || !it.checked || it.id in fadingCheckedIds }
    val allItemsChecked = l?.items?.let { it.isNotEmpty() && it.all { product -> product.checked } } ?: false
    val completedTotal = l?.items?.filter { it.checked }?.sumOf { it.price } ?: 0.0

    LaunchedEffect(sel, allItemsChecked) {
        showCompletionPrompt = allItemsChecked
    }

    fun viewIcon(mode: Int): ImageVector = when (mode) {
        1 -> Icons.Default.GridView
        2 -> Icons.Default.ViewAgenda
        else -> Icons.Default.ViewList
    }

    fun viewLabel(mode: Int): String = when (mode) {
        0 -> "Vista de lista"
        1 -> "Vista de cuadrícula"
        else -> "Vista de lista amplia"
    }

    fun restartPurchase() {
        val listId = l?.id ?: return
        requirePhoneForChanges {
            Store.updateList(listId) { current ->
                current.copy(items = current.items.map { it.copy(checked = false, cartOrder = 0) })
            }
            showCheckedItems = false
            fadingCheckedIds = emptySet()
            showCompletionPrompt = false
        }
    }

    fun toggleItem(item: Item) {
        if (!canEditLists) {
            requirePhoneForChanges { }
            return
        }
        if (!item.checked && !showCheckedItems) {
            fadingCheckedIds = fadingCheckedIds + item.id
            scope.launch {
                delay(620)
                fadingCheckedIds = fadingCheckedIds - item.id
            }
        }
        val currentListId = l?.id ?: return
        toggleCheckedItem(currentListId, item.id)
    }

    val addVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.spoken()?.let { t -> l?.let { list -> requirePhoneForChanges { Store.addTo(list.id, Store.parseSpoken(t)) } } }
    }
    val multiFallbackVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.spoken()?.takeIf { it.isNotBlank() }?.let { recognizedVoiceItems = recognizedVoiceItems + it }
                ?: Toast.makeText(ctx, "No se reconoció ningún artículo", Toast.LENGTH_SHORT).show()
        }
        listeningMulti = true
    }
    val editVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.spoken(); val tg = target
        if (t != null && tg != null) requirePhoneForChanges {
            Store.updateList(tg.first) { s ->
                if (tg.second == null) s.copy(name = t) else s.copy(items = s.items.map { if (it.id == tg.second) it.copy(name = t) else it })
            }
        }
        target = null
    }
    fun startMultiRecognition() {
        val listId = l?.id ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            // El reconocedor externo devuelve una sola transcripción por pausa; acumular
            // cada resultado por separado evita fusionar varios artículos en uno.
            recognizedVoiceItems = emptyList()
            multiFallbackMode = true
            listeningMulti = false
            runCatching {
                multiFallbackVoice.launch(
                    voiceIntent().putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "Di un artículo; al terminar la pausa podrás dictar el siguiente"
                    ).putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                )
            }.onFailure {
                multiFallbackMode = false
                listeningMulti = false
                Toast.makeText(ctx, "No se pudo abrir el reconocimiento de voz", Toast.LENGTH_SHORT).show()
            }
            return
        }
        recognizedVoiceItems = emptyList()
        voiceAudioLevel = -2f
        val session = MultiItemSpeechSession(
            context = ctx,
            onPhrase = { recognizedVoiceItems = recognizedVoiceItems + it },
            onAudioLevel = { voiceAudioLevel = it },
            onResults = { phrases ->
                multiSession = null
                listeningMulti = false
                recognizedVoiceItems = emptyList()
                voiceAudioLevel = -2f
                val entries = phrases.flatMap(Store::parseMultipleSpoken)
                if (entries.isEmpty()) Toast.makeText(ctx, "No se reconocieron artículos", Toast.LENGTH_SHORT).show()
                else requirePhoneForChanges { Store.addTo(listId, entries) }
            },
            onError = {
                multiSession = null
                listeningMulti = false
                recognizedVoiceItems = emptyList()
                voiceAudioLevel = -2f
                Toast.makeText(ctx, "No se reconoció la voz", Toast.LENGTH_SHORT).show()
            }
        )
        multiSession = session
        listeningMulti = true
        runCatching { session.start() }.onFailure {
            session.cancel()
            multiSession = null
            listeningMulti = false
            Toast.makeText(ctx, "No se pudo iniciar el micrófono", Toast.LENGTH_SHORT).show()
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMultiRecognition()
        else Toast.makeText(ctx, "Se necesita permiso para usar el micrófono", Toast.LENGTH_SHORT).show()
    }
    fun requestMultiRecognition() {
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startMultiRecognition()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    ScalingLazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val tg = target
        val recognizedText = recognizedVoiceItems.flatMap(Store::parseMultipleSpoken).joinToString(", ") { it.first }
        if (!canEditLists) item {
            Text(
                if (phoneConnected) "Sincronizando las listas del teléfono…" else "Conecta tu teléfono para hacer cambios",
                color = MaterialTheme.colors.secondary,
                style = MaterialTheme.typography.caption1
            )
        }
        if (listeningMulti) {
            item { Text(if (multiFallbackMode) "Artículos dictados" else "Escuchando varios artículos") }
            if (!multiFallbackMode) item { SpeechAudioIndicator(voiceAudioLevel) }
            item {
                Text(
                    if (multiFallbackMode) "Cada captura termina con una pausa. Pulsa Siguiente para dictar otro artículo."
                    else "Haz una pausa entre artículos; pulsa Agregar cuando termines"
                )
            }
            item { Text(recognizedText.ifBlank { "Los artículos aparecerán aquí" }) }
            if (multiFallbackMode) {
                item {
                    Tile("Siguiente artículo", {
                        listeningMulti = false
                        runCatching {
                            multiFallbackVoice.launch(
                                voiceIntent().putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el siguiente artículo")
                                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                            )
                        }.onFailure { listeningMulti = true }
                    })
                }
                item {
                    Tile("Agregar artículos", {
                        val entries = recognizedVoiceItems.flatMap(Store::parseMultipleSpoken)
                        if (entries.isEmpty()) Toast.makeText(ctx, "No se reconocieron artículos", Toast.LENGTH_SHORT).show()
                        else l?.let { list -> requirePhoneForChanges {
                            Store.addTo(list.id, entries)
                            recognizedVoiceItems = emptyList()
                            multiFallbackMode = false
                            listeningMulti = false
                        } }
                    })
                }
            } else {
                item { Tile("Agregar artículos", { multiSession?.finish(); multiSession = null; listeningMulti = false }) }
            }
            item {
                Tile("Cancelar", {
                    multiSession?.cancel(); multiSession = null
                    recognizedVoiceItems = emptyList(); multiFallbackMode = false; listeningMulti = false
                })
            }
        } else if (showVoiceMode) {
            item { Text("¿Cuántos artículos vas a dictar?") }
            item { Tile("Un artículo", { showVoiceMode = false; addVoice.launch(voiceIntent()) }) }
            item { Tile("Varios artículos", { showVoiceMode = false; requestMultiRecognition() }) }
            item { Tile("Cancelar", { showVoiceMode = false }) }
        } else if (addingItemText) {
            item { Text("Nuevo objeto", style = MaterialTheme.typography.title3) }
            item { Text("Nombre") }
            item {
                BasicTextField(
                    value = itemNameDraft,
                    onValueChange = { itemNameDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            item { Text("Precio") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$", style = MaterialTheme.typography.body1)
                    BasicTextField(
                        value = itemPriceDraft,
                        onValueChange = { itemPriceDraft = it },
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface)
                    )
                }
            }
            item { Tile("Agregar objeto", {
                val name = itemNameDraft.trim()
                val price = itemPriceDraft.replace(',', '.').toDoubleOrNull()
                if (name.isEmpty()) Toast.makeText(ctx, "Escribe el nombre del objeto", Toast.LENGTH_SHORT).show()
                else if (price == null || !price.isFinite() || price < 0.0) Toast.makeText(ctx, "Escribe un precio válido", Toast.LENGTH_SHORT).show()
                else l?.let { list -> requirePhoneForChanges {
                    Store.addTo(list.id, listOf(name to price))
                    itemNameDraft = ""
                    itemPriceDraft = "0"
                    addingItemText = false
                } }
            }) }
            item { Tile("Cancelar", { itemNameDraft = ""; itemPriceDraft = "0"; addingItemText = false }) }
        } else if (textEditTarget != null) {
            val key = textEditTarget
            item { Text("Nombre de la lista", style = MaterialTheme.typography.title3) }
            item {
                BasicTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface)
                )
            }
            item { Tile("Guardar nombre", {
                val name = textDraft.trim()
                if (name.isEmpty()) Toast.makeText(ctx, "Escribe el nombre de la lista", Toast.LENGTH_SHORT).show()
                else if (key != null) requirePhoneForChanges {
                    Store.updateList(key.first) { it.copy(name = name) }
                    textEditTarget = null
                    textDraft = ""
                }
            }) }
            item { Tile("Cancelar", { textEditTarget = null; textDraft = "" }) }
        } else if (priceTarget != null) {
            val itemToPrice = lists.find { it.id == priceTarget?.first }?.items?.find { it.id == priceTarget?.second }
            item { Text("Precio de ${itemToPrice?.name.orEmpty()}", style = MaterialTheme.typography.title3) }
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$", style = MaterialTheme.typography.title2)
                    BasicTextField(
                        value = priceDraft,
                        onValueChange = { priceDraft = it },
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface)
                    )
                }
            }
            item { Tile("Guardar precio", {
                val amount = priceDraft.replace(',', '.').toDoubleOrNull()
                val key = priceTarget
                if (key != null && amount != null && amount.isFinite() && amount >= 0.0) requirePhoneForChanges {
                    Store.updateList(key.first) { s -> s.copy(items = s.items.map { if (it.id == key.second) it.copy(price = amount) else it }) }
                    priceTarget = null
                } else Toast.makeText(ctx, "Escribe un precio válido", Toast.LENGTH_SHORT).show()
            }) }
            item { Tile("Cancelar", { priceTarget = null }) }
        } else if (tg != null) {
            item { Text(if (confirm) "¿Seguro que quieres eliminar?" else "Opciones") }
            if (confirm) {
                item { Tile("Sí, eliminar", { requirePhoneForChanges {
                    if (tg.second == null) { Store.update { a -> a.filter { it.id != tg.first } }; sel = null }
                    else Store.updateList(tg.first) { s -> s.copy(items = s.items.filter { it.id != tg.second }) }
                    target = null; confirm = false
                } }) }
                item { Tile("Cancelar", { target = null; confirm = false }) }
            } else {
                val targetItem = tg.second?.let { itemId -> lists.find { it.id == tg.first }?.items?.find { it.id == itemId } }
                item { Text(if (targetItem == null) "Lista: ${lists.find { it.id == tg.first }?.name.orEmpty()}" else targetItem.name) }
                item { Tile("Cambiar nombre con voz", { requirePhoneForChanges { editVoice.launch(voiceIntent()) } }) }
                if (targetItem == null) item {
                    Tile("Cambiar nombre con texto", { requirePhoneForChanges {
                        textEditTarget = tg.first to null
                        textDraft = lists.find { it.id == tg.first }?.name.orEmpty()
                        target = null
                    } })
                }
                if (targetItem != null) item { Tile("Cambiar precio", { requirePhoneForChanges {
                    priceTarget = tg.first to targetItem.id
                    priceDraft = targetItem.price.toString()
                    target = null
                } }) }
                item { Tile("Eliminar", { requirePhoneForChanges { confirm = true } }) }
                item { Tile("Cancelar", { target = null }) }
            }
        } else if (l == null) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis listas", Modifier.weight(1f), style = MaterialTheme.typography.title3)
                    WatchActionIcon(viewIcon(listsViewMode), "Cambiar a ${viewLabel((listsViewMode + 1) % 3).lowercase()}",
                        onClick = { Store.setMainListsViewMode((listsViewMode + 1) % 3) })
                }
            }
            if (listsViewMode == 1) {
                items(visibleLists.chunked(2), key = { row -> row.firstOrNull()?.id ?: "empty" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { list ->
                            WatchListTile(list, 1, { sel = list.id }, { target = list.id to null }, imageCache, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(visibleLists, key = { it.id }) { list ->
                    WatchListTile(list, listsViewMode, { sel = list.id }, { target = list.id to null }, imageCache)
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WatchActionIcon(Icons.Default.ArrowBack, "Regresar a Mis listas", onClick = { sel = null })
                }
            }
            if (selectedStorePhoto != null) item {
                Box(
                    Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 3.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        selectedStorePhoto,
                        contentDescription = "Foto de ${l.store.ifBlank { l.name }}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                }
            }
            item { Text(l.store.ifBlank { "Sin tienda" }, style = MaterialTheme.typography.title3) }
            if (l.name.isNotBlank() && !l.name.equals(l.store, ignoreCase = true)) item {
                Text(l.name, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.onSurfaceVariant)
            }
            item {
                Text(
                    if (showAmount) "Monto: $${"%.2f".format(completedTotal)}" else "Monto",
                    modifier = Modifier.clickable { showAmount = !showAmount }.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    WatchActionIcon(Icons.Default.Mic, "Agregar por voz", onClick = { requirePhoneForChanges { showVoiceMode = true } })
                    WatchActionIcon(Icons.Default.Add, "Agregar objeto por texto", onClick = { requirePhoneForChanges {
                        itemNameDraft = ""
                        itemPriceDraft = "0"
                        addingItemText = true
                    } }, iconSize = 31.dp)
                    WatchActionIcon(viewIcon(itemViewMode), "Cambiar a ${viewLabel((itemViewMode + 1) % 3).lowercase()}",
                        onClick = { requirePhoneForChanges {
                            val nextMode = (itemViewMode + 1) % 3
                            itemViewMode = nextMode
                            Store.updateList(l.id) { it.copy(viewMode = nextMode) }
                        } })
                    WatchActionIcon(
                        if (showCheckedItems) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (showCheckedItems) "Ocultar artículos marcados" else "Mostrar artículos marcados",
                        onClick = { showCheckedItems = !showCheckedItems }
                    )
                    WatchActionIcon(
                        Icons.Default.Refresh,
                        "Volver a empezar la lista",
                        tint = if (l.items.any { it.checked }) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurfaceVariant,
                        onClick = {
                            if (l.items.any { it.checked }) restartPurchase()
                            else Toast.makeText(ctx, "Todavía no hay artículos marcados", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            if (allItemsChecked && showCompletionPrompt) {
                item { Text("¡Terminaste tu lista de compra!", style = MaterialTheme.typography.title3, color = MaterialTheme.colors.primary) }
                item { Text("¿Te gustaría volver a empezar?") }
                item { Tile("Sí, empezar de nuevo", { restartPurchase() }) }
                item { Tile("No por ahora", { showCompletionPrompt = false }) }
            } else if (allItemsChecked && !showCheckedItems) {
                item { Text("Lista de compra terminada. Puedes volver a marcarla cuando quieras.", color = MaterialTheme.colors.onSurfaceVariant) }
            } else if (visibleItems.isEmpty()) {
                item { Text(if (l.items.isEmpty()) "Lista vacía" else "Todo listo" , color = MaterialTheme.colors.onSurfaceVariant) }
            } else if (itemViewMode == 1) {
                items(visibleItems.chunked(2), key = { row -> row.firstOrNull()?.id ?: "empty" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { product ->
                            WatchItemTile(
                                item = product, mode = 1, expanded = product.id in expandedItemIds,
                                onExpand = { expandedItemIds = if (product.id in expandedItemIds) expandedItemIds - product.id else expandedItemIds + product.id },
                                onToggleCheck = { toggleItem(product) },
                                onEditName = { requirePhoneForChanges { target = l.id to product.id; editVoice.launch(voiceIntent()) } },
                                onEditPrice = { requirePhoneForChanges { priceTarget = l.id to product.id; priceDraft = product.price.toString() } },
                                onOptions = { target = l.id to product.id }, imageCache = imageCache,
                                animateRemoval = product.id in fadingCheckedIds, modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(visibleItems, key = { it.id }) { product ->
                    WatchItemTile(
                        item = product, mode = itemViewMode, expanded = product.id in expandedItemIds,
                        onExpand = { expandedItemIds = if (product.id in expandedItemIds) expandedItemIds - product.id else expandedItemIds + product.id },
                        onToggleCheck = { toggleItem(product) },
                        onEditName = { requirePhoneForChanges { target = l.id to product.id; editVoice.launch(voiceIntent()) } },
                        onEditPrice = { requirePhoneForChanges { priceTarget = l.id to product.id; priceDraft = product.price.toString() } },
                        onOptions = { target = l.id to product.id }, imageCache = imageCache,
                        animateRemoval = product.id in fadingCheckedIds
                    )
                }
            }
        }
    }
}

private fun toggleCheckedItem(listId: String, itemId: String) {
    Store.updateList(listId) { s ->
        val item = s.items.firstOrNull { it.id == itemId }
        when {
            item == null -> s
            item.checked -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(checked = false) else it })
            else -> {
                val next = (s.items.maxOfOrNull { it.cartOrder } ?: 0) + 1
                s.copy(items = s.items.map { if (it.id == itemId) it.copy(checked = true, cartOrder = next) else it })
            }
        }
    }
}
