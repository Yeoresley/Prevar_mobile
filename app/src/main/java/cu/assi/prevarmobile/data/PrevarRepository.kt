package cu.assi.prevarmobile.data

import android.os.Build
import com.google.gson.Gson
import com.google.gson.JsonObject
import cu.assi.prevarmobile.data.local.*
import cu.assi.prevarmobile.data.remote.*
import cu.assi.prevarmobile.domain.EvaluationValidator
import cu.assi.prevarmobile.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import retrofit2.HttpException

class PrevarRepository(
    private val dao: PrevarDao,
    private val store: SecureStore,
    private val apiFactory: ApiFactory,
    private val gson: Gson = Gson()
) {
    val questionnaires: Flow<List<QuestionnaireEntity>> = dao.questionnaires()
    val pendingCount: Flow<Int> = dao.pendingCount()
    val lastSync: Flow<String?> = dao.stateFlow("last_sync")
    val lastError: Flow<String?> = dao.stateFlow("last_error")
    val conflict: Flow<SyncConflict?> = dao.stateFlow("sync_conflict").map { raw ->
        runCatching {
            raw?.takeIf { it.isNotBlank() }?.let { gson.fromJson(it, SyncConflict::class.java) }
        }.getOrNull()
    }

    fun evaluations(questionnaireId: Long): Flow<List<EvaluationEntity>> = dao.evaluations(questionnaireId)
    fun evaluation(id: Long): Flow<EvaluationEntity?> = dao.evaluation(id)
    fun measures(evaluationId: Long): Flow<List<MeasureEntity>> = dao.measures(evaluationId)
    fun workers(): Flow<List<WorkerEntity>> = dao.workers()

    fun isPaired(): Boolean = !store.apiUrl.isNullOrBlank() && !store.token.isNullOrBlank()
    fun userName(): String = store.userName.orEmpty()
    fun apiUrl(): String = store.apiUrl.orEmpty()

    suspend fun pair(apiUrl: String, secret: String? = null, code: String? = null) {
        val normalized = apiUrl.trim().trimEnd('/')
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) { "Dirección del servidor no válida." }
        val api = apiFactory.create(normalized, authenticated = false)
        val envelope = try {
            api.pair(
                PairRequest(
                    secret = secret?.takeIf { it.isNotBlank() },
                    code = code?.uppercase()?.takeIf { it.isNotBlank() },
                    deviceUuid = store.deviceUuid,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    platform = "Android ${Build.VERSION.RELEASE}"
                )
            )
        } catch (error: HttpException) {
            error(parseHttpError(error) ?: "PREVAR rechazó la vinculación (HTTP ${error.code()}).")
        }
        val data = envelope.data ?: error(envelope.error ?: "No fue posible vincular PREVAR Mobile.")
        store.apiUrl = normalized
        store.token = data.token
        store.userName = data.profile.userName
        store.unitId = data.profile.unitId
        dao.putState(SyncStateEntity("last_error", ""))
    }

    suspend fun pairFromQr(raw: String) {
        val objectJson = gson.fromJson(raw, JsonObject::class.java)
        require(objectJson.get("format")?.asString == "PREVAR-MOBILE-PAIR-1") { "El QR no corresponde a PREVAR Mobile." }
        val api = objectJson.get("api")?.asString ?: error("El QR no contiene la dirección del servidor.")
        val secret = objectJson.get("secret")?.asString
        val code = objectJson.get("code")?.asString
        pair(api, secret, code)
    }

    suspend fun unpair() {
        store.clearPairing()
        dao.putState(SyncStateEntity("last_error", ""))
        dao.putState(SyncStateEntity("sync_conflict", ""))
    }

    suspend fun minChars(): Int = dao.state("min_chars")?.toIntOrNull() ?: 100

    suspend fun saveEvaluation(
        evaluationId: Long,
        result: Int,
        fundamentation: String,
        evidence: String,
        causes: String,
        pca: Boolean,
        phd: Boolean,
        amount: Double?
    ): List<String> {
        val current = dao.evaluationNow(evaluationId) ?: return listOf("La evaluación ya no está disponible localmente.")
        if (!current.canEdit) return listOf("Esta pregunta no está autorizada para edición móvil.")
        val measureCount = dao.measuresNow(evaluationId).count { !it.deleted }
        val errors = EvaluationValidator.validate(
            EvaluationValidator.Input(result, fundamentation, causes, measureCount), minChars()
        )
        if (errors.isNotEmpty()) return errors
        val updated = current.copy(
            result = result,
            fundamentation = fundamentation.trim(),
            evidence = evidence.trim(),
            causes = if (result == EvaluationValidator.RESULT_NOT_COMPLIES) causes.trim() else "",
            pca = pca,
            phd = phd,
            amount = amount,
            dirty = true
        )
        dao.updateEvaluation(updated)
        val opUuid = UUID.randomUUID().toString()
        val op = JsonObject().apply {
            addProperty("uuid", opUuid)
            addProperty("kind", "evaluation.update")
            addProperty("evaluation_id", evaluationId)
            addProperty("base_version", current.serverVersion)
            add("fields", JsonObject().apply {
                addProperty("evalua", result)
                addProperty("com", updated.fundamentation)
                addProperty("evidencia", updated.evidence)
                addProperty("causas", updated.causes)
                addProperty("pca", if (pca) 1 else 0)
                addProperty("phd", if (phd) 1 else 0)
                if (amount == null) add("cuantia", null) else addProperty("cuantia", amount)
            })
        }
        val key = "evaluation:$evaluationId"
        dao.deleteOutboxForEntity(key)
        dao.upsertOutbox(OutboxEntity(opUuid, key, "evaluation.update", gson.toJson(op)))
        return emptyList()
    }

    suspend fun saveMeasure(
        localUuid: String? = null,
        serverId: Long? = null,
        evaluationId: Long,
        text: String,
        dueDate: String,
        responsibleId: Long,
        participants: List<Long>,
        baseVersion: Long = 0
    ): String {
        require(text.trim().length >= 5) { "Describa la medida o acción." }
        require(dueDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "La fecha de cumplimiento no es válida." }
        require(responsibleId > 0) { "Seleccione el responsable de la medida." }
        val uuidValue = localUuid ?: UUID.randomUUID().toString()
        val entity = MeasureEntity(
            localUuid = uuidValue,
            serverId = serverId,
            evaluationId = evaluationId,
            text = text.trim(),
            dueDate = dueDate,
            responsibleId = responsibleId,
            participants = participants.distinct().joinToString(";"),
            serverVersion = baseVersion,
            deleted = false,
            dirty = true
        )
        dao.upsertMeasure(entity)
        val opUuid = UUID.randomUUID().toString()
        val op = JsonObject().apply {
            addProperty("uuid", opUuid)
            addProperty("kind", "measure.upsert")
            addProperty("evaluation_id", evaluationId)
            addProperty("mobile_uuid", uuidValue)
            if (serverId != null) addProperty("server_id", serverId)
            if (baseVersion > 0) addProperty("base_version", baseVersion)
            add("fields", JsonObject().apply {
                addProperty("medida", entity.text)
                addProperty("fcc", dueDate)
                addProperty("responsable", responsibleId)
                addProperty("participantes", entity.participants)
            })
        }
        val key = "measure:$uuidValue"
        dao.deleteOutboxForEntity(key)
        dao.upsertOutbox(OutboxEntity(opUuid, key, "measure.upsert", gson.toJson(op)))
        return uuidValue
    }

    suspend fun deleteMeasure(measure: MeasureEntity) {
        if (measure.serverId == null) {
            dao.upsertMeasure(measure.copy(deleted = true, dirty = false))
            dao.deleteOutboxForEntity("measure:${measure.localUuid}")
            return
        }
        dao.upsertMeasure(measure.copy(deleted = true, dirty = true))
        val opUuid = UUID.randomUUID().toString()
        val op = JsonObject().apply {
            addProperty("uuid", opUuid)
            addProperty("kind", "measure.delete")
            addProperty("server_id", measure.serverId)
            addProperty("base_version", measure.serverVersion)
        }
        val key = "measure:${measure.localUuid}"
        dao.deleteOutboxForEntity(key)
        dao.upsertOutbox(OutboxEntity(opUuid, key, "measure.delete", gson.toJson(op)))
    }

    suspend fun synchronize(): SyncResult {
        val base = store.apiUrl ?: return SyncResult(false, "El móvil no está vinculado a PREVAR.")
        if (store.token.isNullOrBlank()) return SyncResult(false, "El token de vinculación no está disponible.")
        val api = apiFactory.create(base, authenticated = true)
        return try {
            val pending = dao.outbox(100)
            if (pending.isNotEmpty()) {
                val operations = pending.map { gson.fromJson(it.payloadJson, JsonObject::class.java) }
                val response = api.push(PushRequest(operations))
                if (!response.ok) error(response.error ?: "PREVAR rechazó los cambios pendientes.")
                dao.deleteOutbox(pending.map { it.uuid })
            }

            val listEnvelope = api.questionnaires()
            val remoteList = listEnvelope.data ?: error(listEnvelope.error ?: "No fue posible obtener los autocontroles.")
            dao.upsertQuestionnaires(remoteList.map { it.toEntity() })
            remoteList.forEach { dto ->
                val pullEnvelope = api.pull(dto.id)
                val pull = pullEnvelope.data ?: error(pullEnvelope.error ?: "No fue posible descargar el autocontrol ${dto.numero ?: dto.id}.")
                dao.replaceQuestionnaireSnapshot(
                    questionnaire = pull.questionnaire.toEntity(),
                    evaluations = pull.evaluations.map { it.toEntity() },
                    measures = pull.measures.map { it.toEntity() },
                    workers = pull.workers.map { WorkerEntity(it.id, it.nombre, it.cargo) }
                )
                dao.putState(SyncStateEntity("min_chars", pull.settings.minChars.toString()))
            }
            val now = java.time.OffsetDateTime.now().toString()
            dao.putState(SyncStateEntity("last_sync", now))
            dao.putState(SyncStateEntity("last_error", ""))
            dao.putState(SyncStateEntity("sync_conflict", ""))
            SyncResult(true, "Sincronización completada.")
        } catch (error: Exception) {
            val message = if (error is HttpException) {
                val body = parseHttpErrorObject(error)
                val apiError = body?.get("error")?.asString
                if (error.code() == 409) {
                    val stored = body?.let { storeEvaluationConflict(it) }
                    if (stored != null) {
                        "Conflicto pendiente: compare la versión de PREVAR Web con la del teléfono."
                    } else {
                        "Conflicto de sincronización: ${apiError ?: "el registro cambió en PREVAR"}"
                    }
                } else {
                    apiError ?: "PREVAR respondió HTTP ${error.code()}."
                }
            } else {
                syncFailureMessage(error)
            }
            dao.putState(SyncStateEntity("last_error", message))
            SyncResult(false, message)
        }
    }

    suspend fun resolveConflictKeepWeb(conflict: SyncConflict): SyncResult {
        // Descartar solo el cambio de la evaluación en conflicto.
        // Las medidas pendientes de la misma evaluación son operaciones independientes
        // y deben conservarse para la próxima sincronización.
        dao.deleteOutboxForEntity("evaluation:${conflict.evaluationId}")
        dao.putState(SyncStateEntity("sync_conflict", ""))
        dao.putState(SyncStateEntity("last_error", ""))
        return synchronize()
    }

    suspend fun resolveConflictKeepMobile(conflict: SyncConflict): SyncResult {
        val key = "evaluation:${conflict.evaluationId}"
        val pending = dao.outboxForEntity(key)
            ?: return SyncResult(false, "El cambio móvil pendiente ya no existe.")
        val rebased = rebaseEvaluationOperation(pending.payloadJson, conflict.serverVersion, gson)
        dao.upsertOutbox(pending.copy(payloadJson = rebased))
        dao.putState(SyncStateEntity("sync_conflict", ""))
        dao.putState(SyncStateEntity("last_error", ""))
        return synchronize()
    }

    private suspend fun storeEvaluationConflict(body: JsonObject): SyncConflict? {
        val payload = body.getAsJsonObject("conflict") ?: return null
        if (payload.get("entity_type")?.asString != "evaluation") return null
        val evaluationId = payload.get("evaluation_id")?.asLong ?: return null
        val local = dao.evaluationNow(evaluationId) ?: return null
        val server = payload.getAsJsonObject("server") ?: return null
        val conflict = SyncConflict(
            evaluationId = evaluationId,
            code = local.code,
            question = local.question,
            serverVersion = payload.get("server_version")?.asLong ?: local.serverVersion,
            web = conflictVersionFromJson(server),
            mobile = local.toConflictVersion()
        )
        dao.putState(SyncStateEntity("sync_conflict", gson.toJson(conflict)))
        return conflict
    }

    private fun conflictVersionFromJson(value: JsonObject) = ConflictVersion(
        result = value.get("evalua")?.asInt ?: 3,
        fundamentation = value.get("com")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        evidence = value.get("evidencia")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        causes = value.get("causas")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
        pca = (value.get("pca")?.asInt ?: 0) == 1,
        phd = (value.get("phd")?.asInt ?: 0) == 1,
        amount = value.get("cuantia")?.takeUnless { it.isJsonNull }?.asDouble
    )

    private fun EvaluationEntity.toConflictVersion() = ConflictVersion(
        result = result,
        fundamentation = fundamentation,
        evidence = evidence,
        causes = causes,
        pca = pca,
        phd = phd,
        amount = amount
    )

    private fun parseHttpErrorObject(error: HttpException): JsonObject? {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
    }

    private fun parseHttpError(error: HttpException): String? =
        parseHttpErrorObject(error)?.get("error")?.asString

    private fun QuestionnaireDto.toEntity() = QuestionnaireEntity(
        id = id,
        number = numero ?: id.toString(),
        commission = comision.orEmpty(),
        total = total,
        state = estado,
        evaluated = evaluadas,
        pending = pendientes,
        positive = positivas,
        negative = negativa,
        progress = poreje,
        date = fecha
    )

    private fun EvaluationDto.toEntity() = EvaluationEntity(
        id = evaluationId,
        questionnaireId = questionnaireId,
        gauId = gauId,
        generalQuestionId = generalQuestionId,
        code = codigo.orEmpty(),
        question = pregunta.orEmpty(),
        whatToEvaluate = qevalua.orEmpty(),
        suggestedFundamentation = funsi.orEmpty(),
        legislation = legislaso.orEmpty(),
        whatFails = seincum.orEmpty(),
        lastPositive = lastPositive,
        lastNegative = lastNegative,
        result = evalua,
        fundamentation = com.orEmpty(),
        evidence = evidencia.orEmpty(),
        causes = causas.orEmpty(),
        phd = phd == 1,
        pca = pca == 1,
        amount = cuantia,
        assignedWorker = trabajador,
        serverVersion = serverVersion,
        canEdit = canEdit,
        dirty = false
    )

    private fun MeasureDto.toEntity() = MeasureEntity(
        localUuid = "server-$measureId",
        serverId = measureId,
        evaluationId = evaluationId,
        text = medida,
        dueDate = fcc,
        responsibleId = responsable,
        participants = participantes.orEmpty(),
        serverVersion = serverVersion,
        dirty = false
    )
}

data class ConflictVersion(
    val result: Int,
    val fundamentation: String,
    val evidence: String,
    val causes: String,
    val pca: Boolean,
    val phd: Boolean,
    val amount: Double?
)

data class SyncConflict(
    val evaluationId: Long,
    val code: String,
    val question: String,
    val serverVersion: Long,
    val web: ConflictVersion,
    val mobile: ConflictVersion
)

internal fun rebaseEvaluationOperation(payloadJson: String, serverVersion: Long, gson: Gson = Gson()): String {
    val payload = gson.fromJson(payloadJson, JsonObject::class.java)
    require(payload.get("kind")?.asString == "evaluation.update") {
        "La operación pendiente no corresponde a una evaluación."
    }
    payload.addProperty("base_version", serverVersion)
    return gson.toJson(payload)
}

internal fun syncFailureMessage(error: Throwable): String =
    if (error is java.io.IOException) {
        "Sin conexión con PREVAR. Los cambios quedan guardados en el teléfono y se enviarán cuando vuelva la conectividad."
    } else {
        error.message ?: "No fue posible sincronizar."
    }

data class SyncResult(val success: Boolean, val message: String)
