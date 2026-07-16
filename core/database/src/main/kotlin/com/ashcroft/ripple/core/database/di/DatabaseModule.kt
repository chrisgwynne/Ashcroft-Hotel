package com.ashcroft.ripple.core.database.di

import android.content.Context
import androidx.room.Room
import com.ashcroft.ripple.core.database.RippleDatabase
import com.ashcroft.ripple.core.database.dao.SavedGameDao
import com.ashcroft.ripple.core.database.save.RoomSaveRepository
import com.ashcroft.ripple.core.database.save.SaveRepository
import dagger.Binds
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
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): RippleDatabase =
        Room
            .databaseBuilder(context, RippleDatabase::class.java, RippleDatabase.NAME)
            .addMigrations(*RippleDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideSavedGameDao(database: RippleDatabase): SavedGameDao = database.savedGameDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSaveRepository(impl: RoomSaveRepository): SaveRepository
}
