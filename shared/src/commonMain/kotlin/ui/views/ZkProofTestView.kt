package ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ui.composables.Logo
import ui.composables.buttons.NavigateUpButton
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZkProofTestView(
    navigateUp: () -> Unit,
    onClickLogo: () -> Unit,
    onClickSettings: () -> Unit,
) {
    // Mock стан (поки без ViewModel)
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var generatedProof by remember { mutableStateOf<String?>(null) }
    var nullifierHash by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Mock функція генерації
    fun generateProof() {
        isGenerating = true
        errorMessage = null
        generatedProof = null
        progress = 0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ZK Proof Test",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Logo(onClick = onClickLogo)
                    IconButton(onClick = onClickSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
                navigationIcon = {
                    NavigateUpButton(navigateUp)
                }
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // Секція: Збережені дані користувача
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "👤 Your Profile Data",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    // Mock дані (потім замінимо на реальні)
                    ProfileDataRow("Name:", "Not set yet")
                    ProfileDataRow("Age:", "Not set yet")
                    ProfileDataRow("Country:", "Not set yet")
                    ProfileDataRow("Identity Number:", "Not set yet")

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "ℹ️ Fill your profile data from Home screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Секція: Тестовий запит
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "📋 Test Survey Requirements",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    RequirementRow("Survey ID:", "test_survey_001")
                    RequirementRow("Min Age:", "18 years", satisfied = true)
                    RequirementRow("Allowed Countries:", "SK, CZ, AT", satisfied = true)
                    RequirementRow("Min Education:", "High School", satisfied = true)
                }
            }

            // Privacy notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(Modifier.padding(16.dp)) {
                    Text("🔒 ", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Privacy Protected",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Your personal data will NOT be shared. Only a cryptographic proof will be generated.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Кнопка генерації
            Button(
                onClick = { generateProof() },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    if (isGenerating) "Generating..." else "Generate ZK Proof",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Progress bar
            if (isGenerating) {
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Progress: ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "⏱️ Estimated time: ${((1 - progress) * 30).toInt()} seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Результат
            generatedProof?.let { proof ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "✅ Proof Generated Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Proof:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            proof,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text("Nullifier Hash:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            nullifierHash ?: "N/A",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text("Public Signals:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "[\"signal1\", \"signal2\", \"signal3\"]",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { /* TODO: Copy to clipboard */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📋 Copy Result")
                        }
                    }
                }
            }

            // Помилка
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "❌ Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Симуляція прогресу (поки без реальної генерації)
    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            for (i in 1..100) {
                delay(30)
                progress = i / 100f
            }

            // Mock результат
            generatedProof = "MOCK_PROOF_0x${(1..64).joinToString("") { "0123456789abcdef".random().toString() }}"
            nullifierHash = "0x${(1..64).joinToString("") { "0123456789abcdef".random().toString() }}"
            isGenerating = false
        }
    }
}

// Допоміжні composable
@Composable
private fun ProfileDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RequirementRow(
    label: String,
    value: String,
    satisfied: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (satisfied) {
            Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}