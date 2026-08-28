package com.amitray.goodscroll.data.remote

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataFetcherModule {

    @Binds
    @Singleton
    abstract fun bindMetadataFetcher(impl: JsoupMetadataFetcher): MetadataFetcher
}
