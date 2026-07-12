package com.example.ctrl

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
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
import java.util.concurrent.TimeUnit

data class BreakResponse(
    @SerializedName("study_time_minutes") val studyTimeMinutes: Int,
    @SerializedName("recommended_break_minutes") val recommendedBreakMinutes: Int
)

data class UploadResponse(
    val status: String,
    val title: String,
    @SerializedName("chunks_saved") val chunksSaved: Int? = null,
    val message: String? = null
)

data class MaterialsResponse(
    @SerializedName("saved_materials") val savedMaterials: List<String>
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    @SerializedName("correct_answer") val correctAnswer: String
)

data class QuizEvaluateRequest(
    @SerializedName("total_questions") val totalQuestions: Int,
    @SerializedName("correct_answers") val correctAnswers: Int
)

data class QuizEvaluateResponse(
    val passed: Boolean,
    val score: Double,
    val action: String,
    val message: String
)

@Suppress("unused")
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

@Suppress("unused")
object RetrofitClient {
    private const val BASE_URL = "http://165.245.137.153:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
