package com.carlosmvp.mandado

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class Item(val id: String = UUID.randomUUID().toString(), val name: String, val price: Double = 0.0,
                val photo: String = "", val order: Int = 0, val checked: Boolean = false, val cartOrder: Int = 0,
                val photoSource: String = "", val photoUpdatedAt: Long = 0L)
data class ShopList(val id: String = UUID.randomUUID().toString(), val name: String, val store: String = "",
                    val items: List<Item> = emptyList(), val createdAt: Long = System.currentTimeMillis(), val active: Boolean = true,
                    val storePhoto: String = "", val storePhotoSource: String = "", val viewMode: Int = 1,
                    val suggestionsVisible: Boolean = true, val itemSortOrder: ItemSortOrder = ItemSortOrder.MANUAL,
                    val storePhotoUpdatedAt: Long = 0L)
data class StoreShop(val id: String = UUID.randomUUID().toString(), val name: String, val photo: String = "", val photoSource: String = "")
data class SuggestedProduct(val price: Double, val photo: String, val photoSource: String)

enum class ItemSortOrder { MANUAL, NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC, SUGGESTED }

fun sortItems(items: List<Item>, order: ItemSortOrder): List<Item> = when (order) {
    ItemSortOrder.MANUAL -> items.sortedBy { it.order }
    ItemSortOrder.NAME_ASC -> items.sortedWith(compareBy<Item> { it.name.lowercase() }.thenBy { it.order })
    ItemSortOrder.NAME_DESC -> items.sortedWith(compareByDescending<Item> { it.name.lowercase() }.thenBy { it.order })
    ItemSortOrder.PRICE_ASC -> items.sortedWith(compareBy<Item> { it.price }.thenBy { it.order })
    ItemSortOrder.PRICE_DESC -> items.sortedWith(compareByDescending<Item> { it.price }.thenBy { it.order })
    ItemSortOrder.SUGGESTED -> items.sortedWith(
        compareBy<Item> { if (it.cartOrder > 0) 0 else 1 }
            .thenBy { if (it.cartOrder > 0) it.cartOrder else it.order }
            .thenBy { it.order }
    )
}

