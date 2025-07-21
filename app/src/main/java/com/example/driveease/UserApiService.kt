package com.example.driveease

import retrofit2.http.Body
import retrofit2.http.POST


interface UserApiService{
    @POST("/auth/signin/email")
    suspend fun signUp(@Body userRequest: UserRequest): UserResponse
}

data class UserRequest(
    val name: String,
    val email: String,
    val phone: String,
    val password: String
)

data class UserResponse(
    val message: String
)