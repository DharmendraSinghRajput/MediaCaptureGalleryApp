package com.ssti.mediacapturegalleryapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ssti.mediacapturegalleryapp.data.local.dao.MediaDao
import com.ssti.mediacapturegalleryapp.data.local.entity.MediaEntity

@Database(entities = [MediaEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