/** Fuente única de verdad. Guarda en disco y sincroniza reloj <-> teléfono por la Data Layer (/state). */
object Store {
    val lists = MutableStateFlow<List<ShopList>>(emptyList())
    val shops = MutableStateFlow<List<StoreShop>>(emptyList())
    val mainListsViewMode = MutableStateFlow(0)
    private lateinit var ctx: Context
    private val prefs get() = ctx.getSharedPreferences("mandado", 0)
    private data class EncodedPhoto(val signature: String, val hash: String, val bytes: ByteArray)
    private val encodedPhotos = mutableMapOf<String, EncodedPhoto>()
    private val stateLock = Any()
    private val deletedListTimes = mutableMapOf<String, Long>()
    private val deletedItemTimes = mutableMapOf<String, Long>()
    private var syncOrigin = ""
    private val photoRefPrefix = "carrito-image://"

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        syncOrigin = prefs.getString("syncOrigin", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("syncOrigin", it).apply()
        }
        synchronized(deletedListTimes) {
            deletedListTimes.clear()
            deletedListTimes.putAll(parseDeletedListTimes(prefs.getString("deletedLists", "{}")!!))
        }
        synchronized(deletedItemTimes) {
            deletedItemTimes.clear()
            deletedItemTimes.putAll(parseDeletedItemTimes(prefs.getString("deletedItems", "{}")!!))
        }
        lists.value = parse(prefs.getString("json", "[]")!!)
        mainListsViewMode.value = prefs.getInt("mainListsViewMode", 0).coerceIn(0, 2)
        shops.value = parseShops(prefs.getString("shops", "[]")!!)
        if (!prefs.contains("shops")) {
            shops.value = lists.value.filter { it.store.isNotBlank() }
                .distinctBy { it.store.trim().lowercase() }
                .map { StoreShop(name = it.store.trim(), photo = it.storePhoto, photoSource = it.storePhotoSource) }
            saveShops()
        }
        val client = Wearable.getDataClient(ctx)
        val isWatch = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        client.addListener { buf ->
            buf.forEach { e ->
                if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/state")
                    receive(DataMapItem.fromDataItem(e.dataItem).dataMap)
            }
        }
        client.getDataItems(Uri.parse("wear://*/state")).addOnSuccessListener { buf ->
            try {
                buf.forEach { receive(DataMapItem.fromDataItem(it).dataMap) }
            } finally {
                buf.release()
            }
            // Older builds synchronized only phone file paths. Republish from the phone
            // after loading the current state so all existing local photos are sent as Assets.
            if (!isWatch) publishCurrentState()
        }.addOnFailureListener {
            if (!isWatch) publishCurrentState()
        }
    }

    private fun receive(data: DataMap) {
        var json = data.getString("json") ?: return
        if (data.getString("origin") == syncOrigin) return
        mergeDeletedListTimes(parseDeletedListTimes(data.getString("deletedLists") ?: "{}"))
        mergeDeletedItemTimes(parseDeletedItemTimes(data.getString("deletedItems") ?: "{}"))
        // Keep an explicit item-id -> asset-hash map alongside the payload. This
        // lets a receiver reconnect photos even if the Data Layer delivers a
        // merged/older JSON value with the Asset entries from the latest update.
        val photoMap = runCatching { JSONObject(data.getString("imageMap") ?: "{}") }.getOrDefault(JSONObject())
        Log.i("CarritoImageSync", "Incoming metadata: hasImageMap=${data.containsKey("imageMap")}, mapEntries=${photoMap.length()}, rawRefs=${Regex("carrito-image://([a-fA-F0-9]{64})").findAll(json).count()}, dataKeys=${data.keySet().joinToString()}")
        if (photoMap.length() > 0) {
            val mapped = runCatching { parse(json) }.getOrNull() ?: return
            json = toJson(mapped.map { list ->
                list.copy(
                    storePhoto = photoMap.optString("store:${list.id}").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                        ?.let { "$photoRefPrefix$it" } ?: list.storePhoto,
                    items = list.items.map { item ->
                        val hash = photoMap.optString("item:${list.id}:${item.id}")
                        item.copy(photo = hash.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                            ?.let { "$photoRefPrefix$it" } ?: item.photo)
                    }
                )
            })
        }
        val markerHashes = Regex("carrito-image://([a-fA-F0-9]{64})").findAll(json)
            .map { it.groupValues[1].lowercase() }.toSet()
        // The serialized JSON can arrive without URI markers even when the
        // matching Assets and imageMap did arrive. Treat imageMap hashes as
        // authoritative transfer requests instead of silently skipping them.
        val mappedHashes = photoMap.keys().asSequence()
            .mapNotNull { key -> photoMap.optString(key).takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.lowercase() }
            .toSet()
        val hashes = markerHashes + mappedHashes
        val missing = hashes.filter { !syncedPhotoFile(it).isFile }
        Log.i("CarritoImageSync", "Receive state: refs=${hashes.size}, missing=${missing.size}, assetKeys=${data.keySet().count { it.startsWith("image_") }}")
        if (missing.isEmpty()) {
            receiveResolved(json, hashes.associateWith { syncedPhotoFile(it).absolutePath })
            return
        }

        val resolved = mutableMapOf<String, String>()
        val remaining = AtomicInteger(missing.size)
        val handler = Handler(Looper.getMainLooper())
        fun completeOne() {
            if (remaining.decrementAndGet() == 0) {
                hashes.forEach { hash ->
                    val file = syncedPhotoFile(hash)
                    if (file.isFile) resolved[hash] = file.absolutePath
                }
                receiveResolved(json, resolved)
            }
        }
        fun fetchAsset(hash: String, asset: Asset, attempt: Int) {
            fun retry(reason: String) {
                if (attempt < 3) {
                    Log.w("CarritoImageSync", "Retry image ${hash.take(10)} attempt=${attempt + 1}: $reason")
                    handler.postDelayed({ fetchAsset(hash, asset, attempt + 1) }, 700L * (attempt + 1))
                } else {
                    Log.e("CarritoImageSync", "Image ${hash.take(10)} unavailable after retries: $reason")
                    completeOne()
                }
            }
            Wearable.getDataClient(ctx).getFdForAsset(asset)
                .addOnSuccessListener { response ->
                    val copied = try {
                        runCatching {
                            syncedPhotoFile(hash).parentFile?.mkdirs()
                            response.inputStream.use { source ->
                                FileOutputStream(syncedPhotoFile(hash)).use { output -> source.copyTo(output) }
                            }
                            syncedPhotoFile(hash).isFile && syncedPhotoFile(hash).length() > 0L
                        }.getOrDefault(false)
                    } finally {
                        response.release()
                    }
                    if (copied) {
                        Log.i("CarritoImageSync", "Saved image ${hash.take(10)} (${syncedPhotoFile(hash).length()} bytes)")
                        completeOne()
                    } else retry("asset stream was empty or could not be saved")
                }
                .addOnFailureListener { error -> retry(error.message ?: "asset request failed") }
        }
        missing.forEach { hash ->
            val asset = data.getAsset("image_$hash")
            if (asset == null) {
                Log.e("CarritoImageSync", "Missing DataMap asset image_${hash.take(10)}")
                completeOne()
            } else fetchAsset(hash, asset, 0)
        }
    }

    private fun receiveResolved(json: String, files: Map<String, String>) {
        val received = runCatching { parse(json) }.getOrNull() ?: return
        fun resolve(path: String): String {
            val hash = Regex("^carrito-image://([a-fA-F0-9]{64})$").matchEntire(path)?.groupValues?.get(1)?.lowercase()
                ?: return path
            return files[hash] ?: syncedPhotoFile(hash).takeIf { it.isFile }?.absolutePath ?: path
        }
        synchronized(stateLock) {
            val currentLists = lists.value
            val currentById = currentLists.associateBy { it.id }
            val deleted = synchronized(deletedListTimes) { deletedListTimes.toMap() }
            val deletedItems = synchronized(deletedItemTimes) { deletedItemTimes.toMap() }
            val receivedIds = received.asSequence()
                .filter { (deleted[it.id] ?: 0L) < it.createdAt }
                .map { it.id }
                .toSet()
            // Keep local lists that are missing from a stale peer snapshot. Without
            // this union, opening the phone could replace a newly saved list with an
            // older Data Layer copy that had not received it yet.
            val mergedReceived = received.filter { (deleted[it.id] ?: 0L) < it.createdAt } +
                currentLists.filter { it.id !in receivedIds && (deleted[it.id] ?: 0L) < it.createdAt }
            val localized = mergedReceived.map { list ->
                val previous = currentById[list.id]
                val incomingStorePhoto = resolve(list.storePhoto)
                val keepPreviousStorePhoto = previous != null && (
                    previous.storePhotoUpdatedAt > list.storePhotoUpdatedAt ||
                        (previous.storePhoto.isNotBlank() && File(previous.storePhoto).isFile &&
                            incomingStorePhoto.isNotBlank() && !File(incomingStorePhoto).isFile &&
                            previous.storePhotoUpdatedAt == list.storePhotoUpdatedAt)
                    )
                list.copy(
                    storePhoto = if (keepPreviousStorePhoto) previous!!.storePhoto else incomingStorePhoto.let { path ->
                        if (path.startsWith(photoRefPrefix) && previous?.storePhoto?.let { File(it).isFile } == true) previous.storePhoto else path
                    },
                    storePhotoSource = if (keepPreviousStorePhoto) previous!!.storePhotoSource else list.storePhotoSource,
                    storePhotoUpdatedAt = maxOf(list.storePhotoUpdatedAt, if (keepPreviousStorePhoto) previous!!.storePhotoUpdatedAt else 0L),
                    items = list.items.filterNot { item -> deletedItems.containsKey(deletedItemKey(list.id, item.id)) }.map { item ->
                        val path = resolve(item.photo)
                        val previousItem = previous?.items?.firstOrNull { it.id == item.id }
                        val previousPhoto = previousItem?.photo
                        val keepPreviousPhoto = previousItem != null && (
                            previousItem.photoUpdatedAt > item.photoUpdatedAt ||
                                (previousPhoto!!.isNotBlank() && File(previousPhoto).isFile && path.isNotBlank() &&
                                    !File(path).isFile && previousItem.photoUpdatedAt == item.photoUpdatedAt)
                            )
                        item.copy(
                            photo = if (keepPreviousPhoto) previousPhoto!! else if (path.startsWith(photoRefPrefix) && previousPhoto?.let { File(it).isFile } == true) previousPhoto else path,
                            photoSource = if (keepPreviousPhoto) previousItem!!.photoSource else item.photoSource,
                            photoUpdatedAt = maxOf(item.photoUpdatedAt, if (keepPreviousPhoto) previousItem!!.photoUpdatedAt else 0L)
                        )
                    }
                )
            }
            val localJson = toJson(localized)
            if (localJson == toJson(currentLists)) return
            val saved = prefs.edit().putString("json", localJson).commit()
            if (!saved) Log.e("CarritoListSync", "Could not persist merged list state")
            Log.i("CarritoListSync", "Merged state: local=${currentLists.size}, incoming=${received.size}, saved=${localized.size}, preserved=${localized.count { it.id !in receivedIds }}")
            lists.value = localized
        }
    }

    private fun syncedPhotoFile(hash: String) = File(File(ctx.filesDir, "synced-photos"), "$hash.img")

    private fun encodeLocalPhoto(path: String): EncodedPhoto? {
        val file = File(path)
        if (!file.isFile) return null
        val signature = "${file.length()}:${file.lastModified()}"
        synchronized(encodedPhotos) { encodedPhotos[path]?.takeIf { it.signature == signature }?.let { return it } }
        return runCatching {
            // Transfer the exact app-cached file bytes. Do not resize, recompress,
            // or change the image format before attaching it to the Data Layer.
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            EncodedPhoto(signature, hash, bytes).also { encoded -> synchronized(encodedPhotos) { encodedPhotos[path] = encoded } }
        }.getOrNull()
    }

    private fun syncJson(source: List<ShopList>, dataMap: DataMap): String {
        val attached = mutableSetOf<String>()
        val photoMap = JSONObject()
        fun encode(path: String): String {
            if (path.isBlank() || path.startsWith("http", ignoreCase = true)) return path
            val imageHash = Regex("^carrito-image://([a-fA-F0-9]{64})$").matchEntire(path)?.groupValues?.get(1)
            val sourcePath = imageHash?.let { syncedPhotoFile(it.lowercase()).absolutePath } ?: path
            val photo = encodeLocalPhoto(sourcePath) ?: return path
            if (attached.add(photo.hash)) dataMap.putAsset("image_${photo.hash}", Asset.createFromBytes(photo.bytes))
            return "$photoRefPrefix${photo.hash}"
        }
        val wireLists = source.map { list ->
            list.copy(
                storePhoto = encode(list.storePhoto),
                items = list.items.map { item -> item.copy(photo = encode(item.photo)) }
            )
        }
        wireLists.forEach { list ->
            Regex("^carrito-image://([a-fA-F0-9]{64})$").matchEntire(list.storePhoto)?.groupValues?.get(1)?.let {
                photoMap.put("store:${list.id}", it.lowercase())
            }
            list.items.forEach { item ->
                Regex("^carrito-image://([a-fA-F0-9]{64})$").matchEntire(item.photo)?.groupValues?.get(1)?.let {
                    photoMap.put("item:${list.id}:${item.id}", it.lowercase())
                }
            }
        }
        dataMap.putString("imageMap", photoMap.toString())
        Log.i("CarritoImageSync", "Sending state: images=${attached.size}, mapEntries=${photoMap.length()}, jsonBytes=${toJson(wireLists).toByteArray().size}")
        return toJson(wireLists)
    }

    fun update(f: (List<ShopList>) -> List<ShopList>) {
        synchronized(stateLock) {
            val previousLists = lists.value
            val previous = previousLists.associateBy { it.id }
            val now = System.currentTimeMillis()
            val changedLists = f(previousLists)
            val nextIds = changedLists.mapTo(mutableSetOf()) { it.id }
            synchronized(deletedListTimes) {
                (previous.keys - nextIds).forEach { deletedListTimes[it] = now }
                nextIds.forEach { deletedListTimes.remove(it) }
            }
            val changedById = changedLists.associateBy { it.id }
            synchronized(deletedItemTimes) {
                previousLists.forEach { oldList ->
                    val newList = changedById[oldList.id] ?: return@forEach
                    val retainedItemIds = newList.items.mapTo(mutableSetOf()) { it.id }
                    oldList.items.asSequence().map { it.id }.filterNot { it in retainedItemIds }.forEach { itemId ->
                        val key = deletedItemKey(oldList.id, itemId)
                        deletedItemTimes[key] = maxOf(now, (deletedItemTimes[key] ?: 0L) + 1L)
                    }
                }
            }
            val nextLists = changedLists.map { list ->
                val old = previous[list.id]
                list.copy(
                    storePhotoUpdatedAt = if (old == null && list.storePhoto.isNotBlank()) maxOf(list.storePhotoUpdatedAt, now)
                        else if (old != null && old.storePhoto != list.storePhoto) maxOf(now, old.storePhotoUpdatedAt + 1L)
                        else maxOf(list.storePhotoUpdatedAt, old?.storePhotoUpdatedAt ?: 0L),
                    items = list.items.map { item ->
                        val oldItem = old?.items?.firstOrNull { it.id == item.id }
                        item.copy(
                            photoUpdatedAt = if (oldItem == null && item.photo.isNotBlank()) maxOf(item.photoUpdatedAt, now)
                                else if (oldItem != null && oldItem.photo != item.photo) maxOf(now, oldItem.photoUpdatedAt + 1L)
                                else maxOf(item.photoUpdatedAt, oldItem?.photoUpdatedAt ?: 0L)
                        )
                    }
                )
            }
            val j = toJson(nextLists)
            val saved = prefs.edit()
                .putString("json", j)
                .putString("deletedLists", serializeDeletedListTimes())
                .putString("deletedItems", serializeDeletedItemTimes())
                .commit()
            if (!saved) Log.e("CarritoListSync", "Could not persist local list state")
            lists.value = nextLists
            Log.i("CarritoListSync", "Local state saved: before=${previousLists.size}, after=${nextLists.size}, removed=${previous.keys - nextIds}")
        }
        publishCurrentState()
    }

    private fun parseDeletedListTimes(json: String): Map<String, Long> = runCatching {
        val value = JSONObject(json)
        buildMap {
            val keys = value.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val deletedAt = value.optLong(id, 0L)
                if (deletedAt > 0L) put(id, deletedAt)
            }
        }
    }.getOrDefault(emptyMap())

    private fun deletedItemKey(listId: String, itemId: String) = "$listId:$itemId"

    private fun parseDeletedItemTimes(json: String): Map<String, Long> = runCatching {
        val value = JSONObject(json)
        buildMap {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val deletedAt = value.optLong(key, 0L)
                if (deletedAt > 0L) put(key, deletedAt)
            }
        }
    }.getOrDefault(emptyMap())

    private fun serializeDeletedListTimes(): String {
        val snapshot = synchronized(deletedListTimes) { deletedListTimes.toMap() }
        return JSONObject().apply { snapshot.forEach { (id, deletedAt) -> put(id, deletedAt) } }.toString()
    }

    private fun serializeDeletedItemTimes(): String {
        val snapshot = synchronized(deletedItemTimes) { deletedItemTimes.toMap() }
        return JSONObject().apply { snapshot.forEach { (key, deletedAt) -> put(key, deletedAt) } }.toString()
    }

    private fun mergeDeletedListTimes(incoming: Map<String, Long>) {
        if (incoming.isEmpty()) return
        synchronized(stateLock) {
            synchronized(deletedListTimes) {
                incoming.forEach { (id, deletedAt) ->
                    if (deletedAt > (deletedListTimes[id] ?: 0L)) deletedListTimes[id] = deletedAt
                }
            }
            val saved = prefs.edit().putString("deletedLists", serializeDeletedListTimes()).commit()
            if (!saved) Log.e("CarritoListSync", "Could not persist list deletion markers")
        }
    }

    private fun mergeDeletedItemTimes(incoming: Map<String, Long>) {
        if (incoming.isEmpty()) return
        synchronized(stateLock) {
            synchronized(deletedItemTimes) {
                incoming.forEach { (key, deletedAt) ->
                    if (deletedAt > (deletedItemTimes[key] ?: 0L)) deletedItemTimes[key] = deletedAt
                }
            }
            val saved = prefs.edit().putString("deletedItems", serializeDeletedItemTimes()).commit()
            if (!saved) Log.e("CarritoListSync", "Could not persist item deletion markers")
        }
    }

    private fun publishCurrentState() {
        val (listSnapshot, deletedSnapshot, deletedItemSnapshot) = synchronized(stateLock) {
            Triple(lists.value, serializeDeletedListTimes(), serializeDeletedItemTimes())
        }
        val req = PutDataMapRequest.create("/state").apply {
            dataMap.putString("json", syncJson(listSnapshot, dataMap))
            dataMap.putString("deletedLists", deletedSnapshot)
            dataMap.putString("deletedItems", deletedItemSnapshot)
            dataMap.putString("origin", syncOrigin)
            dataMap.putLong("t", System.nanoTime())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx).putDataItem(req)
    }

    fun updateList(id: String, f: (ShopList) -> ShopList) = update { l -> l.map { if (it.id == id) f(it) else it } }

    fun setMainListsViewMode(mode: Int) {
        val normalized = mode.coerceIn(0, 2)
        mainListsViewMode.value = normalized
        prefs.edit().putInt("mainListsViewMode", normalized).apply()
    }

    fun updateShops(f: (List<StoreShop>) -> List<StoreShop>) {
        shops.value = f(shops.value)
        saveShops()
    }

    private fun saveShops() {
        prefs.edit().putString("shops", JSONArray(shops.value.map { shop ->
            JSONObject().put("id", shop.id).put("name", shop.name).put("photo", shop.photo).put("photoSource", shop.photoSource)
        }).toString()).apply()
    }

    private fun parseShops(json: String): List<StoreShop> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getJSONObject(it) }.map { item ->
            StoreShop(item.optString("id", UUID.randomUUID().toString()), item.optString("name", ""),
                item.optString("photo", ""), item.optString("photoSource", ""))
        }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    fun moveItem(listId: String, itemId: String, direction: Int) = updateList(listId) { s ->
        val ordered = s.items.sortedBy { it.order }.toMutableList()
        val from = ordered.indexOfFirst { it.id == itemId }
        val to = from + direction.coerceIn(-1, 1)
        if (from < 0 || to !in ordered.indices) s
        else {
            val moved = ordered.removeAt(from)
            ordered.add(to, moved)
            s.copy(items = ordered.mapIndexed { index, item -> item.copy(order = index + 1) })
        }
    }

    fun addTo(listId: String, entries: List<Pair<String, Double>>): List<Item> {
        var added = emptyList<Item>()
        updateList(listId) { s ->
            var n = s.items.maxOfOrNull { it.order } ?: 0
            added = entries.map { (name, price) -> Item(name = name, price = price, order = ++n) }
            s.copy(items = s.items + added)
        }
        return added
    }

    /** "pan, jamón a 30 y leche 25" -> [(pan,0),(jamón,30),(leche,25)] */
    fun parseSpoken(t: String): List<Pair<String, Double>> =
        t.split(Regex(",| y ")).map { it.trim() }.filter { it.isNotEmpty() }.map { s ->
            val m = Regex("^(.*?)\\s+(?:a\\s+)?\\$?(\\d+(?:[.,]\\d+)?)$").find(s)
            val price = m?.groupValues?.get(2)?.replace(',', '.')?.toDoubleOrNull()
            if (m != null && price != null && price.isFinite()) m.groupValues[1] to price else s to 0.0
        }

    /** Divide transcripción segmentada o puntuada y descarta muletillas aisladas. */
    fun parseMultipleSpoken(t: String): List<Pair<String, Double>> =
        t.split(Regex(",(?:\\s+)|[;.!?\\n]+"))
            .map { it.trim().replace(Regex("^(?:mmm+|hmm+|eh+|em+|este)\\s+", RegexOption.IGNORE_CASE), "").trim() }
            .filter { it.isNotEmpty() && !Regex("^(?:mmm+|hmm+|eh+|em+|este)$", RegexOption.IGNORE_CASE).matches(it) }
            .flatMap(::parseSpoken)

    /** Sugerencias: objetos que aparecen junto a los de esta lista en otras listas. */
    fun suggest(cur: ShopList): List<String> {
        if (cur.items.isEmpty()) {
            data class Popularity(val sameStore: Int, val total: Int, val name: String)
            val popularity = linkedMapOf<String, Popularity>()
            lists.value.filter { it.id != cur.id }.forEach { other ->
                val sameStore = cur.store.isNotBlank() && other.store.trim().equals(cur.store.trim(), ignoreCase = true)
                other.items.distinctBy { it.name.trim().lowercase() }.forEach { item ->
                    val key = item.name.trim().lowercase()
                    if (key.isNotBlank()) {
                        val current = popularity[key]
                        popularity[key] = Popularity(
                            sameStore = (current?.sameStore ?: 0) + (if (sameStore) 1 else 0),
                            total = (current?.total ?: 0) + 1,
                            name = current?.name ?: item.name.trim()
                        )
                    }
                }
            }
            return popularity.values
                .sortedWith(compareByDescending<Popularity> { it.sameStore }
                    .thenByDescending { it.total }
                    .thenBy { it.name.lowercase() })
                .map { it.name }
        }
        val mine = cur.items.map { it.name.lowercase() }.toSet()
        val counts = HashMap<String, Pair<Int, String>>()
        lists.value.filter { it.id != cur.id }.forEach { l ->
            val names = l.items.map { it.name.lowercase() }
            if (names.any { it in mine }) {
                l.items.filter { it.name.lowercase() !in mine }.forEach { item ->
                    val key = item.name.lowercase()
                    val current = counts[key]
                    counts[key] = (current?.first?.plus(1) ?: 1) to (current?.second ?: item.name)
                }
            }
        }
        return counts.entries.sortedByDescending { it.value.first }.map { it.value.second }
    }

    /** Reuses details from the same store first, with a cross-store price average as fallback. */
    fun suggestedProduct(cur: ShopList, name: String): SuggestedProduct {
        val normalizedName = name.trim().lowercase()
        val observations = lists.value.asSequence()
            .filter { it.id != cur.id }
            .flatMap { list ->
                list.items.asSequence()
                    .filter { it.name.trim().lowercase() == normalizedName }
                    .distinctBy { it.name.trim().lowercase() }
                    .map { list to it }
            }
            .toList()
        val sameStore = if (cur.store.isBlank()) emptyList() else observations.filter {
            it.first.store.trim().equals(cur.store.trim(), ignoreCase = true)
        }
        val storePrices = sameStore.map { it.second.price }.filter { it > 0.0 }
        val allPrices = observations.map { it.second.price }.filter { it > 0.0 }
        val prices = storePrices.ifEmpty { allPrices }
        val photoSourceItem = sameStore.firstOrNull { it.second.photo.isNotBlank() }
            ?: observations.firstOrNull { it.second.photo.isNotBlank() }
        return SuggestedProduct(
            price = if (prices.isEmpty()) 0.0 else prices.average(),
            photo = photoSourceItem?.second?.photo.orEmpty(),
            photoSource = photoSourceItem?.second?.photoSource.orEmpty()
        )
    }

    fun toJson(l: List<ShopList>) = JSONArray(l.map { s ->
        JSONObject().put("id", s.id).put("name", s.name).put("store", s.store).put("at", s.createdAt).put("active", s.active)
            .put("storePhoto", s.storePhoto).put("storePhotoSource", s.storePhotoSource).put("viewMode", s.viewMode)
            .put("suggestionsVisible", s.suggestionsVisible).put("itemSortOrder", s.itemSortOrder.name)
            .put("storePhotoUpdatedAt", s.storePhotoUpdatedAt)
            .put("items", JSONArray(s.items.map { i ->
                JSONObject().put("id", i.id).put("name", i.name).put("price", i.price).put("photo", i.photo)
                    .put("order", i.order).put("checked", i.checked).put("cart", i.cartOrder).put("photoSource", i.photoSource)
                    .put("photoUpdatedAt", i.photoUpdatedAt)
            }))
    }).toString()

    fun parse(j: String): List<ShopList> {
        val a = JSONArray(j)
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val ia = o.getJSONArray("items")
            ShopList(o.getString("id"), o.getString("name"), o.getString("store"),
                (0 until ia.length()).map { ia.getJSONObject(it) }.map { x ->
                    Item(x.getString("id"), x.getString("name"), x.getDouble("price"), x.getString("photo"),
                        x.getInt("order"), x.getBoolean("checked"), x.getInt("cart"), x.optString("photoSource", ""), x.optLong("photoUpdatedAt", 0L))
                }, o.getLong("at"), o.getBoolean("active"), o.optString("storePhoto", ""), o.optString("storePhotoSource", ""),
                o.optInt("viewMode", 0), o.optBoolean("suggestionsVisible", true),
                runCatching { ItemSortOrder.valueOf(o.optString("itemSortOrder", ItemSortOrder.MANUAL.name)) }
                    .getOrDefault(ItemSortOrder.MANUAL), o.optLong("storePhotoUpdatedAt", 0L))
        }
    }
}
