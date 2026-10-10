package cu.assi.prevarmobile.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PrevarDao {
    @Query("SELECT * FROM questionnaire ORDER BY date DESC, id DESC")
    fun questionnaires(): Flow<List<QuestionnaireEntity>>

    @Query("SELECT * FROM questionnaire WHERE id=:id LIMIT 1")
    suspend fun questionnaire(id: Long): QuestionnaireEntity?

    @Query("SELECT * FROM evaluation WHERE questionnaireId=:questionnaireId ORDER BY code, id")
    fun evaluations(questionnaireId: Long): Flow<List<EvaluationEntity>>

    @Query("SELECT * FROM evaluation WHERE id=:id LIMIT 1")
    fun evaluation(id: Long): Flow<EvaluationEntity?>

    @Query("SELECT * FROM evaluation WHERE id=:id LIMIT 1")
    suspend fun evaluationNow(id: Long): EvaluationEntity?

    @Query("SELECT * FROM measure WHERE evaluationId=:evaluationId AND deleted=0 ORDER BY dueDate, localUuid")
    fun measures(evaluationId: Long): Flow<List<MeasureEntity>>

    @Query("SELECT * FROM measure WHERE evaluationId=:evaluationId AND deleted=0")
    suspend fun measuresNow(evaluationId: Long): List<MeasureEntity>

    @Query("SELECT * FROM worker ORDER BY name")
    fun workers(): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM attachment WHERE evaluationId=:evaluationId AND deleted=0 ORDER BY createdAt DESC")
    fun attachments(evaluationId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE evaluationId=:evaluationId")
    suspend fun attachmentsNow(evaluationId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachment WHERE localUuid=:localUuid LIMIT 1")
    suspend fun attachmentNow(localUuid: String): AttachmentEntity?

    @Query("SELECT * FROM attachment WHERE dirty=1 ORDER BY createdAt")
    suspend fun dirtyAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM risk_draft WHERE evaluationId=:evaluationId AND deleted=0 ORDER BY updatedAt DESC")
    fun riskDrafts(evaluationId: Long): Flow<List<RiskDraftEntity>>

    @Query("SELECT * FROM risk_draft WHERE evaluationId=:evaluationId")
    suspend fun riskDraftsNow(evaluationId: Long): List<RiskDraftEntity>

    @Query("SELECT * FROM risk_draft WHERE localUuid=:localUuid LIMIT 1")
    suspend fun riskDraftNow(localUuid: String): RiskDraftEntity?

    @Query("SELECT * FROM risk_draft WHERE dirty=1 ORDER BY updatedAt")
    suspend fun dirtyRiskDrafts(): List<RiskDraftEntity>

    @Query("SELECT (SELECT COUNT(*) FROM attachment WHERE dirty=1) + (SELECT COUNT(*) FROM risk_draft WHERE dirty=1)")
    fun pendingArtifactCount(): Flow<Int>

    @Query("SELECT * FROM worker ORDER BY name")
    suspend fun workersNow(): List<WorkerEntity>

    @Query("SELECT * FROM outbox ORDER BY createdAt LIMIT :limit")
    suspend fun outbox(limit: Int = 100): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE entityKey=:entityKey LIMIT 1")
    suspend fun outboxForEntity(entityKey: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM outbox")
    fun pendingCount(): Flow<Int>

    @Query("SELECT value FROM sync_state WHERE `key`=:key LIMIT 1")
    fun stateFlow(key: String): Flow<String?>

    @Query("SELECT value FROM sync_state WHERE `key`=:key LIMIT 1")
    suspend fun state(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestionnaires(rows: List<QuestionnaireEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvaluations(rows: List<EvaluationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasures(rows: List<MeasureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkers(rows: List<WorkerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(row: OutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(row: SyncStateEntity)

    @Update
    suspend fun updateEvaluation(row: EvaluationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasure(row: MeasureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(row: AttachmentEntity)

    @Query("DELETE FROM attachment WHERE localUuid=:localUuid")
    suspend fun deleteAttachment(localUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRiskDraft(row: RiskDraftEntity)

    @Query("DELETE FROM risk_draft WHERE localUuid=:localUuid")
    suspend fun deleteRiskDraft(localUuid: String)

    @Query("DELETE FROM outbox WHERE uuid IN (:uuids)")
    suspend fun deleteOutbox(uuids: List<String>)

    @Query("DELETE FROM outbox WHERE entityKey=:entityKey")
    suspend fun deleteOutboxForEntity(entityKey: String)

    @Query("DELETE FROM measure WHERE evaluationId IN (SELECT id FROM evaluation WHERE questionnaireId=:questionnaireId)")
    suspend fun clearMeasuresForQuestionnaire(questionnaireId: Long)

    @Query("DELETE FROM evaluation WHERE questionnaireId=:questionnaireId")
    suspend fun clearEvaluationsForQuestionnaire(questionnaireId: Long)

    @Transaction
    suspend fun replaceQuestionnaireSnapshot(
        questionnaire: QuestionnaireEntity,
        evaluations: List<EvaluationEntity>,
        measures: List<MeasureEntity>,
        workers: List<WorkerEntity>
    ) {
        clearMeasuresForQuestionnaire(questionnaire.id)
        clearEvaluationsForQuestionnaire(questionnaire.id)
        upsertQuestionnaires(listOf(questionnaire))
        upsertEvaluations(evaluations)
        upsertMeasures(measures)
        upsertWorkers(workers)
    }
}
