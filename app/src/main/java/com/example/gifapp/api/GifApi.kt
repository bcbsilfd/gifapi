package com.example.gifapp.api

import com.example.gifapp.model.Gif
import com.example.gifapp.model.GifResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GifApi {

    @GET("random")
    fun getRandom(
        @Query("json") json: Boolean
    ): Call<Gif>

    @GET("latest/{page}?json=true")
    fun getLatest(
        @Path("page") page: Int
    ): Call<GifResponse>

    @GET("top/{page}?json=true")
    fun getTop(
        @Path("page") page: Int
    ): Call<GifResponse>
}