package io.nekohasekai.sfa.database

import android.os.Parcelable
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.parcelize.Parcelize
import java.net.URI

@Entity(
    tableName = "remote_servers",
)
@Parcelize
class RemoteServer(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var userOrder: Long = 0L,
    var name: String = "",
    var url: String = "",
    var secret: String = "",
) : Parcelable {
    val displayName: String
        get() = name.ifEmpty { url }

    companion object {
        fun validateURL(urlString: String): String? {
            var trimmed = urlString.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            if (!trimmed.contains("://")) {
                trimmed = "http://$trimmed"
            }
            val uri =
                try {
                    URI(trimmed)
                } catch (_: Exception) {
                    return null
                }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return null
            }
            if (uri.host.isNullOrEmpty()) {
                return null
            }
            return trimmed
        }
    }

    @androidx.room.Dao
    interface Dao {
        @Insert
        fun insert(server: RemoteServer): Long

        @Update
        fun update(server: RemoteServer): Int

        @Update
        fun update(servers: List<RemoteServer>): Int

        @Delete
        fun delete(server: RemoteServer): Int

        @Query("SELECT * FROM remote_servers WHERE id = :serverId")
        fun get(serverId: Long): RemoteServer?

        @Query("SELECT * FROM remote_servers ORDER BY userOrder ASC")
        fun list(): List<RemoteServer>

        @Query("SELECT MAX(userOrder) + 1 FROM remote_servers")
        fun nextOrder(): Long?
    }
}
