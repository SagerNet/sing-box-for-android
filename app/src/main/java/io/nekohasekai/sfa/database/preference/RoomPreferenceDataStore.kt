package io.nekohasekai.sfa.database.preference

import android.util.Log
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Suppress("MemberVisibilityCanBePrivate", "unused")
open class RoomPreferenceDataStore(createDao: () -> KeyValueEntity.Dao) : PreferenceDataStore() {
    private val kvPairDao by lazy(createDao)
    private val writeAccess = Any()
    private val pendingWrites = Channel<() -> Unit>(Channel.UNLIMITED)
    private val cache: MutableMap<String, KeyValueEntity> by lazy {
        ConcurrentHashMap<String, KeyValueEntity>().apply {
            kvPairDao.all().forEach { put(it.key, it) }
        }
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (write in pendingWrites) {
                try {
                    write()
                } catch (exception: Exception) {
                    Log.e("RoomPreferenceDataStore", "write preferences", exception)
                }
            }
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        cache.size
        Unit
    }

    suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        synchronized(writeAccess) {
            check(pendingWrites.trySend { completion.complete(Unit) }.isSuccess)
        }
        completion.await()
    }

    private fun put(entity: KeyValueEntity) {
        val values = cache
        synchronized(writeAccess) {
            values[entity.key] = entity
            check(pendingWrites.trySend { kvPairDao.put(entity) }.isSuccess)
        }
        fireChangeListener(entity.key)
    }

    fun getBoolean(key: String) = cache[key]?.boolean

    fun getFloat(key: String) = cache[key]?.float

    fun getInt(key: String) = cache[key]?.long?.toInt()

    fun getLong(key: String) = cache[key]?.long

    fun getString(key: String) = cache[key]?.string

    fun getStringSet(key: String) = cache[key]?.stringSet

    fun reset() {
        val values = cache
        synchronized(writeAccess) {
            values.clear()
            check(pendingWrites.trySend { kvPairDao.reset() }.isSuccess)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean) = getBoolean(key) ?: defValue

    override fun getFloat(key: String, defValue: Float) = getFloat(key) ?: defValue

    override fun getInt(key: String, defValue: Int) = getInt(key) ?: defValue

    override fun getLong(key: String, defValue: Long) = getLong(key) ?: defValue

    override fun getString(key: String, defValue: String?) = getString(key) ?: defValue

    override fun getStringSet(key: String, defValue: MutableSet<String>?) = getStringSet(key) ?: defValue

    fun putBoolean(key: String, value: Boolean?) = if (value == null) remove(key) else putBoolean(key, value)

    fun putFloat(key: String, value: Float?) = if (value == null) remove(key) else putFloat(key, value)

    fun putInt(key: String, value: Int?) = if (value == null) remove(key) else putLong(key, value.toLong())

    fun putLong(key: String, value: Long?) = if (value == null) remove(key) else putLong(key, value)

    override fun putBoolean(key: String, value: Boolean) = put(KeyValueEntity(key).put(value))

    override fun putFloat(key: String, value: Float) = put(KeyValueEntity(key).put(value))

    override fun putInt(key: String, value: Int) = put(KeyValueEntity(key).put(value.toLong()))

    override fun putLong(key: String, value: Long) = put(KeyValueEntity(key).put(value))

    override fun putString(key: String, value: String?) = if (value == null) {
        remove(key)
    } else {
        put(KeyValueEntity(key).put(value))
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) = if (values == null) {
        remove(key)
    } else {
        put(KeyValueEntity(key).put(values))
    }

    fun remove(key: String) {
        val values = cache
        synchronized(writeAccess) {
            values.remove(key)
            check(pendingWrites.trySend { kvPairDao.delete(key) }.isSuccess)
        }
        fireChangeListener(key)
    }

    private val listeners = HashSet<OnPreferenceDataStoreChangeListener>()

    private fun fireChangeListener(key: String) {
        val listeners =
            synchronized(listeners) {
                listeners.toList()
            }
        listeners.forEach { it.onPreferenceDataStoreChanged(this, key) }
    }

    fun registerChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
}
