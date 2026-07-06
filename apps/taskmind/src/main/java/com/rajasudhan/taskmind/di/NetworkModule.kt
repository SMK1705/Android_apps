package com.rajasudhan.taskmind.di

import com.rajasudhan.taskmind.data.source.email.GmailApi
import com.rajasudhan.taskmind.data.source.embedding.Embedder
import com.rajasudhan.taskmind.data.source.embedding.HashingEmbedder
import com.rajasudhan.taskmind.data.source.transcription.NativeWhisperEngine
import com.rajasudhan.taskmind.data.source.transcription.WhisperEngine
import com.rajasudhan.taskmind.data.source.understanding.LlmProvider
import com.rajasudhan.taskmind.data.source.understanding.RoutingLlmProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindLlmProvider(
        routingLlmProvider: RoutingLlmProvider
    ): LlmProvider

    @Binds
    @Singleton
    abstract fun bindEmbedder(
        hashingEmbedder: HashingEmbedder
    ): Embedder

    // The native whisper.cpp engine isn't linked yet (#207); this binding makes the second pass a
    // graceful no-op until it is. Swap the implementation here when the JNI engine lands.
    @Binds
    @Singleton
    abstract fun bindWhisperEngine(
        nativeWhisperEngine: NativeWhisperEngine
    ): WhisperEngine

    companion object {
        @Provides
        @Singleton
        fun provideMoshi(): Moshi {
            return Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder().build()
        }

        @Provides
        @Singleton
        fun provideGmailApi(client: OkHttpClient, moshi: Moshi): GmailApi {
            return Retrofit.Builder()
                .baseUrl("https://gmail.googleapis.com/gmail/v1/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GmailApi::class.java)
        }
    }
}
