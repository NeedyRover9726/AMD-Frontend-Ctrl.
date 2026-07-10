package com.example.ctrl

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// 1. The data we send TO the backend (e.g., "Pages 38-55")
data class QuizGenerationRequest(
    val fileName: String,
    val readingRange: String,
    val requestedLength: Int
)

// 2. The data we receive FROM the backend
data class QuizGenerationResponse(
    val quizId: String,
    val questions: List<QuestionModel>
)

data class QuestionModel(
    val questionText: String,
    val options: List<String>,
    val correctOptionIndex: Int
)

// 3. The API Routing Interface
interface ApiService {
    @POST("api/quiz/generate")
    suspend fun generateDynamicQuiz(@Body request: QuizGenerationRequest): QuizGenerationResponse
}

// 4. The Retrofit Client (Pointed to local emulator IP)
object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
