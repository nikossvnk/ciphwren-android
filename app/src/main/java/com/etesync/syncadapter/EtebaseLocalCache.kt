package com.etesync.syncadapter

import android.content.Context
import com.etebase.client.*
import com.etebase.client.Collection
import okhttp3.OkHttpClient
import java.io.File
import java.util.*

class EtebaseLocalCache private constructor(context: Context, username: String) {
    private val fsCache: FileSystemCache = FileSystemCache.create(context.filesDir.absolutePath, username)
    private val filesDir: File = File(context.filesDir, username)
    private val colsDir: File = File(filesDir, "cols")

    private fun getCollectionItemsDir(colUid: String): File {
        val colsDir = File(filesDir, "cols")
        val colDir = File(colsDir, colUid)
        return File(colDir, "items")
    }

    private fun clearUserCache() {
        fsCache.clearUserCache()
    }

    fun saveStoken(stoken: String) {
        fsCache.saveStoken(stoken)
    }

    fun loadStoken(): String? {
        return fsCache.loadStoken()
    }

    fun collectionSaveStoken(colUid: String, stoken: String) {
        fsCache.collectionSaveStoken(colUid, stoken)
    }

    fun collectionLoadStoken(colUid: String): String? {
        return fsCache.collectionLoadStoken(colUid)
    }

    fun collectionList(colMgr: CollectionManager, withDeleted: Boolean = false): List<CachedCollection> {
        return fsCache.collectionList(colMgr).mapNotNull {
            if (withDeleted || !it.isDeleted()) {
                CachedCollection(it, it.meta(), it.collectionType())
            } else {
                // Skipped, so nothing else references it
                it.close()
                null
            }
        }
    }

    fun collectionGet(colMgr: CollectionManager, colUid: String): CachedCollection {
        return fsCache.collectionGet(colMgr, colUid).let {
            CachedCollection(it, it.meta(), it.collectionType())
        }
    }

    fun collectionSet(colMgr: CollectionManager, collection: Collection) {
        fsCache.collectionSet(colMgr, collection)
    }

    fun collectionUnset(colMgr: CollectionManager, colUid: String) {
        try {
            fsCache.collectionUnset(colMgr, colUid)
        } catch (e: EtebaseException.UrlParse) {
            // Ignore, as it just means the file doesn't exist
        }
    }

    fun itemList(itemMgr: ItemManager, colUid: String, withDeleted: Boolean = false): List<CachedItem> {
        return fsCache.itemList(itemMgr, colUid).mapNotNull {
            if (withDeleted || !it.isDeleted()) {
                CachedItem(it, it.meta(), it.contentString())
            } else {
                // Skipped, so nothing else references it
                it.close()
                null
            }
        }
    }

    fun itemGet(itemMgr: ItemManager, colUid: String, itemUid: String): CachedItem? {
        // Need the try because the inner call doesn't return null on missing, but an error
        val ret = try {
            fsCache.itemGet(itemMgr, colUid, itemUid)
        } catch (e: EtebaseException) {
            return null
        }
        return ret.let {
            CachedItem(it, it.meta(), it.contentString())
        }
    }

    fun itemSet(itemMgr: ItemManager, colUid: String, item: Item) {
        fsCache.itemSet(itemMgr, colUid, item)
    }

    fun itemUnset(itemMgr: ItemManager, colUid: String, itemUid: String) {
        fsCache.itemUnset(itemMgr, colUid, itemUid)
    }

    companion object {
        private val localCacheCache: HashMap<String, EtebaseLocalCache> = HashMap()

        fun getInstance(context: Context, username: String): EtebaseLocalCache {
            synchronized(localCacheCache) {
                val cached = localCacheCache.get(username)
                if (cached != null) {
                    return cached
                } else {
                    val ret = EtebaseLocalCache(context, username)
                    localCacheCache.set(username, ret)
                    return ret
                }
            }
        }

        fun clearUserCache(context: Context, username: String) {
            val localCache = getInstance(context, username)
            localCache.clearUserCache()
            localCacheCache.remove(username)
        }

        // FIXME: If we ever cache this we need to cache bust on changePassword
        fun getEtebase(context: Context, httpClient: OkHttpClient, settings: AccountSettings): Account {
            // The account keeps its own reference to the client
            return createClient(httpClient, settings.uri.toString()).use { client ->
                Account.restore(client, settings.etebaseSession!!, null)
            }
        }

        private fun Item.contentString(): String = String(content(), Charsets.UTF_8)
    }
}

data class CachedCollection(val col: Collection, val meta: ItemMetadata, val collectionType: String)

data class CachedItem(val item: Item, val meta: ItemMetadata, val content: String)