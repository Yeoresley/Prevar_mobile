package cu.assi.prevarmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.integration.android.ScanIntentResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import cu.assi.prevarmobile.data.PrevarRepository
import cu.assi.prevarmobile.data.local.EvaluationEntity
import cu.assi.prevarmobile.data.local.MeasureEntity
import cu.assi.prevarmobile.data.local.QuestionnaireEntity
import cu.assi.prevarmobile.data.local.WorkerEntity
import cu.assi.prevarmobile.domain.EvaluationValidator
import cu.assi.prevarmobile.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PrevarMobileApp
        SyncScheduler.schedule(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrevarMobileRoot(app.repository)
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Questions(val questionnaireId: Long, val title: String) : Screen
    data class Evaluation(val evaluationId: Long, val questionnaireId: Long, val questionnaireTitle: String) : Screen
}

@Composable
private fun PrevarMobileRoot(repository: PrevarRepository) {
    var paired by remember { mutableStateOf(repository.isPaired()) }
    if (!paired) {
        PairingScreen(repository) { paired = true }
        return
    }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val current = screen) {
        Screen.Home -> HomeScreen(repository,
            onQuestionnaire = { screen = Screen.Questions(it.id, it.number) },
            onUnpair = { scope.launch { repository.unpair(); paired = false } }
        )
        is Screen.Questions -> QuestionsScreen(
            repository = repository,
            questionnaireId = current.questionnaireId,
            title = current.title,
            onBack = { screen = Screen.Home },
            onEvaluation = { screen = Screen.Evaluation(it.id, current.questionnaireId, current.title) }
        )
        is Screen.Evaluation -> EvaluationScreen(
            repository = repository,
            evaluationId = current.evaluationId,
            title = current.questionnaireTitle,
            onBack = { screen = Screen.Questions(current.questionnaireId, current.questionnaireTitle) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingScreen(repository: PrevarRepository, onPaired: () -> Unit) {
    val scope = rememberCoroutineScope()
    var apiUrl by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val content = result.contents ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { repository.pairFromQr(content) }
                .onSuccess { onPaired() }
                .onFailure { message = it.message ?: "No fue posible vincular el dispositivo." }
            busy = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("PREVAR Mobile") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Vincular dispositivo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("En PREVAR Web abra el icono del teléfono y genere un QR temporal.")
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Escanee el QR mostrado por PREVAR")
                            .setBeepEnabled(false)
                            .setOrientationLocked(true)
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Escanear QR de PREVAR") }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Text("Vinculación manual", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("Servidor (…/mobile-api/v1)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Código de 8 caracteres") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { repository.pair(apiUrl, code = code) }
                            .onSuccess { onPaired() }
                            .onFailure { message = it.message ?: "No fue posible vincular el dispositivo." }
                        busy = false
                    }
                },
                enabled = !busy && apiUrl.isNotBlank() && code.length >= 6,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Vincular con código") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    repository: PrevarRepository,
    onQuestionnaire: (QuestionnaireEntity) -> Unit,
    onUnpair: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val questionnaires by repository.questionnaires.collectAsStateWithLifecycle(emptyList())
    val pending by repository.pendingCount.collectAsStateWithLifecycle(0)
    val lastSync by repository.lastSync.collectAsStateWithLifecycle(null)
    val lastError by repository.lastError.collectAsStateWithLifecycle(null)
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("PREVAR Mobile"); Text(repository.userName(), style = MaterialTheme.typography.labelSmall) } },
                actions = { TextButton(onClick = onUnpair) { Text("Desvincular") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    syncing = true
                    scope.launch {
                        val result = repository.synchronize()
                        status = result.message
                        syncing = false
                    }
                },
                text = { Text(if (syncing) "Sincronizando…" else "Sincronizar") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.padding(16.dp)) {
                Text("Cambios pendientes: $pending", fontWeight = FontWeight.SemiBold)
                Text("Última sincronización: ${lastSync ?: "todavía no realizada"}", style = MaterialTheme.typography.bodySmall)
                if (!lastError.isNullOrBlank()) Text("Error: $lastError", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
            if (questionnaires.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sincronice para descargar los autocontroles preparados en PREVAR.")
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(questionnaires, key = { it.id }) { q ->
                        ListItem(
                            headlineContent = { Text("Cuestionario ${q.number}", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${q.commission}\n${q.evaluated}/${q.total} evaluadas · ${q.pending} pendientes") },
                            trailingContent = { Text("${q.progress.toInt()}%") },
                            modifier = Modifier.clickable { onQuestionnaire(q) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionsScreen(
    repository: PrevarRepository,
    questionnaireId: Long,
    title: String,
    onBack: () -> Unit,
    onEvaluation: (EvaluationEntity) -> Unit
) {
    val evaluations by repository.evaluations(questionnaireId).collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf(3) }
    val shown = remember(evaluations, filter) {
        when (filter) {
            1 -> evaluations.filter { it.result == 1 }
            2 -> evaluations.filter { it.result == 2 }
            3 -> evaluations.filter { it.result == 3 }
            else -> evaluations
        }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Autocontrol $title") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(12.dp).fillMaxWidth()) {
                listOf(0 to "Todas", 3 to "Pendientes", 1 to "Cumple", 2 to "No cumple").forEachIndexed { index, pair ->
                    SegmentedButton(
                        selected = filter == pair.first,
                        onClick = { filter = pair.first },
                        shape = SegmentedButtonDefaults.itemShape(index, 4)
                    ) { Text(pair.second) }
                }
            }
            LazyColumn {
                items(shown, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text("${e.code}  ${e.question}") },
                        supportingContent = { Text(resultLabel(e.result) + if (!e.canEdit) " · Solo lectura" else "") },
                        modifier = Modifier.clickable { onEvaluation(e) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun resultLabel(result: Int) = when (result) {
    1 -> "Cumple"
    2 -> "No cumple"
    else -> "Pendiente"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvaluationScreen(
    repository: PrevarRepository,
    evaluationId: Long,
    title: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val evaluation by repository.evaluation(evaluationId).collectAsStateWithLifecycle(null)
    val measures by repository.measures(evaluationId).collectAsStateWithLifecycle(emptyList())
    val workers by repository.workers().collectAsStateWithLifecycle(emptyList())
    val current = evaluation
    if (current == null) {
        Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    var result by remember(current.id) { mutableIntStateOf(current.result) }
    var fundamentation by remember(current.id) { mutableStateOf(current.fundamentation) }
    var evidence by remember(current.id) { mutableStateOf(current.evidence) }
    var causes by remember(current.id) { mutableStateOf(current.causes) }
    var pca by remember(current.id) { mutableStateOf(current.pca) }
    var phd by remember(current.id) { mutableStateOf(current.phd) }
    var amount by remember(current.id) { mutableStateOf(current.amount?.toString().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var showMeasureDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(current.code.ifBlank { title }) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(current.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (current.whatToEvaluate.isNotBlank()) item { InfoBlock("¿Qué evaluar?", current.whatToEvaluate) }
            if (current.legislation.isNotBlank()) item { InfoBlock("Legislación asociada", current.legislation) }
            item {
                Text("Resultado", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = result == 1, onClick = { result = 1 }, label = { Text("Cumple") }, enabled = current.canEdit)
                    FilterChip(selected = result == 2, onClick = { result = 2 }, label = { Text("No cumple") }, enabled = current.canEdit)
                    FilterChip(selected = result == 3, onClick = { result = 3 }, label = { Text("Pendiente") }, enabled = current.canEdit)
                }
            }
            if (result != 3) {
                item {
                    OutlinedTextField(
                        fundamentation,
                        { fundamentation = it },
                        label = { Text("Fundamentación") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = current.canEdit
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (!current.lastPositive.isNullOrBlank() && result == 1) TextButton(onClick = { fundamentation = current.lastPositive }) { Text("Usar última positiva") }
                        if (!current.lastNegative.isNullOrBlank() && result == 2) TextButton(onClick = { fundamentation = current.lastNegative }) { Text("Usar última negativa") }
                        if (current.suggestedFundamentation.isNotBlank()) TextButton(onClick = { fundamentation = current.suggestedFundamentation }) { Text("Usar sugerencia") }
                    }
                }
                item {
                    OutlinedTextField(evidence, { evidence = it }, label = { Text("Evidencia / referencia documental") }, minLines = 2, modifier = Modifier.fillMaxWidth(), enabled = current.canEdit)
                }
            }
            if (result == 2) {
                if (current.whatFails.isNotBlank()) item { InfoBlock("¿Qué se incumple?", current.whatFails) }
                item { OutlinedTextField(causes, { causes = it }, label = { Text("Causas y condiciones") }, minLines = 4, modifier = Modifier.fillMaxWidth(), enabled = current.canEdit) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pca, onCheckedChange = { pca = it }, enabled = current.canEdit); Text("PCA")
                        Spacer(Modifier.width(16.dp))
                        Checkbox(checked = phd, onCheckedChange = { phd = it }, enabled = current.canEdit); Text("PHD")
                    }
                    OutlinedTextField(
                        amount,
                        { amount = it },
                        label = { Text("Cuantía (si aplica)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = current.canEdit
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Medidas / acciones (${measures.size})", fontWeight = FontWeight.SemiBold)
                        Button(onClick = { showMeasureDialog = true }, enabled = current.canEdit) { Text("Adicionar") }
                    }
                }
                items(measures, key = { it.localUuid }) { measure ->
                    MeasureCard(measure, workers) { scope.launch { repository.deleteMeasure(measure) } }
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val errors = repository.saveEvaluation(
                                current.id, result, fundamentation, evidence, causes, pca, phd,
                                amount.replace(',', '.').toDoubleOrNull()
                            )
                            message = if (errors.isEmpty()) "Guardado en el teléfono. Se enviará al sincronizar." else errors.joinToString("\n")
                        }
                    },
                    enabled = current.canEdit,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar offline") }
            }
            message?.let { item { Text(it, color = if (it.startsWith("Guardado")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } }
        }
    }

    if (showMeasureDialog) {
        MeasureDialog(
            workers = workers,
            onDismiss = { showMeasureDialog = false },
            onSave = { text, due, responsible ->
                scope.launch {
                    runCatching { repository.saveMeasure(evaluationId = current.id, text = text, dueDate = due, responsibleId = responsible, participants = emptyList()) }
                        .onSuccess { showMeasureDialog = false; message = "Medida guardada offline." }
                        .onFailure { message = it.message }
                }
            }
        )
    }
}

@Composable
private fun InfoBlock(title: String, text: String) {
    Card { Column(Modifier.padding(12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(text) } }
}

@Composable
private fun MeasureCard(measure: MeasureEntity, workers: List<WorkerEntity>, onDelete: () -> Unit) {
    val responsible = workers.firstOrNull { it.id == measure.responsibleId }?.name ?: measure.responsibleId.toString()
    Card {
        Column(Modifier.padding(12.dp)) {
            Text(measure.text)
            Text("Responsable: $responsible", style = MaterialTheme.typography.bodySmall)
            Text("Cumplimiento: ${measure.dueDate}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasureDialog(workers: List<WorkerEntity>, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    var responsible by remember { mutableLongStateOf(workers.firstOrNull()?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva medida / acción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Medida o acción") }, minLines = 3)
                OutlinedTextField(due, { due = it }, label = { Text("Fecha YYYY-MM-DD") })
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = workers.firstOrNull { it.id == responsible }?.name.orEmpty(),
                        onValueChange = {}, readOnly = true, label = { Text("Responsable") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        workers.forEach { worker ->
                            DropdownMenuItem(text = { Text(worker.name) }, onClick = { responsible = worker.id; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, due, responsible) }, enabled = text.length >= 5 && responsible > 0) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
