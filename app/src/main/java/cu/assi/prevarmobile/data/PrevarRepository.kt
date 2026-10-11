package cu.assi.prevarmobile.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import cu.assi.prevarmobile.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import cu.assi.prevarmobile.data.local.*
import cu.assi.prevarmobile.data.remote.*
import cu.assi.prevarmobile.domain.AttachmentPolicy
import cu.assi.prevarmobile.domain.EvaluationValidator
import cu.assi.prevarmobile.domain.RiskMatrix
import cu.assi.prevarmobile.domain.ServerUrlPolicy
import cu.assi.prevarmobile.security.AttachmentVault
import cu.assi.prevarmobile.security.PinnedCaTrust
import cu.assi.prevarmobile.security.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import retrofit2.HttpException

class PrevarRepository(
    private val context: Context,
    private val dao: PrevarDao,
    private val store: SecureStore,
    private val apiFactory: ApiFactory,
    private val gson: Gson = Gson()
) {
    private val attachmentVault = AttachmentVault(context)

    val questionnaires: Flow<List<QuestionnaireEntity>> = dao.questionnaires()
    val pendingCount: Flow<Int> = combine(dao.pendingCount(), dao.pendingArtifactCount()) { outbox, artifacts ->
        outbox + artifacts
    }
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
    fun attachments(evaluationId: Long): Flow<List<AttachmentEntity>> = dao.attachments(evaluationId)
    fun riskDrafts(evaluationId: Long): Flow<List<RiskDraftEntity>> = dao.riskDrafts(evaluationId)
    fun workers(): Flow<List<WorkerEntity>> = dao.workers()

    fun isPaired(): Boolean = !store.apiUrl.isNullOrBlank() && !store.token.isNullOrBlank()
    fun userName(): String = store.userName.orEmpty()
    fun apiUrl(): String = store.apiUrl.orEmpty()

    suspend fun pair(apiUrl: String, secret: String? = null, code: String? = null) {
        val normalized = ServerUrlPolicy.normalize(apiUrl, allowPrivateHttp = BuildConfig.DEBUG)
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
        val normalized = ServerUrlPolicy.normalize(api, allowPrivateHttp = BuildConfig.DEBUG)
        val secret = objectJson.get("secret")?.asString
        val code = objectJson.get("code")?.asString
        val tlsCaSha256 = objectJson.get("tls_ca_sha256")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val tlsCaUrl = objectJson.get("tls_ca_url")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        require(tlsCaSha256.isBlank() == tlsCaUrl.isBlank()) {
            "El QR de PREVAR contiene información TLS incompleta."
        }

        val previousCa = store.serverCaDerBase64
        try {
            store.serverCaDerBase64 = if (tlsCaSha256.isNotBlank()) {
                val der = withContext(Dispatchers.IO) {
                    PinnedCaTrust.downloadVerifiedCa(normalized, tlsCaUrl, tlsCaSha256)
                }
                Base64.getEncoder().encodeToString(der)
            } else {
                null
            }
            pair(normalized, secret, code)
        } catch (error: Throwable) {
            store.serverCaDerBase64 = previousCa
            throw error
        }
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

    suspend fun importAttachment(evaluationId: Long, uri: Uri): AttachmentEntity {
        require(dao.evaluationNow(evaluationId) != null) { "La evaluación ya no está disponible." }
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        AttachmentPolicy.validateMime(mime)

        var displayName = "adjunto"
        var declaredSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "adjunto" }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
            }
        }
        declaredSize?.let(AttachmentPolicy::validateSize)

        val uuid = UUID.randomUUID().toString()
        val safeName = AttachmentPolicy.sanitizeFileName(displayName)
        val destination = File(attachmentsDirectory(), "${uuid}.enc")
        val stored = resolver.openInputStream(uri)?.use {
            attachmentVault.store(it, destination, AttachmentPolicy.MAX_BYTES)
        } ?: error("No fue posible leer el adjunto seleccionado.")

        return AttachmentEntity(
            localUuid = uuid,
            evaluationId = evaluationId,
            fileName = safeName,
            mimeType = mime,
            localPath = stored.file.absolutePath,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256
        ).also { dao.upsertAttachment(it) }
    }

    fun preparePhotoCapture(): PendingPhotoCapture {
        val uuid = UUID.randomUUID().toString()
        val cameraDir = File(context.cacheDir, "prevar_camera").apply { mkdirs() }
        val file = File(cameraDir, "${uuid}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return PendingPhotoCapture(uuid, file.absolutePath, uri)
    }

    fun discardPhotoCapture(capture: PendingPhotoCapture) {
        runCatching { File(capture.localPath).delete() }
    }

    suspend fun commitCapturedPhoto(evaluationId: Long, capture: PendingPhotoCapture): AttachmentEntity {
        require(dao.evaluationNow(evaluationId) != null) { "La evaluación ya no está disponible." }
        val source = File(capture.localPath)
        require(source.exists() && source.length() > 0) { "La cámara no generó una fotografía válida." }
        AttachmentPolicy.validateSize(source.length())

        val destination = File(attachmentsDirectory(), "${capture.localUuid}.enc")
        val stored = try {
            attachmentVault.storeFile(source, destination, AttachmentPolicy.MAX_BYTES)
        } finally {
            source.delete()
        }
        val label = "Foto_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".jpg"
        return AttachmentEntity(
            localUuid = capture.localUuid,
            evaluationId = evaluationId,
            fileName = label,
            mimeType = "image/jpeg",
            localPath = stored.file.absolutePath,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256
        ).also { dao.upsertAttachment(it) }
    }

    suspend fun attachmentUri(attachment: AttachmentEntity): Uri {
        var current = dao.attachmentNow(attachment.localUuid) ?: attachment
        val existing = current.localPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { safeAttachmentFile(path) }.getOrNull()?.takeIf { it.isFile }
        }
        if (existing == null) {
            val base = store.apiUrl ?: error("El móvil no está vinculado a PREVAR.")
            if (store.token.isNullOrBlank()) error("El token de vinculación no está disponible.")
            val api = apiFactory.create(base, authenticated = true)
            val response = api.downloadAttachment(current.localUuid)
            if (!response.isSuccessful) error("PREVAR no pudo descargar el adjunto (HTTP ${response.code()}).")
            val body = response.body() ?: error("PREVAR devolvió un adjunto vacío.")
            val destination = File(attachmentsDirectory(), "${current.localUuid}.enc")
            val stored = body.byteStream().use {
                attachmentVault.store(it, destination, AttachmentPolicy.MAX_BYTES)
            }
            require(stored.sha256.equals(current.sha256, ignoreCase = true)) {
                destination.delete()
                "El adjunto descargado no supera la verificación de integridad."
            }
            current = current.copy(
                localPath = stored.file.absolutePath,
                sizeBytes = stored.sizeBytes,
                sha256 = stored.sha256,
                dirty = false,
                deleted = false
            )
            dao.upsertAttachment(current)
        }
        val encrypted = safeAttachmentFile(current.localPath)
        return attachmentVault.decryptedUri(
            encrypted,
            "${current.localUuid}_${AttachmentPolicy.sanitizeFileName(current.fileName)}"
        )
    }

    suspend fun deleteAttachment(attachment: AttachmentEntity) {
        val current = dao.attachmentNow(attachment.localUuid) ?: attachment
        if (current.serverVersion <= 0L) {
            current.localPath.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { safeAttachmentFile(path).delete() }
            }
            dao.deleteAttachment(current.localUuid)
        } else {
            dao.upsertAttachment(
                current.copy(
                    dirty = true,
                    deleted = true
                )
            )
        }
    }

    suspend fun saveRiskDraft(
        evaluationId: Long,
        objectText: String,
        riskText: String,
        probability: Int,
        impact: Int,
        argument: String,
        localUuid: String? = null
    ): RiskDraftEntity {
        require(dao.evaluationNow(evaluationId) != null) { "La evaluación ya no está disponible." }
        require(objectText.trim().length >= 5) { "Describa el objeto u objetivo de control." }
        require(riskText.trim().length >= 5) { "Describa el riesgo identificado." }
        val assessment = RiskMatrix.assess(probability, impact)
        val uuidValue = localUuid ?: UUID.randomUUID().toString()
        val current = localUuid?.let { dao.riskDraftNow(it) }
        val entity = RiskDraftEntity(
            localUuid = uuidValue,
            evaluationId = evaluationId,
            objectText = objectText.trim(),
            riskText = riskText.trim(),
            probability = probability,
            impact = impact,
            result = assessment.result,
            level = assessment.level,
            argument = argument.trim(),
            serverVersion = current?.serverVersion ?: 0,
            dirty = true,
            deleted = false,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertRiskDraft(entity)
        return entity
    }

    suspend fun deleteRiskDraft(draft: RiskDraftEntity) {
        val current = dao.riskDraftNow(draft.localUuid) ?: draft
        if (current.serverVersion <= 0L) {
            dao.deleteRiskDraft(current.localUuid)
        } else {
            dao.upsertRiskDraft(
                current.copy(
                    dirty = true,
                    deleted = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun attachmentsDirectory(): File =
        File(context.filesDir, "prevar_attachments").apply { if (!exists()) mkdirs() }

    private fun safeAttachmentFile(path: String): File {
        val root = attachmentsDirectory().canonicalFile
        val target = File(path).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "Ruta de adjunto no válida." }
        return target
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

            syncRiskDrafts(api)
            syncAttachments(api)

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
                mergeRemoteRiskDrafts(pull.riskDrafts)
                mergeRemoteAttachments(pull.attachments)
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

    private suspend fun syncRiskDrafts(api: PrevarApi) {
        val dirty = dao.dirtyRiskDrafts()
        if (dirty.isEmpty()) return
        val opByUuid = linkedMapOf<String, RiskDraftEntity>()
        val operations = dirty.map { draft ->
            val opUuid = UUID.nameUUIDFromBytes(
                "risk:${draft.localUuid}:${draft.updatedAt}:${draft.deleted}".toByteArray(Charsets.UTF_8)
            ).toString()
            opByUuid[opUuid] = draft
            JsonObject().apply {
                addProperty("uuid", opUuid)
                addProperty("kind", if (draft.deleted) "risk_draft.delete" else "risk_draft.upsert")
                addProperty("mobile_uuid", draft.localUuid)
                addProperty("evaluation_id", draft.evaluationId)
                if (draft.serverVersion > 0) addProperty("base_version", draft.serverVersion)
                if (!draft.deleted) {
                    add("fields", JsonObject().apply {
                        addProperty("object_text", draft.objectText)
                        addProperty("risk_text", draft.riskText)
                        addProperty("probability", draft.probability)
                        addProperty("impact", draft.impact)
                        addProperty("argument", draft.argument)
                    })
                }
            }
        }
        val envelope = api.push(PushRequest(operations))
        val data = envelope.data ?: error(envelope.error ?: "PREVAR rechazó los borradores de riesgo.")
        val resultByUuid = data.results.associateBy { it.uuid }
        opByUuid.forEach { (opUuid, draft) ->
            val result = resultByUuid[opUuid] ?: error("PREVAR no confirmó el borrador de riesgo ${draft.localUuid}.")
            if (draft.deleted) {
                dao.deleteRiskDraft(draft.localUuid)
            } else {
                dao.upsertRiskDraft(
                    draft.copy(
                        serverVersion = result.serverVersion ?: maxOf(1L, draft.serverVersion),
                        dirty = false,
                        deleted = false
                    )
                )
            }
        }
    }

    private suspend fun syncAttachments(api: PrevarApi) {
        dao.dirtyAttachments().forEach { attachment ->
            if (attachment.deleted) {
                if (attachment.serverVersion > 0) {
                    val envelope = api.deleteAttachment(attachment.localUuid)
                    if (!envelope.ok) error(envelope.error ?: "PREVAR rechazó la eliminación del adjunto.")
                }
                attachment.localPath.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { safeAttachmentFile(path).delete() }
                }
                dao.deleteAttachment(attachment.localUuid)
            } else {
                val encrypted = safeAttachmentFile(attachment.localPath)
                val clear = attachmentVault.decryptedFile(
                    encrypted,
                    "upload_${attachment.localUuid}_${AttachmentPolicy.sanitizeFileName(attachment.fileName)}"
                )
                try {
                    val body = clear.asRequestBody(attachment.mimeType.toMediaType())
                    val envelope = api.uploadAttachment(
                        evaluationId = attachment.evaluationId,
                        mobileUuid = attachment.localUuid,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        sha256 = attachment.sha256,
                        body = body
                    )
                    val data = envelope.data ?: error(envelope.error ?: "PREVAR rechazó el adjunto.")
                    dao.upsertAttachment(
                        attachment.copy(
                            serverVersion = data.serverVersion,
                            dirty = false,
                            deleted = false
                        )
                    )
                } finally {
                    clear.delete()
                }
            }
        }
    }

    private suspend fun mergeRemoteRiskDrafts(remote: List<RiskDraftDto>) {
        remote.groupBy { it.evaluationId }.forEach { (evaluationId, rows) ->
            val local = dao.riskDraftsNow(evaluationId)
            val remoteIds = rows.map { it.mobileUuid }.toSet()
            local.filter { !it.dirty && it.serverVersion > 0 && it.localUuid !in remoteIds }
                .forEach { dao.deleteRiskDraft(it.localUuid) }
            rows.forEach { dto ->
                val current = dao.riskDraftNow(dto.mobileUuid)
                if (current?.dirty == true) return@forEach
                dao.upsertRiskDraft(
                    RiskDraftEntity(
                        localUuid = dto.mobileUuid,
                        evaluationId = dto.evaluationId,
                        objectText = dto.objectText,
                        riskText = dto.riskText,
                        probability = dto.probability,
                        impact = dto.impact,
                        result = dto.result,
                        level = dto.level,
                        argument = dto.argument.orEmpty(),
                        serverVersion = dto.version,
                        dirty = false,
                        deleted = false,
                        updatedAt = current?.updatedAt ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private suspend fun mergeRemoteAttachments(remote: List<AttachmentDto>) {
        remote.groupBy { it.evaluationId }.forEach { (evaluationId, rows) ->
            val local = dao.attachmentsNow(evaluationId)
            val remoteIds = rows.map { it.mobileUuid }.toSet()
            local.filter { !it.dirty && it.serverVersion > 0 && it.localUuid !in remoteIds }.forEach { row ->
                row.localPath.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { safeAttachmentFile(path).delete() }
                }
                dao.deleteAttachment(row.localUuid)
            }
            rows.forEach { dto ->
                val current = dao.attachmentNow(dto.mobileUuid)
                if (current?.dirty == true) return@forEach
                dao.upsertAttachment(
                    AttachmentEntity(
                        localUuid = dto.mobileUuid,
                        evaluationId = dto.evaluationId,
                        fileName = dto.fileName,
                        mimeType = dto.mimeType,
                        localPath = current?.localPath.orEmpty(),
                        sizeBytes = dto.sizeBytes,
                        sha256 = dto.sha256,
                        serverVersion = dto.version,
                        dirty = false,
                        deleted = false,
                        createdAt = current?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }
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

data class PendingPhotoCapture(
    val localUuid: String,
    val localPath: String,
    val uri: Uri
)

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
