package com.amitray.goodscroll.di

import android.content.Context
import androidx.room.Room
import com.amitray.goodscroll.data.local.BookmarkDao
import com.amitray.goodscroll.data.local.GoodScrollDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GoodScrollDatabase =
        Room.databaseBuilder(
            context,
            GoodScrollDatabase::class.java,
            GoodScrollDatabase.DATABASE_NAME,
        ).build()

    @Provides
    fun provideBookmarkDao(database: GoodScrollDatabase): BookmarkDao = database.bookmarkDao()
}
