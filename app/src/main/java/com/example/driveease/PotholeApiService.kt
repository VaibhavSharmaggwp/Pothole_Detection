// PotholeApiService.kt
package com.example.driveease

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Part

interface PotholeApiService {
    @GET("/ping")
    fun ping(): Call<PingResponse>

    
    @Multipart
    @POST("/report-pothole")
    fun reportPothole(
        @Part("user_id") userId: RequestBody,
        @Part("lat") latitude: RequestBody,
        @Part("lng") longitude: RequestBody,
        @Part("description") description: RequestBody,
        @Part("severity") severity: RequestBody,
        @Part image: MultipartBody.Part
    ): Call<ReportResponse>

}
data class PingResponse(
    val ping: String
)
data class ReportResponse(
    val message: String
)