package com.rajasudhan.taskmind.data.source.email

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gmail REST API (read-only). Base URL `https://gmail.googleapis.com/gmail/v1/`.
 * Every call carries a short-lived OAuth Bearer token obtained via [GmailAuth].
 */
interface GmailApi {

    @GET("users/me/messages")
    suspend fun listMessages(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int
    ): GmailMessageList

    @GET("users/me/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Query("format") format: String = "full"
    ): GmailMessage

    @GET("users/me/profile")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): GmailProfile
}
