package com.example.driveease
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface UserSigninApiService {
    @POST("/auth/login/email")
    suspend fun SignIn(@Body userRequest: UserSignRequest): UserSignInResponse

    @GET("/fetch/id/{id}")
    suspend fun fetchPotholesByUserId(@Path("id") userId: Int): PotholeListResponse

}

data class UserSignRequest(
    val email: String,
    val password: String
)

data class UserSignInResponse(
    val id: Int,  // added new field
    val message: String,
    val token: String,
    val error: String
)

data class PotholeListResponse(
    val id: Int,
    val potholes: List<PotholeResponse>
)

data class PotholeResponse(
    val id: Int,
    val description: String,
    val image_url: String,
    val severity: String,
    val created_at: String,
    val zone_name: String,
    val status: String
)