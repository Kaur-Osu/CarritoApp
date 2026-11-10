package com.carlosmvp.mandado

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

private const val STORE_PHOTO_ID = "__store_photo__"
private const val NEW_ITEM_PHOTO_ID = "__new_item_photo__"
private const val SHOP_PHOTO_PREFIX = "__catalog_shop__:"
private const val PHOTO_BACKGROUND_DARKENING = 0.36f

private enum class ListSortOrder { MANUAL, NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC, DATE_ASC, DATE_DESC }

private val MandadoLightColors = lightColorScheme(
    primary = Color(0xFF286B57), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0E4), onPrimaryContainer = Color(0xFF123A2E),
    secondary = Color(0xFFAA6038), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E1D3), onSecondaryContainer = Color(0xFF4B2815),
    tertiary = Color(0xFF546C86), onTertiary = Color.White,
    background = Color(0xFFF4F7F3), onBackground = Color(0xFF19231D),
    surface = Color(0xFFFCFDFC), onSurface = Color(0xFF19231D),
    surfaceVariant = Color(0xFFE8EEE9), onSurfaceVariant = Color(0xFF46564D),
    outline = Color(0xFF74847A), outlineVariant = Color(0xFFCAD5CD),
    error = Color(0xFFB3261E), onError = Color.White
)

private val MandadoDarkColors = darkColorScheme(
    primary = Color(0xFF9BD8BE), onPrimary = Color(0xFF12382D),
    primaryContainer = Color(0xFF285747), onPrimaryContainer = Color(0xFFD5F3E4),
    secondary = Color(0xFFF0B58F), onSecondary = Color(0xFF492612),
    secondaryContainer = Color(0xFF5C3A29), onSecondaryContainer = Color(0xFFFFDCC6),
    tertiary = Color(0xFFB5C9E3), onTertiary = Color(0xFF26384C),
    background = Color(0xFF111914), onBackground = Color(0xFFE1E9E2),
    surface = Color(0xFF19221D), onSurface = Color(0xFFE1E9E2),
    surfaceVariant = Color(0xFF26322B), onSurfaceVariant = Color(0xFFC0CDC4),
    outline = Color(0xFF8B9A90), outlineVariant = Color(0xFF435148),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

private val MandadoBaseTypography = Typography()
private val MandadoTypography = MandadoBaseTypography.copy(
    headlineSmall = MandadoBaseTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = MandadoBaseTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleMedium = MandadoBaseTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    bodyLarge = MandadoBaseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, letterSpacing = 0.1.sp),
    bodyMedium = MandadoBaseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = MandadoBaseTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
)

@Composable
private fun MandadoTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) MandadoDarkColors else MandadoLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MandadoTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground, content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionIconButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface,
                             modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.size(48.dp).then(modifier).combinedClickable(
        onClick = onClick,
        onLongClick = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() }
    ), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); Store.init(this)
        setContent { MandadoTheme { App(onExit = { finish() }) } }
    }
}

