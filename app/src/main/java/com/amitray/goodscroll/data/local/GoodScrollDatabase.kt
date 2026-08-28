package com.amitray.goodscroll.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Bookmark::class],
    version = 1,
    exportSchema = true,
)
abstract class GoodScrollDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val DATABASE_NAME = "goodscroll.db"
    }
}
