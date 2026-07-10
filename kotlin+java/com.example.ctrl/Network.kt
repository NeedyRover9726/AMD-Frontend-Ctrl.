package com.example.ctrl

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class BreakResponse(val study_time_minutes: Int, val recommended_break_minutes: Int)
data class UploadResponse(val status: String, val title: String, val chunks_saved: Int)
data class MaterialsResponse(val saved_materials: List<String>)
data class QuizQuestion(val question: String, val options: List<String>, val correct_answer: String)
data class QuizEvaluateRequest(val total_questions: Int, val correct_answers: Int)
data class QuizEvaluateResponse(val passed: Boolean, val score: Double, val action: String, val message: String)

interface ApiService {
    @GET("calculate-break/")
    suspend fun calculateBreak(@Query("study_time") studyTime: Int): BreakResponse

    @Multipart
    @POST("upload-material/")
    suspend fun uploadMaterial(@Part("title") title: RequestBody, @Part file: MultipartBody.Part): UploadResponse

    @GET("materials/")
    suspend fun getMaterials(): MaterialsResponse

    @FormUrlEncoded
    @POST("generate-quiz/")
    suspend fun generateQuiz(@Field("topic") topic: String, @Field("selected_titles") selectedTitles: String, @Field("length") length: Int): List<QuizQuestion>

    @POST("evaluate-quiz/")
    suspend fun evaluateQuiz(@Body result: QuizEvaluateRequest): QuizEvaluateResponse
}

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