@Composable fun App(onExit: () -> Unit) {
    var sel by remember { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val lists by Store.lists.collectAsState()
    val l = lists.find { it.id == sel }
    BackHandler(enabled = true) {
        if (sel != null) sel = null else showExitConfirmation = true
    }
    if (l == null) Lists(lists) { sel = it } else Items(l) { sel = null }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Salir de Carrito") },
            text = { Text("¿Quieres salir de la app?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onExit()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ShopListControls(list: ShopList, foreground: Color, onImage: Boolean, onDelete: () -> Unit) {
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.surface,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = if (onImage) Color.White else MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = if (onImage) Color.White else MaterialTheme.colorScheme.surface,
        uncheckedTrackColor = if (onImage) Color.White.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outlineVariant,
        uncheckedBorderColor = if (onImage) Color.White else MaterialTheme.colorScheme.outline
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = list.active,
            onCheckedChange = { active -> Store.updateList(list.id) { it.copy(active = active) } },
            colors = switchColors
        )
        ActionIconButton(Icons.Default.ContentCopy, "Duplicar lista", tint = if (onImage) Color.White else foreground, onClick = {
            Store.update { all -> all + list.copy(
                id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), active = true,
                items = list.items.map { it.copy(id = UUID.randomUUID().toString(), checked = false, cartOrder = 0) }
            ) }
        })
        ActionIconButton(Icons.Default.Delete, "Eliminar lista", tint = if (onImage) Color.White else foreground, onClick = onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShopListCard(list: ShopList, mode: Int, open: (String) -> Unit, selecting: Boolean,
                         selected: Boolean, onToggleSelection: () -> Unit, onLongPress: () -> Unit, onDelete: () -> Unit) {
    fun cardClick() { if (selecting) onToggleSelection() else open(list.id) }
    val selectionModifier = Modifier.combinedClickable(onClick = { cardClick() }, onLongClick = onLongPress)
    val image = remember(list.storePhoto) {
        list.storePhoto.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    val foreground = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface
    val selectionBorder by animateDpAsState(if (selected) 3.dp else 0.dp, label = "selection-border")
    val cardSurface by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "list-card-surface"
    )
    val details = "${list.items.size} objetos · ${DateFormat.getDateInstance().format(Date(list.createdAt))}"
    if (mode == 1) {
        Box(Modifier.fillMaxWidth().padding(4.dp).height(190.dp).shadow(3.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .animateContentSize().border(selectionBorder, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .background(cardSurface).then(selectionModifier)) {
            if (image != null) Image(image, "Foto de ${list.store}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (image != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(10.dp)) {
                Text(list.name, color = foreground, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(details, color = foreground.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
            }
            if (selecting) {
                Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Seleccionada", Modifier.align(Alignment.TopEnd).padding(8.dp), tint = Color.White)
            } else Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.Center) {
                ShopListControls(list, foreground, image != null, onDelete)
            }
        }
    } else if (mode == 2) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .animateContentSize().border(selectionBorder, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .background(cardSurface).then(selectionModifier), verticalAlignment = Alignment.CenterVertically) {
            if (image != null) Box(Modifier.size(width = 112.dp, height = 104.dp).clip(RoundedCornerShape(10.dp))) {
                Image(image, "Foto de ${list.store}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
            } else Box(Modifier.size(width = 112.dp, height = 104.dp), contentAlignment = Alignment.Center) {
                Text("Sin foto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(list.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selecting) Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Seleccionada", tint = MaterialTheme.colorScheme.primary)
                else ShopListControls(list, foreground, false, onDelete)
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
            .animateContentSize().border(selectionBorder, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).then(selectionModifier)) {
            if (image != null) {
                Image(image, "Foto de ${list.store}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
            } else Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium, color = foreground)
                    Text(details, color = foreground.copy(alpha = 0.9f))
                }
                if (selecting) Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "Seleccionada", tint = if (image != null) Color.White else MaterialTheme.colorScheme.primary)
                else ShopListControls(list, foreground, image != null, onDelete)
            }
        }
    }
}

@Composable
private fun ListReorderRow(list: ShopList, dragHandle: Modifier, modifier: Modifier = Modifier) {
    val image = remember(list.storePhoto) {
        list.storePhoto.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Row(modifier.animateContentSize().fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(dragHandle.width(42.dp).height(88.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DragHandle, contentDescription = "Arrastrar para reordenar", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (image != null) Image(image, "Foto de ${list.store}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            if (image != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
            Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp)) {
                Text(list.name, color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${list.items.size} objetos · ${DateFormat.getDateInstance().format(Date(list.createdAt))}",
                    color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ItemReorderRow(item: Item, dragHandle: Modifier, modifier: Modifier = Modifier) {
    val image = remember(item.photo) {
        item.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    Row(modifier.animateContentSize().fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(dragHandle.width(42.dp).height(76.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DragHandle, contentDescription = "Arrastrar para reordenar", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically) {
            if (image != null) Image(image, "Foto de ${item.name}", Modifier.size(76.dp), contentScale = ContentScale.Crop)
            else Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Text("Sin foto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$${"%.2f".format(item.price)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Lists(lists: List<ShopList>, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val shops by Store.shops.collectAsState()
    val mainListsViewMode by Store.mainListsViewMode.collectAsState()
    var listName by remember { mutableStateOf("") }
    var newShopName by remember { mutableStateOf("") }
    var selectedShopId by remember { mutableStateOf<String?>(null) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var showShopManager by remember { mutableStateOf(false) }
    var shopPhotoOptionsFor by remember { mutableStateOf<String?>(null) }
    var editingShopId by remember { mutableStateOf<String?>(null) }
    var editingShopName by remember { mutableStateOf("") }
    var pendingShopPhotoId by remember { mutableStateOf<String?>(null) }
    var selectedListIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var listToDelete by remember { mutableStateOf<String?>(null) }
    var confirmDeleteSelectedLists by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(ListSortOrder.MANUAL) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isReorderingLists by remember { mutableStateOf(false) }
    var reorderListIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var listDragCarry by remember { mutableFloatStateOf(0f) }
    val listDragStepPx = with(LocalDensity.current) { 72.dp.toPx() }
    val sortedLists = when (sortOrder) {
        ListSortOrder.MANUAL -> lists
        ListSortOrder.NAME_ASC -> lists.sortedBy { it.name.lowercase() }
        ListSortOrder.NAME_DESC -> lists.sortedByDescending { it.name.lowercase() }
        ListSortOrder.PRICE_ASC -> lists.sortedBy { list -> list.items.sumOf { it.price } }
        ListSortOrder.PRICE_DESC -> lists.sortedByDescending { list -> list.items.sumOf { it.price } }
        ListSortOrder.DATE_ASC -> lists.sortedBy { it.createdAt }
        ListSortOrder.DATE_DESC -> lists.sortedByDescending { it.createdAt }
    }
    fun updateShopPhoto(shopId: String, path: String) {
        val shop = shops.find { it.id == shopId } ?: return
        Store.updateShops { all -> all.map { if (it.id == shopId) it.copy(photo = path, photoSource = "") else it } }
        Store.update { all -> all.map { if (it.store.equals(shop.name, ignoreCase = true)) it.copy(storePhoto = path, storePhotoSource = "") else it } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        val shopId = pendingShopPhotoId
        if (bitmap != null && shopId != null) {
            val file = File(ctx.filesDir, "shop-${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            updateShopPhoto(shopId, file.absolutePath)
        }
        pendingShopPhotoId = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val shopId = pendingShopPhotoId
        if (uri != null && shopId != null) runCatching {
            val file = File(ctx.filesDir, "shop-${UUID.randomUUID()}.image")
            (ctx.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la imagen")).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }.onSuccess { file -> updateShopPhoto(shopId, file.absolutePath) }
            .onFailure { Toast.makeText(ctx, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show() }
        pendingShopPhotoId = null
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mis listas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ActionIconButton(Icons.Default.Store, "Administrar tiendas", onClick = { showShopManager = true })
            Button(onClick = { listName = ""; newShopName = ""; selectedShopId = null; showNewListDialog = true }) {
                Icon(Icons.Default.Add, null); Text("Lista")
            }
        }
        if (selectedListIds.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    selectedListIds = if (selectedListIds.size == lists.size) emptySet() else lists.map { it.id }.toSet()
                }) { Text(if (selectedListIds.size == lists.size) "Deseleccionar todas" else "Seleccionar todas") }
                TextButton(onClick = { confirmDeleteSelectedLists = true }) { Text("Borrar (${selectedListIds.size})") }
              }
              TextButton(onClick = { selectedListIds = emptySet() }) { Text("Cancelar selección") }
            }
        } else if (isReorderingLists) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { isReorderingLists = false; reorderListIds = emptyList() }) { Text("Cancelar") }
                Button(onClick = {
                    Store.update { current ->
                        val byId = current.associateBy { it.id }
                        reorderListIds.mapNotNull { byId[it] } + current.filter { it.id !in reorderListIds }
                    }
                    isReorderingLists = false
                    reorderListIds = emptyList()
                }) { Text("Guardar orden") }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = null)
                    Text(when (sortOrder) {
                        ListSortOrder.MANUAL -> "Manual"
                        ListSortOrder.NAME_ASC -> "A → Z"
                        ListSortOrder.NAME_DESC -> "Z → A"
                        ListSortOrder.PRICE_ASC -> "Total ↑"
                        ListSortOrder.PRICE_DESC -> "Total ↓"
                        ListSortOrder.DATE_ASC -> "Fecha ↑"
                        ListSortOrder.DATE_DESC -> "Fecha ↓"
                    })
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf(
                        ListSortOrder.MANUAL to "Orden manual",
                        ListSortOrder.NAME_ASC to "Nombre A → Z",
                        ListSortOrder.NAME_DESC to "Nombre Z → A",
                        ListSortOrder.PRICE_ASC to "Total menor a mayor",
                        ListSortOrder.PRICE_DESC to "Total mayor a menor",
                        ListSortOrder.DATE_ASC to "Fecha: más antigua a más nueva",
                        ListSortOrder.DATE_DESC to "Fecha: más nueva a más antigua"
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { sortOrder = mode; showSortMenu = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { Store.setMainListsViewMode((mainListsViewMode + 1) % 3) }) {
                Text(when (mainListsViewMode) { 0 -> "Lista"; 1 -> "Cuadrícula"; else -> "Lista amplia" })
            }
            ActionIconButton(Icons.Default.SwapVert, "Ordenar manualmente", onClick = {
                reorderListIds = sortedLists.map { it.id }
                sortOrder = ListSortOrder.MANUAL
                isReorderingLists = true
            })
        }
        val displayedReorderLists = if (isReorderingLists) {
            val byId = lists.associateBy { it.id }
            reorderListIds.mapNotNull { byId[it] } + lists.filter { it.id !in reorderListIds }
        } else emptyList()
        val reorderHandle: (ShopList) -> Modifier = { list ->
            Modifier.pointerInput(list.id, isReorderingLists) {
                detectDragGestures(
                    onDragStart = { listDragCarry = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        listDragCarry += amount.y
                        while (listDragCarry >= listDragStepPx || listDragCarry <= -listDragStepPx) {
                            val direction = if (listDragCarry > 0f) 1 else -1
                            val from = reorderListIds.indexOf(list.id)
                            val to = from + direction
                            if (from >= 0 && to in reorderListIds.indices) {
                                reorderListIds = reorderListIds.toMutableList().apply { add(to, removeAt(from)) }
                                listDragCarry -= direction * listDragStepPx
                            } else listDragCarry = 0f
                        }
                    },
                    onDragEnd = { listDragCarry = 0f },
                    onDragCancel = { listDragCarry = 0f }
                )
            }
        }
        when (if (isReorderingLists) 3 else mainListsViewMode) {
            1 -> LazyVerticalGrid(columns = GridCells.Adaptive(168.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) {
                gridItems(sortedLists, key = { it.id }) { list ->
                    ShopListCard(list, 1, open, selectedListIds.isNotEmpty(), list.id in selectedListIds,
                        onToggleSelection = { selectedListIds = if (list.id in selectedListIds) selectedListIds - list.id else selectedListIds + list.id },
                        onLongPress = { if (list.id !in selectedListIds) selectedListIds = selectedListIds + list.id else selectedListIds = selectedListIds - list.id },
                        onDelete = { listToDelete = list.id })
                }
            }
            2 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(sortedLists, key = { it.id }) { list ->
                    ShopListCard(list, 2, open, selectedListIds.isNotEmpty(), list.id in selectedListIds,
                        onToggleSelection = { selectedListIds = if (list.id in selectedListIds) selectedListIds - list.id else selectedListIds + list.id },
                        onLongPress = { if (list.id !in selectedListIds) selectedListIds = selectedListIds + list.id else selectedListIds = selectedListIds - list.id },
                        onDelete = { listToDelete = list.id })
                }
            }
            3 -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(displayedReorderLists, key = { it.id }) { list ->
                    ListReorderRow(list, reorderHandle(list), Modifier.animateItemPlacement())
                }
            }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(sortedLists, key = { it.id }) { list ->
                    ShopListCard(list, 0, open, selectedListIds.isNotEmpty(), list.id in selectedListIds,
                        onToggleSelection = { selectedListIds = if (list.id in selectedListIds) selectedListIds - list.id else selectedListIds + list.id },
                        onLongPress = { if (list.id !in selectedListIds) selectedListIds = selectedListIds + list.id else selectedListIds = selectedListIds - list.id },
                        onDelete = { listToDelete = list.id })
                }
            }
        }
    }
    val listPendingDeletion = lists.find { it.id == listToDelete }
    if (listPendingDeletion != null) {
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Seguro que deseas eliminar «${listPendingDeletion.name}»?") },
            confirmButton = { TextButton(onClick = { Store.update { all -> all.filterNot { it.id == listPendingDeletion.id } }; listToDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { listToDelete = null }) { Text("Cancelar") } }
        )
    }
    if (confirmDeleteSelectedLists) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelectedLists = false },
            title = { Text("Eliminar listas seleccionadas") },
            text = { Text("¿Seguro que deseas eliminar ${selectedListIds.size} listas?") },
            confirmButton = { TextButton(onClick = {
                Store.update { all -> all.filterNot { it.id in selectedListIds } }
                selectedListIds = emptySet()
                confirmDeleteSelectedLists = false
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDeleteSelectedLists = false }) { Text("Cancelar") } }
        )
    }
    if (showNewListDialog) {
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text("Nueva lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(listName, { listName = it }, label = { Text("Nombre de la lista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Elige una tienda", style = MaterialTheme.typography.titleSmall)
                    LazyRow(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shops, key = { it.id }) { shop ->
                            val image = remember(shop.photo) { shop.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                            Box(Modifier.width(108.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .clickable { selectedShopId = shop.id }
                                .background(if (selectedShopId == shop.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                                if (image != null) Image(image, "Foto de ${shop.name}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                if (image != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
                                Text(shop.name, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp),
                                    color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (selectedShopId == shop.id) Box(Modifier.matchParentSize().border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newShopName, { newShopName = it }, Modifier.weight(1f), label = { Text("Agregar tienda nueva") }, singleLine = true)
                        ActionIconButton(Icons.Default.Add, "Agregar tienda", onClick = {
                            val shopName = newShopName.trim()
                            if (shopName.isNotBlank()) {
                                val existing = shops.firstOrNull { it.name.equals(shopName, ignoreCase = true) }
                                val shop = existing ?: StoreShop(name = shopName).also { created -> Store.updateShops { it + created } }
                                selectedShopId = shop.id
                                newShopName = ""
                            } else Toast.makeText(ctx, "Porfavor ingrese un nombre de la tienda", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val title = listName.trim()
                if (title.isNotBlank()) {
                    val shop = shops.find { it.id == selectedShopId }
                    Store.update { it + ShopList(name = title, store = shop?.name.orEmpty(), storePhoto = shop?.photo.orEmpty(), storePhotoSource = shop?.photoSource.orEmpty()) }
                    showNewListDialog = false
                } else Toast.makeText(ctx, "Porfavor ingrese un nombre de la lista", Toast.LENGTH_SHORT).show()
            }) { Text("Crear lista") } },
            dismissButton = { TextButton(onClick = { showNewListDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showShopManager) {
        Dialog(onDismissRequest = { showShopManager = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tiendas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        ActionIconButton(Icons.Default.Close, "Cerrar tiendas", onClick = { showShopManager = false })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newShopName, { newShopName = it }, Modifier.weight(1f), label = { Text("Nueva tienda") }, singleLine = true)
                        ActionIconButton(Icons.Default.Add, "Agregar tienda", onClick = {
                            val value = newShopName.trim()
                            if (value.isBlank()) Toast.makeText(ctx, "Porfavor ingrese un nombre de la tienda", Toast.LENGTH_SHORT).show()
                            else if (shops.none { it.name.equals(value, ignoreCase = true) }) {
                                Store.updateShops { it + StoreShop(name = value) }
                                newShopName = ""
                            } else Toast.makeText(ctx, "Esa tienda ya existe", Toast.LENGTH_SHORT).show()
                        })
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(shops, key = { it.id }) { shop ->
                            val image = remember(shop.photo) { shop.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 76.dp, height = 60.dp).clip(RoundedCornerShape(8.dp))) {
                                    if (image != null) Image(image, "Foto de ${shop.name}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                    else Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface))
                                    if (image != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
                                    Text(shop.name.take(1).uppercase(), Modifier.align(Alignment.Center), color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface)
                                }
                                Text(shop.name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ActionIconButton(Icons.Default.Edit, "Editar nombre de tienda", onClick = { editingShopId = shop.id; editingShopName = shop.name })
                                ActionIconButton(Icons.Default.CameraAlt, "Modificar foto de tienda", onClick = { shopPhotoOptionsFor = shop.id })
                                ActionIconButton(Icons.Default.Delete, "Eliminar tienda", onClick = {
                                    Store.updateShops { all -> all.filterNot { it.id == shop.id } }
                                    if (selectedShopId == shop.id) selectedShopId = null
                                })
                            }
                        }
                    }
                }
            }
        }
    }
    val shopForPhoto = shops.find { it.id == shopPhotoOptionsFor }
    if (shopForPhoto != null) {
        AlertDialog(
            onDismissRequest = { shopPhotoOptionsFor = null }, title = { Text("Foto de ${shopForPhoto.name}") },
            text = { Text("Elige cómo cambiar o quitar la foto") },
            confirmButton = { TextButton(onClick = { pendingShopPhotoId = shopForPhoto.id; shopPhotoOptionsFor = null; camera.launch(null) }) { Text("Cámara") } },
            dismissButton = { Row {
                TextButton(onClick = { pendingShopPhotoId = shopForPhoto.id; shopPhotoOptionsFor = null; gallery.launch("image/*") }) { Text("Galería") }
                TextButton(onClick = { updateShopPhoto(shopForPhoto.id, ""); shopPhotoOptionsFor = null }) { Text("Quitar foto") }
            } }
        )
    }
    if (editingShopId != null) {
        AlertDialog(onDismissRequest = { editingShopId = null }, title = { Text("Nombre de la tienda") },
            text = { OutlinedTextField(editingShopName, { editingShopName = it }, label = { Text("Nombre") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val id = editingShopId
                val oldName = shops.find { it.id == id }?.name
                val newName = editingShopName.trim()
                if (newName.isBlank()) Toast.makeText(ctx, "Porfavor ingrese un nombre de la tienda", Toast.LENGTH_SHORT).show()
                else if (shops.any { it.id != id && it.name.equals(newName, ignoreCase = true) }) Toast.makeText(ctx, "Esa tienda ya existe", Toast.LENGTH_SHORT).show()
                else if (id != null) {
                    Store.updateShops { all -> all.map { if (it.id == id) it.copy(name = newName) else it } }
                    if (oldName != null) Store.update { all -> all.map { if (it.store.equals(oldName, ignoreCase = true)) it.copy(store = newName) else it } }
                    editingShopId = null
                }
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editingShopId = null }) { Text("Cancelar") } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Items(initialList: ShopList, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val allLists by Store.lists.collectAsState()
    val l = allLists.firstOrNull { it.id == initialList.id } ?: initialList
    val catalogShops by Store.shops.collectAsState()
    val suggestions = remember(l, allLists) { Store.suggest(l) }
    var dismissedSuggestions by remember(l.id) { mutableStateOf<Set<String>>(emptySet()) }
    val visibleSuggestions = suggestions.filter { it.trim().lowercase() !in dismissedSuggestions }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    var newItemPhoto by remember { mutableStateOf("") }
    var newItemPhotoSource by remember { mutableStateOf("") }
    var showNewItemPhotoOptions by remember { mutableStateOf(false) }
    var showNewItemPhotoPreview by remember { mutableStateOf(false) }
    var showSuggestions by remember(l.id) { mutableStateOf(l.suggestionsVisible) }
    var itemLayout by remember(l.id) { mutableIntStateOf(l.viewMode.coerceIn(0, 2)) }
    var totalExpanded by remember(l.id) { mutableStateOf(true) }
    var isSearchingPhoto by remember { mutableStateOf(false) }
    var photoSearchFailed by remember { mutableStateOf(false) }
    var photoSearchRequest by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var sortOrder by remember(l.id, l.itemSortOrder) { mutableStateOf(l.itemSortOrder) }
    var sortBeforeReorder by remember(l.id) { mutableStateOf(l.itemSortOrder) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isReorderingItems by remember(l.id) { mutableStateOf(false) }
    var reorderItemIds by remember(l.id) { mutableStateOf<List<String>>(emptyList()) }
    var itemDragCarry by remember { mutableFloatStateOf(0f) }
    val itemDragStepPx = with(LocalDensity.current) { 64.dp.toPx() }
    var photoFor by remember { mutableStateOf<String?>(null) }
    var photoQueue by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var priceFor by remember { mutableStateOf<String?>(null) }
    var priceText by remember { mutableStateOf("") }
    var photoViewerItemId by remember { mutableStateOf<String?>(null) }
    var photoOptionsFor by remember { mutableStateOf<String?>(null) }
    var selectedItemIds by remember(l.id) { mutableStateOf<Set<String>>(emptySet()) }
    var itemToDelete by remember { mutableStateOf<String?>(null) }
    var confirmDeleteSelectedItems by remember { mutableStateOf(false) }
    var storePhotoOptions by remember { mutableStateOf(false) }
    var showStorePhoto by remember { mutableStateOf(false) }
    var showVoiceMode by remember { mutableStateOf(false) }
    var listeningMulti by remember { mutableStateOf(false) }
    var voiceAudioLevel by remember { mutableStateOf(-2f) }
    var recognizedVoiceItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var showVoiceDetailsQuestion by remember { mutableStateOf(false) }
    var showVoiceDetails by remember { mutableStateOf(false) }
    var voiceDetailItemIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var voiceDetailIndex by remember { mutableIntStateOf(0) }
    var voiceDetailName by remember { mutableStateOf("") }
    var voiceDetailPrice by remember { mutableStateOf("") }
    var resumeVoiceDetailsAfterPhoto by remember { mutableStateOf(false) }
    var multiSession by remember { mutableStateOf<MultiItemSpeechSession?>(null) }
    var editingItem by remember { mutableStateOf<String?>(null) }
    var editingField by remember { mutableStateOf("") }
    var editText by remember { mutableStateOf("") }
    var editingStoreName by remember { mutableStateOf(false) }
    var newStoreSelectionName by remember { mutableStateOf("") }
    var editingListName by remember { mutableStateOf(false) }
    var listNameDraft by remember { mutableStateOf(l.name) }
    fun toggleItemSelection(itemId: String) {
        selectedItemIds = if (itemId in selectedItemIds) selectedItemIds - itemId else selectedItemIds + itemId
    }
    fun selectionAwareClick(onNormalClick: () -> Unit): Modifier =
        if (selectedItemIds.isNotEmpty()) Modifier else Modifier.clickable(onClick = onNormalClick)
    fun updatePhoto(targetId: String, path: String, source: String) {
        if (targetId == NEW_ITEM_PHOTO_ID) {
            newItemPhoto = path
            newItemPhotoSource = source
            return
        }
        if (targetId == STORE_PHOTO_ID && l.store.isNotBlank()) {
            Store.updateShops { all -> all.map { if (it.name.equals(l.store, ignoreCase = true)) it.copy(photo = path, photoSource = source) else it } }
        }
        Store.updateList(l.id) { s ->
            if (targetId == STORE_PHOTO_ID) s.copy(storePhoto = path, storePhotoSource = source)
            else s.copy(items = s.items.map { if (it.id == targetId) it.copy(photo = path, photoSource = source) else it })
        }
    }
    fun resumePendingEditorIfNeeded() {
        if (resumeVoiceDetailsAfterPhoto) {
            resumeVoiceDetailsAfterPhoto = false
            showVoiceDetails = true
        }
    }
    fun loadVoiceDetail(index: Int) {
        val id = voiceDetailItemIds.getOrNull(index) ?: return
        val item = l.items.find { it.id == id } ?: return
        voiceDetailIndex = index
        voiceDetailName = item.name
        voiceDetailPrice = if (item.price == 0.0) "" else item.price.toString()
    }
    fun saveVoiceDetail() {
        val id = voiceDetailItemIds.getOrNull(voiceDetailIndex) ?: return
        val price = voiceDetailPrice.replace(',', '.').toDoubleOrNull()
        Store.updateList(l.id) { s ->
            s.copy(items = s.items.map { item ->
                if (item.id == id) item.copy(
                    name = voiceDetailName.trim().ifBlank { item.name },
                    price = if (voiceDetailPrice.isBlank()) 0.0 else price?.takeIf { it.isFinite() && it >= 0.0 } ?: item.price
                ) else item
            })
        }
    }
    fun processMultiplePhrases(phrases: List<String>) {
        val entries = phrases.flatMap(Store::parseMultipleSpoken)
        if (entries.isEmpty()) {
            Toast.makeText(ctx, "No se reconocieron artículos", Toast.LENGTH_SHORT).show()
            return
        }
        voiceDetailItemIds = Store.addTo(l.id, entries).map { it.id }
        showVoiceDetailsQuestion = true
    }
    fun saveNewItem() {
        if (newItemName.isBlank()) {
            Toast.makeText(ctx, "Escribe el nombre del objeto", Toast.LENGTH_SHORT).show()
            return
        }
        val price = if (newItemPrice.isBlank()) 0.0 else newItemPrice.replace(',', '.').toDoubleOrNull()
        if (price == null || !price.isFinite() || price < 0.0) {
            Toast.makeText(ctx, "Escribe un precio válido", Toast.LENGTH_SHORT).show()
            return
        }
        val item = Store.addTo(l.id, listOf(newItemName.trim() to price)).firstOrNull()
        if (item != null && newItemPhoto.isNotBlank()) {
            Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(photo = newItemPhoto, photoSource = newItemPhotoSource) else it }) }
        }
        newItemName = ""
        newItemPrice = ""
        newItemPhoto = ""
        newItemPhotoSource = ""
        showAddItemDialog = false
    }
    fun startMultiRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            Toast.makeText(ctx, "No hay reconocimiento de voz disponible", Toast.LENGTH_SHORT).show()
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
                processMultiplePhrases(phrases)
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
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { photoQueue = Store.parseSpoken(it) }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        val id = photoFor
        if (bmp != null && id != null) {
            val f = File(ctx.filesDir, "${UUID.randomUUID()}.jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            updatePhoto(id, f.absolutePath, "")
        }
        photoFor = null
        resumePendingEditorIfNeeded()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = photoFor
        if (uri != null && id != null) {
            val f = File(ctx.filesDir, "${UUID.randomUUID()}.image")
            runCatching {
                val input = ctx.contentResolver.openInputStream(uri) ?: error("No se pudo abrir la imagen")
                input.use { source -> f.outputStream().use { output -> source.copyTo(output) } }
            }
                .onSuccess {
                    updatePhoto(id, f.absolutePath, "")
                }
                .onFailure { Toast.makeText(ctx, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show() }
        }
        photoFor = null
        resumePendingEditorIfNeeded()
    }

    fun chooseFromInternet(id: String, name: String, excludeSource: String = "") {
        photoFor = id
        photoSearchRequest = Triple(id, name, excludeSource)
        photoSearchFailed = false
        isSearchingPhoto = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { downloadRelevantPhoto(ctx.filesDir, name, excludeSource) }
            isSearchingPhoto = false
            if (result != null) {
                updatePhoto(id, result.first, result.second)
                photoSearchFailed = false
            } else photoSearchFailed = true
            photoFor = null
            resumePendingEditorIfNeeded()
        }
    }
    val sortedItems = sortItems(l.items, sortOrder)
    val displayedItems = if (isReorderingItems) {
        val byId = l.items.associateBy { it.id }
        reorderItemIds.mapNotNull { byId[it] } + l.items.filter { it.id !in reorderItemIds }
    } else sortedItems
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Volver a listas"); Text("Listas") }
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Text(l.name, Modifier.fillMaxWidth().padding(horizontal = 48.dp).clickable { listNameDraft = l.name; editingListName = true },
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ActionIconButton(Icons.Default.Edit, "Modificar nombre de la lista", modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { listNameDraft = l.name; editingListName = true })
        }
        val storeBanner = remember(l.storePhoto) { if (l.storePhoto.isNotBlank()) BitmapFactory.decodeFile(l.storePhoto)?.asImageBitmap() else null }
        Box(Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(14.dp)).clickable { storePhotoOptions = true }
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (storeBanner != null) Image(storeBanner, "Foto de ${l.store}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (storeBanner == null) 0.08f else PHOTO_BACKGROUND_DARKENING)))
            Row(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (l.store.isBlank()) "Foto de la tienda" else l.store, style = MaterialTheme.typography.titleMedium,
                        color = if (storeBanner == null) MaterialTheme.colorScheme.onSurface else Color.White)
                    Text(if (storeBanner == null) "Toca para agregar una foto" else "Toca para cambiar o quitar la foto",
                        style = MaterialTheme.typography.labelMedium, color = if (storeBanner == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
                }
                Icon(Icons.Default.CameraAlt, contentDescription = "Cambiar o quitar foto de la tienda",
                    tint = if (storeBanner == null) MaterialTheme.colorScheme.onSurface else Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Tienda:", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable { newStoreSelectionName = ""; editingStoreName = true })
            Spacer(Modifier.width(5.dp))
            Text(if (l.store.isBlank()) "Sin asignar" else l.store, Modifier.clickable { newStoreSelectionName = ""; editingStoreName = true },
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ActionIconButton(Icons.Default.Edit, "Cambiar tienda", modifier = Modifier.size(40.dp),
                onClick = { newStoreSelectionName = ""; editingStoreName = true })
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sugerencias", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            ActionIconButton(if (showSuggestions) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                if (showSuggestions) "Ocultar sugerencias" else "Mostrar sugerencias", onClick = {
                val visible = !showSuggestions
                showSuggestions = visible
                Store.updateList(l.id) { it.copy(suggestionsVisible = visible) }
            })
        }
        if (showSuggestions && visibleSuggestions.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().height(82.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(visibleSuggestions, key = { it.trim().lowercase() }) { suggestion ->
                    val suggestedProduct = remember(l, suggestion, allLists) {
                        Store.suggestedProduct(l, suggestion)
                    }
                    val suggestionImage = remember(suggestedProduct.photo) {
                        suggestedProduct.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                    }
                    Box(Modifier.width(104.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable {
                        newItemName = suggestion
                        newItemPrice = suggestedProduct.price.takeIf { it > 0.0 }?.toString().orEmpty()
                        newItemPhoto = suggestedProduct.photo
                        newItemPhotoSource = suggestedProduct.photoSource
                        showNewItemPhotoOptions = false
                        showAddItemDialog = true
                    }.background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (suggestionImage != null) Image(suggestionImage, "Foto de $suggestion", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (suggestionImage == null) 0f else PHOTO_BACKGROUND_DARKENING)))
                        Text(suggestion, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp),
                            color = if (suggestionImage == null) MaterialTheme.colorScheme.onSurface else Color.White,
                            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = { dismissedSuggestions = dismissedSuggestions + suggestion.trim().lowercase() },
                            modifier = Modifier.align(Alignment.TopEnd).size(26.dp).padding(2.dp)
                                .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar sugerencia $suggestion",
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        if (selectedItemIds.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    selectedItemIds = if (selectedItemIds.size == l.items.size) emptySet() else l.items.map { it.id }.toSet()
                }) { Text(if (selectedItemIds.size == l.items.size) "Deseleccionar todas" else "Seleccionar todas") }
                TextButton(onClick = { confirmDeleteSelectedItems = true }) { Text("Borrar (${selectedItemIds.size})") }
              }
              TextButton(onClick = { selectedItemIds = emptySet() }) { Text("Cancelar selección") }
            }
        } else if (isReorderingItems) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    sortOrder = sortBeforeReorder
                    isReorderingItems = false
                    reorderItemIds = emptyList()
                }) { Text("Cancelar") }
                Button(onClick = {
                    Store.updateList(l.id) { current ->
                        val byId = current.items.associateBy { it.id }
                        val ordered = reorderItemIds.mapNotNull { byId[it] } + current.items.filter { it.id !in reorderItemIds }
                        current.copy(items = ordered.mapIndexed { index, item -> item.copy(order = index + 1) }, itemSortOrder = ItemSortOrder.MANUAL)
                    }
                    sortOrder = ItemSortOrder.MANUAL
                    isReorderingItems = false
                    reorderItemIds = emptyList()
                }) { Text("Guardar orden") }
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = null)
                    Text(when (sortOrder) {
                        ItemSortOrder.MANUAL -> "Manual"
                        ItemSortOrder.NAME_ASC -> "A → Z"
                        ItemSortOrder.NAME_DESC -> "Z → A"
                        ItemSortOrder.PRICE_ASC -> "Precio ↑"
                        ItemSortOrder.PRICE_DESC -> "Precio ↓"
                        ItemSortOrder.SUGGESTED -> "Sugerido"
                    })
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf(
                        ItemSortOrder.MANUAL to "Orden manual",
                        ItemSortOrder.NAME_ASC to "Nombre A → Z",
                        ItemSortOrder.NAME_DESC to "Nombre Z → A",
                        ItemSortOrder.PRICE_ASC to "Precio menor a mayor",
                        ItemSortOrder.PRICE_DESC to "Precio mayor a menor",
                        ItemSortOrder.SUGGESTED to "Orden sugerido"
                    ).forEach { (mode, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            sortOrder = mode
                            Store.updateList(l.id) { it.copy(itemSortOrder = mode) }
                            showSortMenu = false
                        })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                val nextMode = (itemLayout + 1) % 3
                itemLayout = nextMode
                Store.updateList(l.id) { it.copy(viewMode = nextMode) }
            }) {
                Text(when (itemLayout) { 0 -> "Lista"; 1 -> "Cuadrícula"; else -> "Lista amplia" })
            }
            ActionIconButton(Icons.Default.SwapVert, "Ordenar manualmente", onClick = {
                sortBeforeReorder = sortOrder
                reorderItemIds = displayedItems.map { it.id }
                sortOrder = ItemSortOrder.MANUAL
                isReorderingItems = true
            })
        }
        val reorderHandle: (Item) -> Modifier = { item ->
            Modifier.pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { itemDragCarry = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        itemDragCarry += amount.y
                        while (itemDragCarry >= itemDragStepPx || itemDragCarry <= -itemDragStepPx) {
                            val direction = if (itemDragCarry > 0f) 1 else -1
                            val from = reorderItemIds.indexOf(item.id)
                            val to = from + direction
                            if (from >= 0 && to in reorderItemIds.indices) {
                                reorderItemIds = reorderItemIds.toMutableList().apply { add(to, removeAt(from)) }
                                itemDragCarry -= direction * itemDragStepPx
                            } else itemDragCarry = 0f
                        }
                    },
                    onDragEnd = { itemDragCarry = 0f },
                    onDragCancel = { itemDragCarry = 0f }
                )
            }
        }
        val selectionModifier: (Item) -> Modifier = { item ->
            Modifier.combinedClickable(
                onClick = { if (selectedItemIds.isNotEmpty()) toggleItemSelection(item.id) },
                onLongClick = { toggleItemSelection(item.id) }
            )
        }
        val total = l.items.sumOf { it.price }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (isReorderingItems) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 152.dp)) {
                items(displayedItems, key = { it.id }) { item ->
                    ItemReorderRow(item, reorderHandle(item), Modifier.animateItemPlacement())
                }
            }
        } else if (itemLayout == 1) {
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 152.dp)) {
                gridItems(displayedItems, key = { it.id }) { item ->
                    val img = remember(item.photo) { if (item.photo.isNotBlank()) BitmapFactory.decodeFile(item.photo)?.asImageBitmap() else null }
                    val selected = item.id in selectedItemIds
                    val selectionBorder by animateDpAsState(if (selected) 3.dp else 0.dp, label = "item-selection-border")
                    val selectionSurface by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        label = "item-grid-surface"
                    )
                    Box(selectionModifier(item).fillMaxWidth().padding(4.dp).height(190.dp).clip(RoundedCornerShape(12.dp))
                        .shadow(3.dp, RoundedCornerShape(12.dp)).animateContentSize().border(selectionBorder, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .background(selectionSurface)) {
                        if (img != null) Image(img, "Foto de ${item.name}", Modifier.matchParentSize().then(selectionAwareClick { photoViewerItemId = item.id }), contentScale = ContentScale.Crop)
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (img == null) 0f else PHOTO_BACKGROUND_DARKENING)))
                        Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                            Text(item.name, color = if (img != null) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = selectionAwareClick { editingItem = item.id; editingField = "name"; editText = item.name })
                            Text("${if (item.photo.isBlank()) "Sin foto · " else ""}$${"%.2f".format(item.price)}", color = if (img != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = selectionAwareClick { editingItem = item.id; editingField = "price"; editText = item.price.toString() })
                        }
                        if (selectedItemIds.isNotEmpty()) Icon(
                            if (item.id in selectedItemIds) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            if (item.id in selectedItemIds) "Seleccionado" else "No seleccionado",
                            Modifier.align(Alignment.TopStart).padding(8.dp),
                            tint = if (item.id in selectedItemIds) Color.White else Color.LightGray
                        )
                        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.Center) {
                            if (selectedItemIds.isEmpty()) Surface(color = Color.Black.copy(alpha = 0.82f), shape = RoundedCornerShape(24.dp)) {
                                ItemActionButtons(item, onImage = true, onPrice = { priceFor = item.id; priceText = if (item.price == 0.0) "" else item.price.toString() },
                                    onPhoto = { photoOptionsFor = item.id }, onDelete = { itemToDelete = item.id })
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 152.dp)) {
                items(displayedItems, key = { it.id }) { item ->
                    val img = remember(item.photo) { if (item.photo.isNotBlank()) BitmapFactory.decodeFile(item.photo)?.asImageBitmap() else null }
                    val selected = item.id in selectedItemIds
                    val selectionBorder by animateDpAsState(if (selected) 3.dp else 0.dp, label = "item-selection-border")
                    val listSurface by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        label = "item-list-surface"
                    )
                    if (itemLayout == 0) {
                        Row(selectionModifier(item).fillMaxWidth().padding(4.dp).animateContentSize()
                            .background(if (selected) listSurface else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
                            if (img != null) Box(Modifier.size(40.dp).padding(end = 8.dp).clip(RoundedCornerShape(5.dp))
                                .then(selectionAwareClick { photoViewerItemId = item.id })) {
                                Image(img, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
                            }
                            else Text("Sin foto", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(item.name, Modifier.weight(1f).then(selectionAwareClick { editingItem = item.id; editingField = "name"; editText = item.name }))
                            if (item.price > 0) Text("$${"%.2f".format(item.price)}", Modifier.then(selectionAwareClick { editingItem = item.id; editingField = "price"; editText = item.price.toString() }).padding(horizontal = 4.dp))
                            if (selectedItemIds.isNotEmpty()) Icon(
                                if (item.id in selectedItemIds) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                if (item.id in selectedItemIds) "Seleccionado" else "No seleccionado",
                                tint = if (item.id in selectedItemIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            else {
                                ItemActionButtons(item, onPrice = { priceFor = item.id; priceText = if (item.price == 0.0) "" else item.price.toString() },
                                    onPhoto = { photoOptionsFor = item.id }, onDelete = { itemToDelete = item.id })
                            }
                        }
                    } else {
                        Row(selectionModifier(item).fillMaxWidth().padding(6.dp).shadow(2.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).animateContentSize()
                            .border(selectionBorder, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .background(listSurface), verticalAlignment = Alignment.CenterVertically) {
                            if (img != null) Box(Modifier.size(104.dp).clip(RoundedCornerShape(10.dp))
                                .then(selectionAwareClick { photoViewerItemId = item.id })) {
                                Image(img, "Foto de ${item.name}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
                            }
                            else Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) { Text("Sin foto", style = MaterialTheme.typography.labelSmall) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = selectionAwareClick { editingItem = item.id; editingField = "name"; editText = item.name })
                                Text("$${"%.2f".format(item.price)}", modifier = selectionAwareClick { editingItem = item.id; editingField = "price"; editText = item.price.toString() })
                                if (selectedItemIds.isNotEmpty()) Icon(
                                    if (item.id in selectedItemIds) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    if (item.id in selectedItemIds) "Seleccionado" else "No seleccionado",
                                    tint = if (item.id in selectedItemIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                else Row(verticalAlignment = Alignment.CenterVertically) {
                                    ItemActionButtons(item, onPrice = { priceFor = item.id; priceText = if (item.price == 0.0) "" else item.price.toString() },
                                        onPhoto = { photoOptionsFor = item.id }, onDelete = { itemToDelete = item.id })
                                }
                            }
                        }
                    }
                }
            }
        }
            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FloatingActionButton(
                        onClick = {
                            newItemName = ""; newItemPrice = ""; newItemPhoto = ""; newItemPhotoSource = ""
                            showNewItemPhotoOptions = false
                            showAddItemDialog = true
                        },
                        modifier = Modifier.size(64.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) { Icon(Icons.Default.Add, "Nuevo objeto", modifier = Modifier.size(36.dp)) }
                    FloatingActionButton(
                        onClick = { showVoiceMode = true },
                        modifier = Modifier.size(52.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) { Icon(Icons.Default.Mic, "Agregar por voz") }
                }
                Surface(
                    modifier = Modifier.clickable { totalExpanded = !totalExpanded },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    shadowElevation = 8.dp
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AttachMoney, contentDescription = if (totalExpanded) "Ocultar total" else "Mostrar total")
                        if (totalExpanded) Text("Total: $${"%.2f".format(total)}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    val pendingItemDelete = l.items.find { it.id == itemToDelete }
    if (pendingItemDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Eliminar objeto") },
            text = { Text("¿Seguro que deseas eliminar «${pendingItemDelete.name}»?") },
            confirmButton = { TextButton(onClick = {
                Store.updateList(l.id) { s -> s.copy(items = s.items.filterNot { it.id == pendingItemDelete.id }) }
                selectedItemIds = selectedItemIds - pendingItemDelete.id
                itemToDelete = null
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancelar") } }
        )
    }
    if (confirmDeleteSelectedItems) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelectedItems = false },
            title = { Text("Eliminar objetos seleccionados") },
            text = { Text("¿Seguro que deseas eliminar ${selectedItemIds.size} objetos?") },
            confirmButton = { TextButton(onClick = {
                Store.updateList(l.id) { s -> s.copy(items = s.items.filterNot { it.id in selectedItemIds }) }
                selectedItemIds = emptySet()
                confirmDeleteSelectedItems = false
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDeleteSelectedItems = false }) { Text("Cancelar") } }
        )
    }

    if (showVoiceMode) {
        AlertDialog(
            onDismissRequest = { showVoiceMode = false },
            title = { Text("Agregar por voz") },
            text = { Text("¿Vas a dictar un artículo o varios?") },
            confirmButton = { TextButton(onClick = {
                showVoiceMode = false
                speech.launch(android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX"))
            }) { Text("Un artículo") } },
            dismissButton = { Row {
                TextButton(onClick = { showVoiceMode = false; requestMultiRecognition() }) { Text("Varios artículos") }
                TextButton(onClick = { showVoiceMode = false }) { Text("Cancelar") }
            } }
        )
    }

    if (listeningMulti) {
        AlertDialog(
            onDismissRequest = { multiSession?.cancel(); multiSession = null; listeningMulti = false },
            title = { Text("Escuchando varios artículos") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SpeechAudioIndicator(voiceAudioLevel)
                    Text("Di cada artículo con una pausa. Pulsa Agregar artículos cuando termines.")
                    val recognizedText = recognizedVoiceItems.flatMap(Store::parseMultipleSpoken).joinToString(", ") { it.first }
                    Text(recognizedText.ifBlank { "Los artículos reconocidos aparecerán aquí" }, Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = { multiSession?.finish(); multiSession = null; listeningMulti = false }) { Text("Agregar artículos") } },
            dismissButton = { TextButton(onClick = { multiSession?.cancel(); multiSession = null; listeningMulti = false }) { Text("Cancelar") } }
        )
    }

    if (showVoiceDetailsQuestion) {
        AlertDialog(
            onDismissRequest = { showVoiceDetailsQuestion = false },
            title = { Text("Detalles de los artículos") },
            text = { Text("¿Deseas agregar detalles a los objetos incluidos?") },
            confirmButton = { TextButton(onClick = {
                showVoiceDetailsQuestion = false
                loadVoiceDetail(0)
                showVoiceDetails = true
            }) { Text("Sí") } },
            dismissButton = { TextButton(onClick = { showVoiceDetailsQuestion = false }) { Text("No") } }
        )
    }

    if (showAddItemDialog) {
        val newPhotoPreview = remember(newItemPhoto) { if (newItemPhoto.isNotBlank()) BitmapFactory.decodeFile(newItemPhoto)?.asImageBitmap() else null }
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Nuevo objeto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newItemName, { newItemName = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newItemPrice, { newItemPrice = it }, label = { Text("Precio") }, prefix = { Text("$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (newPhotoPreview != null) Image(newPhotoPreview, "Foto del objeto", Modifier.size(56.dp).clickable { showNewItemPhotoPreview = true }, contentScale = ContentScale.Crop)
                        else Text("Foto: Sin foto", color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            showNewItemPhotoOptions = !showNewItemPhotoOptions
                        }) { Text(if (showNewItemPhotoOptions) "Ocultar opciones" else if (newItemPhoto.isBlank()) "Agregar foto" else "Cambiar foto") }
                    }
                    if (showNewItemPhotoOptions) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { photoFor = NEW_ITEM_PHOTO_ID; cam.launch(null) }) { Text("Cámara") }
                                TextButton(onClick = { chooseFromInternet(NEW_ITEM_PHOTO_ID, newItemName) }) { Text("Internet") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { photoFor = NEW_ITEM_PHOTO_ID; gallery.launch("image/*") }) { Text("Galería") }
                                TextButton(onClick = { newItemPhoto = ""; newItemPhotoSource = ""; showNewItemPhotoOptions = false }) { Text("Sin foto") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { saveNewItem() }) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { showAddItemDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showNewItemPhotoPreview) {
        Dialog(onDismissRequest = { showNewItemPhotoPreview = false }) {
            val preview = remember(newItemPhoto) { if (newItemPhoto.isNotBlank()) BitmapFactory.decodeFile(newItemPhoto)?.asImageBitmap() else null }
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preview != null) Image(preview, "Foto de $newItemName", Modifier.fillMaxWidth().heightIn(max = 440.dp).clickable { showNewItemPhotoPreview = false }, contentScale = ContentScale.Fit)
                    TextButton(onClick = { showNewItemPhotoPreview = false }) { Text("Cerrar") }
                }
            }
        }
    }

    if (isSearchingPhoto) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Buscando foto") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Buscando una imagen para ${photoSearchRequest?.second.orEmpty()}…")
                }
            },
            confirmButton = {}
        )
    }

    if (photoSearchFailed && !isSearchingPhoto) {
        AlertDialog(
            onDismissRequest = { photoSearchFailed = false },
            title = { Text("No se pudo buscar la foto") },
            text = { Text("Conéctate a internet y vuelve a intentarlo, o continúa sin foto. El formulario seguirá abierto.") },
            confirmButton = { TextButton(onClick = {
                val request = photoSearchRequest
                if (request != null) chooseFromInternet(request.first, request.second, request.third)
                else photoSearchFailed = false
            }) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = {
                photoSearchFailed = false
                photoSearchRequest = null
                Toast.makeText(ctx, "Puedes continuar sin conexión", Toast.LENGTH_SHORT).show()
            }) { Text("Continuar offline") } }
        )
    }

    val voiceDetailItem = l.items.find { it.id == voiceDetailItemIds.getOrNull(voiceDetailIndex) }
    if (showVoiceDetails && voiceDetailItem != null) {
        val detailPhoto = remember(voiceDetailItem.photo) { if (voiceDetailItem.photo.isNotBlank()) BitmapFactory.decodeFile(voiceDetailItem.photo)?.asImageBitmap() else null }
        Dialog(onDismissRequest = { saveVoiceDetail(); showVoiceDetails = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(max = 420.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Artículo ${voiceDetailIndex + 1} de ${voiceDetailItemIds.size}", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(voiceDetailName, { voiceDetailName = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(voiceDetailPrice, { voiceDetailPrice = it }, label = { Text("Precio") }, prefix = { Text("$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (detailPhoto != null) Image(detailPhoto, "Foto de ${voiceDetailItem.name}", Modifier.size(56.dp), contentScale = ContentScale.Crop)
                        else Text("Sin foto", color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            saveVoiceDetail()
                            showVoiceDetails = false
                            resumeVoiceDetailsAfterPhoto = true
                            photoOptionsFor = voiceDetailItem.id
                        }) { Text(if (voiceDetailItem.photo.isBlank()) "Agregar foto" else "Cambiar foto") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = voiceDetailIndex > 0, onClick = { saveVoiceDetail(); loadVoiceDetail(voiceDetailIndex - 1) }) { Text("Anterior") }
                        TextButton(onClick = {
                            saveVoiceDetail()
                            if (voiceDetailIndex == voiceDetailItemIds.lastIndex) showVoiceDetails = false
                            else loadVoiceDetail(voiceDetailIndex + 1)
                        }) { Text(if (voiceDetailIndex == voiceDetailItemIds.lastIndex) "Finalizar" else "Siguiente") }
                        TextButton(onClick = { saveVoiceDetail(); showVoiceDetails = false }) { Text("Salir") }
                    }
                }
            }
        }
    }

    if (photoQueue.isNotEmpty() && photoFor == null) {
        val (name, price) = photoQueue.first()
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Foto para $name") },
            text = { Text("¿Cómo quieres agregar la foto?") },
            confirmButton = { TextButton(onClick = {
                photoQueue = photoQueue.drop(1)
                val item = addItem(l.id, name, price)
                photoFor = item.id
                cam.launch(null)
            }) { Text("Tomar foto") } },
            dismissButton = { Row {
                TextButton(onClick = {
                    photoQueue = photoQueue.drop(1)
                    val item = addItem(l.id, name, price)
                    chooseFromInternet(item.id, name)
                }) { Text("Buscar en internet") }
                TextButton(onClick = {
                    photoQueue = photoQueue.drop(1)
                    val item = addItem(l.id, name, price)
                    photoFor = item.id
                    gallery.launch("image/*")
                }) { Text("Galería") }
                TextButton(onClick = { photoQueue = photoQueue.drop(1); addItem(l.id, name, price) }) { Text("Sin foto") }
            } }
        )
    }

    val itemForPhotoOptions = l.items.find { it.id == photoOptionsFor }
    if (itemForPhotoOptions != null) {
        AlertDialog(
            onDismissRequest = { photoOptionsFor = null; resumePendingEditorIfNeeded() },
            title = { Text("Foto para ${itemForPhotoOptions.name}") },
            text = { Text("Elige cómo agregar o cambiar la foto") },
            confirmButton = { TextButton(onClick = {
                photoOptionsFor = null
                photoFor = itemForPhotoOptions.id
                cam.launch(null)
            }) { Text("Cámara") } },
            dismissButton = { Row {
                TextButton(onClick = {
                    photoOptionsFor = null
                    val item = itemForPhotoOptions
                    chooseFromInternet(item.id, item.name, item.photoSource)
                }) { Text("Internet") }
                TextButton(onClick = {
                    photoOptionsFor = null
                    photoFor = itemForPhotoOptions.id
                    gallery.launch("image/*")
                }) { Text("Galería") }
                TextButton(onClick = {
                    Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == itemForPhotoOptions.id) it.copy(photo = "", photoSource = "") else it }) }
                    photoOptionsFor = null
                    resumePendingEditorIfNeeded()
                }) { Text("Sin foto") }
            } }
        )
    }

    if (storePhotoOptions) {
        AlertDialog(
            onDismissRequest = { storePhotoOptions = false },
            title = { Text("Foto de la tienda") },
            text = { Text("Elige cómo agregar o cambiar la foto") },
            confirmButton = { TextButton(onClick = {
                storePhotoOptions = false
                photoFor = STORE_PHOTO_ID
                cam.launch(null)
            }) { Text("Cámara") } },
            dismissButton = { Row {
                TextButton(onClick = {
                    storePhotoOptions = false
                    chooseFromInternet(STORE_PHOTO_ID, l.store.ifBlank { l.name }, l.storePhotoSource)
                }) { Text("Internet") }
                TextButton(onClick = {
                    storePhotoOptions = false
                    photoFor = STORE_PHOTO_ID
                    gallery.launch("image/*")
                }) { Text("Galería") }
                TextButton(onClick = {
                    updatePhoto(STORE_PHOTO_ID, "", "")
                    storePhotoOptions = false
                }) { Text("Sin foto") }
            } }
        )
    }

    if (priceFor != null) {
        AlertDialog(
            onDismissRequest = { priceFor = null },
            title = { Text("Precio del objeto") },
            text = { OutlinedTextField(priceText, { priceText = it }, label = { Text("Precio") }, prefix = { Text("$ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val id = priceFor
                val price = priceText.replace(',', '.').toDoubleOrNull()
                if (id != null && price != null && price.isFinite() && price >= 0.0) {
                    Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(price = price) else it }) }
                    priceFor = null
                } else if (priceText.isBlank() && id != null) {
                    Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(price = 0.0) else it }) }
                    priceFor = null
                } else Toast.makeText(ctx, "Ingrese un precio válido", Toast.LENGTH_SHORT).show()
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { priceFor = null }) { Text("Cancelar") } }
        )
    }

    if (editingStoreName) {
        AlertDialog(
            onDismissRequest = { editingStoreName = false },
            title = { Text("Seleccionar tienda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige una tienda existente o agrega una nueva.")
                    LazyRow(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(catalogShops, key = { it.id }) { shop ->
                            val image = remember(shop.photo) { shop.photo.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                            Box(Modifier.width(112.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    Store.updateList(l.id) { it.copy(store = shop.name, storePhoto = shop.photo, storePhotoSource = shop.photoSource) }
                                    editingStoreName = false
                                }.background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (image != null) Image(image, "Foto de ${shop.name}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                if (image != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = PHOTO_BACKGROUND_DARKENING)))
                                Text(shop.name, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp),
                                    color = if (image != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(newStoreSelectionName, { newStoreSelectionName = it }, Modifier.weight(1f),
                            label = { Text("Agregar tienda nueva") }, singleLine = true)
                        ActionIconButton(Icons.Default.Add, "Agregar y seleccionar tienda", onClick = {
                            val name = newStoreSelectionName.trim()
                            if (name.isBlank()) Toast.makeText(ctx, "Porfavor ingrese un nombre de la tienda", Toast.LENGTH_SHORT).show()
                            else {
                                val shop = catalogShops.firstOrNull { it.name.equals(name, ignoreCase = true) }
                                    ?: StoreShop(name = name).also { added -> Store.updateShops { it + added } }
                                Store.updateList(l.id) { it.copy(store = shop.name, storePhoto = shop.photo, storePhotoSource = shop.photoSource) }
                                newStoreSelectionName = ""
                                editingStoreName = false
                            }
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { Store.updateList(l.id) { it.copy(store = "", storePhoto = "", storePhotoSource = "") }; editingStoreName = false }) { Text("Sin tienda") } },
            dismissButton = { TextButton(onClick = { editingStoreName = false }) { Text("Cancelar") } }
        )
    }

    if (editingListName) {
        AlertDialog(
            onDismissRequest = { editingListName = false },
            title = { Text("Nombre de la lista") },
            text = { OutlinedTextField(listNameDraft, { listNameDraft = it }, label = { Text("Lista") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                if (listNameDraft.isNotBlank()) {
                    Store.updateList(l.id) { it.copy(name = listNameDraft.trim()) }
                    editingListName = false
                } else Toast.makeText(ctx, "Porfavor ingrese un nombre de la lista", Toast.LENGTH_SHORT).show()
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editingListName = false }) { Text("Cancelar") } }
        )
    }

    val itemBeingEdited = l.items.find { it.id == editingItem }
    if (itemBeingEdited != null) {
        val isName = editingField == "name"
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(if (isName) "Editar nombre" else "Editar precio") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = { Text(if (isName) "Nombre" else "Precio") },
                    prefix = { if (!isName) Text("$ ") },
                    keyboardOptions = if (isName) KeyboardOptions.Default else KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = {
                val id = itemBeingEdited.id
                if (isName) {
                    if (editText.isNotBlank()) {
                        Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(name = editText.trim()) else it }) }
                        editingItem = null
                    } else Toast.makeText(ctx, "Porfavor ingrese un nombre para el objeto", Toast.LENGTH_SHORT).show()
                } else {
                    val price = editText.replace(',', '.').toDoubleOrNull()
                    if (editText.isBlank()) {
                        Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(price = 0.0) else it }) }
                        editingItem = null
                    } else if (price != null && price.isFinite() && price >= 0.0) {
                        Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(price = price) else it }) }
                        editingItem = null
                    } else Toast.makeText(ctx, "Ingrese un precio válido", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("Cancelar") } }
        )
    }

    photoViewerItemId?.let { itemId ->
        val viewerItems = l.items.sortedBy { it.order }
        val viewerIndex = viewerItems.indexOfFirst { it.id == itemId }
        val item = viewerItems.getOrNull(viewerIndex)
        if (item != null) {
            val preview = remember(item.photo) { if (item.photo.isNotBlank()) BitmapFactory.decodeFile(item.photo)?.asImageBitmap() else null }
            val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
            fun moveViewer(delta: Int) {
                if (viewerItems.size > 1) {
                    val nextIndex = (viewerIndex + delta + viewerItems.size) % viewerItems.size
                    photoViewerItemId = viewerItems[nextIndex].id
                }
            }
            Dialog(onDismissRequest = { photoViewerItemId = null }) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.widthIn(max = 600.dp).heightIn(max = 700.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Box(
                            Modifier.fillMaxWidth().height(400.dp)
                                .pointerInput(viewerIndex, viewerItems.size) {
                                    var dragDistance = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, amount -> change.consume(); dragDistance += amount },
                                        onDragEnd = {
                                            if (abs(dragDistance) >= swipeThreshold) moveViewer(if (dragDistance < 0f) 1 else -1)
                                            dragDistance = 0f
                                        },
                                        onDragCancel = { dragDistance = 0f }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (preview != null) {
                                Image(preview, contentDescription = "Foto de ${item.name}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Sin foto", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (viewerItems.size > 1) {
                                ActionIconButton(Icons.Default.ChevronLeft, "Producto anterior", tint = Color.White,
                                    modifier = Modifier.align(Alignment.CenterStart).background(Color.Black.copy(alpha = 0.72f), CircleShape), onClick = { moveViewer(-1) })
                                ActionIconButton(Icons.Default.ChevronRight, "Producto siguiente", tint = Color.White,
                                    modifier = Modifier.align(Alignment.CenterEnd).background(Color.Black.copy(alpha = 0.72f), CircleShape), onClick = { moveViewer(1) })
                            }
                        }
                        Text("${viewerIndex + 1} de ${viewerItems.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                Store.updateList(l.id) { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(photo = "", photoSource = "") else it }) }
                            }) { Text("Quitar foto") }
                            TextButton(onClick = { chooseFromInternet(item.id, item.name, item.photoSource) }) {
                                Text(if (item.photo.isBlank()) "Buscar foto" else "Buscar otra")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { photoFor = item.id; gallery.launch("image/*") }) { Text("Galería") }
                            TextButton(onClick = { photoFor = item.id; cam.launch(null) }) { Text("Cámara") }
                        }
                        TextButton(onClick = { photoViewerItemId = null }) { Text("Cerrar") }
                    }
                }
            }
        } else {
            photoViewerItemId = null
        }
    }

    if (showStorePhoto) {
        val storePreview = remember(l.storePhoto) { if (l.storePhoto.isNotBlank()) BitmapFactory.decodeFile(l.storePhoto)?.asImageBitmap() else null }
        Dialog(onDismissRequest = { showStorePhoto = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.widthIn(max = 600.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Foto de ${l.store.ifBlank { "la tienda" }}", style = MaterialTheme.typography.titleMedium)
                    if (storePreview != null) {
                        Image(storePreview, "Foto de la tienda", Modifier.fillMaxWidth().height(400.dp), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                            Text("Sin foto", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { updatePhoto(STORE_PHOTO_ID, "", "") }) { Text("Quitar foto") }
                        TextButton(onClick = { chooseFromInternet(STORE_PHOTO_ID, l.store.ifBlank { l.name }, l.storePhotoSource) }) {
                            Text(if (l.storePhoto.isBlank()) "Buscar foto" else "Buscar otra")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { photoFor = STORE_PHOTO_ID; gallery.launch("image/*") }) { Text("Galería") }
                        TextButton(onClick = { photoFor = STORE_PHOTO_ID; cam.launch(null) }) { Text("Cámara") }
                    }
                    TextButton(onClick = { showStorePhoto = false }) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
private fun ItemActionButtons(item: Item, onImage: Boolean = false, onPrice: () -> Unit, onPhoto: () -> Unit, onDelete: () -> Unit) {
    val iconTint = if (onImage) Color.White else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        ActionIconButton(Icons.Default.AttachMoney, "Editar precio", tint = iconTint, onClick = onPrice)
        ActionIconButton(Icons.Default.CameraAlt, "Cambiar foto", tint = iconTint, onClick = onPhoto)
        ActionIconButton(Icons.Default.Delete, "Eliminar objeto", tint = iconTint, onClick = onDelete)
    }
}

private fun addItem(listId: String, name: String, price: Double): Item {
    val item = Item(name = name, price = price)
    Store.updateList(listId) { s -> s.copy(items = s.items + item.copy(order = (s.items.maxOfOrNull { it.order } ?: 0) + 1)) }
    return item
}

private fun downloadRelevantPhoto(filesDir: File, term: String, excludeSource: String = ""): Pair<String, String>? {
    val encodedTerm = URLEncoder.encode(term, "UTF-8")
    val userAgent = "MandadoAndroid/1.0 (shopping-list app)"

    // Openverse covers many categories and supplies relevance-ranked image results.
    val openverseImage = runCatching {
        val response = readJson("https://api.openverse.org/v1/images/?q=$encodedTerm&page_size=20", userAgent)
        val results = response?.optJSONArray("results") ?: return@runCatching null
        for (index in 0 until results.length()) {
            val image = results.optJSONObject(index) ?: continue
            val imageUrl = image.optString("thumbnail").ifBlank { image.optString("url") }
            if (imageUrl.isNotBlank() && imageUrl != excludeSource) return@runCatching imageUrl
        }
        null
    }.getOrNull()

    // Open Food Facts is useful when the query matches a packaged product or brand exactly.
    runCatching {
        val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encodedTerm&search_simple=1&action=process&json=1&page_size=30&fields=product_name,brands,image_front_url,image_url"
        val products = readJson(url, userAgent)?.optJSONArray("products") ?: return@runCatching null
        val terms = term.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()
        val candidates = (0 until products.length()).mapNotNull { index ->
            val product = products.optJSONObject(index) ?: return@mapNotNull null
            val name = product.optString("product_name")
            val brands = product.optString("brands")
            val searchable = "$name $brands".lowercase()
            val matched = terms.count { it in searchable }
            val matchedInName = terms.count { it in name.lowercase() }
            val imageUrl = product.optString("image_front_url").ifBlank { product.optString("image_url") }
            if (matched == terms.size && imageUrl.isNotBlank() && imageUrl != excludeSource) Triple(matched * 10 + matchedInName, name, imageUrl) else null
        }.sortedByDescending { it.first }
        candidates.firstNotNullOfOrNull { candidate ->
            saveRemoteImage(filesDir, candidate.third, userAgent)?.let { it to candidate.third }
        }
    }.getOrNull()?.let { return it }

    openverseImage?.let { imageUrl ->
        saveRemoteImage(filesDir, imageUrl, userAgent)?.let { return it to imageUrl }
    }

    // Wikimedia Commons is a broad fallback; keep its relevance order instead of shuffling it.
    return runCatching {
        val apiUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedTerm&gsrnamespace=6&gsrlimit=20&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json"
        val pages = readJson(apiUrl, userAgent)?.optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching null
        val keys = pages.keys()
        while (keys.hasNext()) {
            val info = pages.getJSONObject(keys.next()).optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val imageUrl = info.optString("thumburl").ifBlank { info.optString("url") }
            if (imageUrl.isNotBlank() && imageUrl != excludeSource) {
                val filePath = saveRemoteImage(filesDir, imageUrl, userAgent)
                if (filePath != null) return@runCatching filePath to imageUrl
            }
        }
        null
    }.getOrNull()
}

private fun readJson(address: String, userAgent: String): JSONObject? {
    val connection = URL(address).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", userAgent)
        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally {
        connection.disconnect()
    }
}

private fun saveRemoteImage(filesDir: File, imageUrl: String, userAgent: String): String? {
    val connection = URL(imageUrl).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", userAgent)
        val file = File(filesDir, "${UUID.randomUUID()}.image")
        connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}
