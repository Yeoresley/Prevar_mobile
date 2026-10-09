package cu.assi.prevarmobile.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface PrevarApi {
    @POST("pair")
    suspend fun pair(@Body request: PairRequest): ApiEnvelope<PairResponse>

    @GET("questionnaires")
    suspend fun questionnaires(): ApiEnvelope<List<QuestionnaireDto>>

    @GET("pull")
    suspend fun pull(@Query("questionnaire_id") questionnaireId: Long): ApiEnvelope<PullResponse>

    @POST("push")
    suspend fun push(@Body request: PushRequest): ApiEnvelope<PushResponse>
}
