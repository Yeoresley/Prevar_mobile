package cu.assi.prevarmobile.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(val ok: Boolean, val data: T? = null, val error: String? = null, val code: String? = null)

data class PairRequest(
    val secret: String? = null,
    val code: String? = null,
    @SerializedName("device_uuid") val deviceUuid: String,
    @SerializedName("device_name") val deviceName: String,
    val platform: String = "Android"
)

data class PairProfile(
    @SerializedName("user_id") val userId: Long,
    @SerializedName("id_unidad") val unitId: Long,
    val trabajador: Long,
    @SerializedName("role_id") val roleId: Long,
    @SerializedName("user_name") val userName: String
)

data class PairResponse(
    val token: String,
    @SerializedName("device_id") val deviceId: Long,
    val profile: PairProfile
)

data class QuestionnaireDto(
    val id: Long,
    val numero: String? = null,
    val comision: String? = null,
    val total: Int = 0,
    val estado: Int = 1,
    val evaluadas: Int = 0,
    val pendientes: Int = 0,
    val positivas: Int = 0,
    val negativa: Int = 0,
    val poreje: Double = 0.0,
    val fecha: String? = null
)

data class EvaluationDto(
    @SerializedName("evaluation_id") val evaluationId: Long,
    @SerializedName("id_dci") val questionnaireId: Long,
    val evalua: Int = 3,
    val com: String? = null,
    val evidencia: String? = null,
    val causas: String? = null,
    val phd: Int? = 0,
    val pca: Int? = 0,
    val cuantia: Double? = null,
    val trabajador: Long? = null,
    @SerializedName("gau_id") val gauId: Long,
    @SerializedName("general_question_id") val generalQuestionId: Long? = null,
    val codigo: String? = null,
    val pregunta: String? = null,
    val qevalua: String? = null,
    val funsi: String? = null,
    val legislaso: String? = null,
    val seincum: String? = null,
    @SerializedName("server_version") val serverVersion: Long = 1,
    @SerializedName("last_positive") val lastPositive: String? = null,
    @SerializedName("last_negative") val lastNegative: String? = null,
    @SerializedName("can_edit") val canEdit: Boolean = false
)

data class MeasureDto(
    @SerializedName("measure_id") val measureId: Long,
    @SerializedName("id_eval") val evaluationId: Long,
    val medida: String,
    val fcc: String,
    val responsable: Long,
    val participantes: String? = null,
    @SerializedName("server_version") val serverVersion: Long = 1
)

data class RiskDraftDto(
    @SerializedName("mobile_uuid") val mobileUuid: String,
    @SerializedName("evaluation_id") val evaluationId: Long,
    @SerializedName("object_text") val objectText: String,
    @SerializedName("risk_text") val riskText: String,
    val probability: Int,
    val impact: Int,
    val result: Int,
    val level: Int,
    val argument: String? = null,
    val version: Long = 1,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AttachmentDto(
    @SerializedName("mobile_uuid") val mobileUuid: String,
    @SerializedName("evaluation_id") val evaluationId: Long,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("mime_type") val mimeType: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    val version: Long = 1,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AttachmentSyncResponse(
    @SerializedName("mobile_uuid") val mobileUuid: String,
    @SerializedName("evaluation_id") val evaluationId: Long? = null,
    @SerializedName("server_version") val serverVersion: Long = 1,
    val deleted: Boolean? = null
)

data class WorkerDto(val id: Long, val nombre: String, val cargo: String? = null)

data class SettingsDto(@SerializedName("min_chars") val minChars: Int = 100)

data class PullResponse(
    val questionnaire: QuestionnaireDto,
    val evaluations: List<EvaluationDto>,
    val measures: List<MeasureDto>,
    @SerializedName("risk_drafts") val riskDrafts: List<RiskDraftDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val workers: List<WorkerDto>,
    val settings: SettingsDto
)

data class PushRequest(val operations: List<JsonObject>)

data class PushOperationResult(
    val uuid: String,
    val status: String? = null,
    @SerializedName("server_id") val serverId: Long? = null,
    @SerializedName("evaluation_id") val evaluationId: Long? = null,
    @SerializedName("server_version") val serverVersion: Long? = null,
    val deleted: Boolean? = null
)

data class PushResponse(
    val results: List<PushOperationResult> = emptyList(),
    val questionnaires: Map<String, JsonObject> = emptyMap(),
    @SerializedName("server_time") val serverTime: String? = null
)
