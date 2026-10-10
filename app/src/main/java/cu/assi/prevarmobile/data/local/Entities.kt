package cu.assi.prevarmobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questionnaire")
data class QuestionnaireEntity(
    @PrimaryKey val id: Long,
    val number: String,
    val commission: String,
    val total: Int,
    val state: Int,
    val evaluated: Int,
    val pending: Int,
    val positive: Int,
    val negative: Int,
    val progress: Double,
    val date: String?,
    val lastPulledAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evaluation")
data class EvaluationEntity(
    @PrimaryKey val id: Long,
    val questionnaireId: Long,
    val gauId: Long,
    val generalQuestionId: Long?,
    val code: String,
    val question: String,
    val whatToEvaluate: String,
    val suggestedFundamentation: String,
    val legislation: String,
    val whatFails: String,
    val lastPositive: String?,
    val lastNegative: String?,
    val result: Int,
    val fundamentation: String,
    val evidence: String,
    val causes: String,
    val phd: Boolean,
    val pca: Boolean,
    val amount: Double?,
    val assignedWorker: Long?,
    val serverVersion: Long,
    val canEdit: Boolean,
    val dirty: Boolean = false
)

@Entity(tableName = "measure")
data class MeasureEntity(
    @PrimaryKey val localUuid: String,
    val serverId: Long?,
    val evaluationId: Long,
    val text: String,
    val dueDate: String,
    val responsibleId: Long,
    val participants: String,
    val serverVersion: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = false
)

@Entity(tableName = "worker")
data class WorkerEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val position: String?
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val uuid: String,
    val entityKey: String,
    val kind: String,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String
)


@Entity(tableName = "attachment")
data class AttachmentEntity(
    @PrimaryKey val localUuid: String,
    val evaluationId: Long,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "risk_draft")
data class RiskDraftEntity(
    @PrimaryKey val localUuid: String,
    val evaluationId: Long,
    val objectText: String,
    val riskText: String,
    val probability: Int,
    val impact: Int,
    val result: Int,
    val level: Int,
    val argument: String,
    val updatedAt: Long = System.currentTimeMillis()
)
