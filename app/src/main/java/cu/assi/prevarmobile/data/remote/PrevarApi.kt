package cu.assi.prevarmobile.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

interface PrevarApi {
    @POST("pair")
    suspend fun pair(@Body request: PairRequest): ApiEnvelope<PairResponse>

    @GET("questionnaires")
    suspend fun questionnaires(): ApiEnvelope<List<QuestionnaireDto>>

    @GET("pull")
    suspend fun pull(@Query("questionnaire_id") questionnaireId: Long): ApiEnvelope<PullResponse>

    @POST("push")
    suspend fun push(@Body request: PushRequest): ApiEnvelope<PushResponse>

    @POST("attachment")
    suspend fun uploadAttachment(
        @Query("evaluation_id") evaluationId: Long,
        @Query("mobile_uuid") mobileUuid: String,
        @Query("file_name") fileName: String,
        @Query("mime_type") mimeType: String,
        @Query("sha256") sha256: String,
        @Body body: RequestBody
    ): ApiEnvelope<AttachmentSyncResponse>

    @DELETE("attachment")
    suspend fun deleteAttachment(
        @Query("mobile_uuid") mobileUuid: String
    ): ApiEnvelope<AttachmentSyncResponse>

    @Streaming
    @GET("attachment")
    suspend fun downloadAttachment(
        @Query("mobile_uuid") mobileUuid: String
    ): Response<ResponseBody>
}
