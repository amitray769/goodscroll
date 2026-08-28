package com.amitray.goodscroll.di

import android.content.Context
import androidx.work.WorkManager
import com.amitray.goodscroll.notification.ReminderNotifier
import com.amitray.goodscroll.notification.SystemReminderNotifier
import com.amitray.goodscroll.reminder.ReminderScheduler
import com.amitray.goodscroll.reminder.SystemTimeProvider
import com.amitray.goodscroll.reminder.TimeProvider
import com.amitray.goodscroll.reminder.WorkManagerReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderNotifier(impl: SystemReminderNotifier): ReminderNotifier

    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    companion object {

        /**
         * The instance configured by [com.amitray.goodscroll.GoodScrollApplication], so workers
         * are built by the [androidx.hilt.work.HiltWorkerFactory] and get their dependencies.
         */
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
            WorkManager.getInstance(context)
    }
}
