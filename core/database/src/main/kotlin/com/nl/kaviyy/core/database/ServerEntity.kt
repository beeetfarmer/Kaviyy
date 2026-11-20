package com.nl.kaviyy.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.nl.kaviyy.core.model.server.PinnedCertificate
import com.nl.kaviyy.core.model.server.Server
import com.nl.kaviyy.core.model.server.ServerId
import com.nl.kaviyy.core.model.server.ServerUrl
import com.nl.kaviyy.core.model.server.ServerUrlResult
import kotlinx.coroutines.flow.Flow

/** A configured server. Secrets are never stored here (they live in the encrypted CredentialStore). */
@Entity(tableName = "servers", indices = [Index(value = ["url"], unique = true)])
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Normalised `ServerUrl.value`. */
    val url: String,
    val version: String? = null,
    val pinnedCertDerBase64: String? = null,
    val allowInsecure: Boolean = false,
    val lastUsedAt: Long = 0,
)

fun ServerEntity.toModel(): Server? =
    (ServerUrl.parse(url) as? ServerUrlResult.Valid)?.let { valid ->
        Server(ServerId(id), name, valid.url, version, pinnedCertDerBase64?.let(::PinnedCertificate), allowInsecure)
    }

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE url = :url")
    suspend fun byUrl(url: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun byId(id: Long): ServerEntity?

    @Upsert
    suspend fun upsert(server: ServerEntity): Long

    @Query("UPDATE servers SET lastUsedAt = :at WHERE id = :id")
    suspend fun touch(
        id: Long,
        at: Long,
    )

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Long)
}
