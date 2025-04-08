package com.example.driveease

import android.os.Message
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PotholeApiService {

    @Multipart
    @POST("/report-pothole")
    fun reportPothole(
        @Part("user_id") userId: RequestBody,
        @Part("lat") latitude: RequestBody,
        @Part("lng") longitude: RequestBody,
        @Part image: MultipartBody.Part
    ): Call<Void> // Use Call<Void> for async
}
//data class ReportResponse(
//    val message: String
//)