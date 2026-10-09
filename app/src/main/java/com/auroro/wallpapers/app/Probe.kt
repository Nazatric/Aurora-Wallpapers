package com.auroro.wallpapers.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.serialization.Serializable

@Entity(tableName = "probe")
data class ProbeEntity(@PrimaryKey val id: Long)

@Dao
interface ProbeDao {
    @Query("SELECT * FROM probe")
    suspend fun all(): List<ProbeEntity>
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = true)
abstract class ProbeDb : RoomDatabase() {
    abstract fun dao(): ProbeDao
}

@Serializable
data class ProbeDto(val a: Int)
