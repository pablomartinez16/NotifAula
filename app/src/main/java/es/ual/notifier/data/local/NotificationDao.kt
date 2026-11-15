package es.ual.notifier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 300")
    fun observeRecent(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: NotificationEntity): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}

@Dao
interface SeenDao {
    @Query("SELECT * FROM seen_items WHERE `key` = :key")
    suspend fun get(key: String): SeenItem?

    @Query("SELECT EXISTS(SELECT 1 FROM seen_items WHERE `key` = :key)")
    suspend fun exists(key: String): Boolean

    @Upsert
    suspend fun upsert(item: SeenItem)

    @Upsert
    suspend fun upsertAll(items: List<SeenItem>)

    @Query("DELETE FROM seen_items")
    suspend fun deleteAll()
}
