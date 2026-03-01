package ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import crypto.ZkKeyGenerator
import data.storage.CryptoKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun KeyGenerationSection(
    modifier: Modifier = Modifier,
    cryptoKeyRepository: CryptoKeyRepository = koinInject()
) {
    var privateKey by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Загружаем существующие ключи при старте
    LaunchedEffect(Unit) {
        val existingPrivateKey = cryptoKeyRepository.getPrivateKey()
        val existingPublicKey = cryptoKeyRepository.getPublicKey()

        if (existingPrivateKey != null && existingPublicKey != null) {
            privateKey = existingPrivateKey
            publicKey = existingPublicKey
        }
    }

    Column(modifier = modifier) {
        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    val keys = withContext(Dispatchers.Default) {
                        // ZK-friendly key generation with Baby JubJub
                        val keyPair = ZkKeyGenerator.generate()
                        Pair(keyPair.privateKey, keyPair.publicKey)
                    }
                    privateKey = keys.first
                    publicKey = keys.second

                    // Сохраняем ключи
                    cryptoKeyRepository.saveKeys(privateKey, publicKey)

                    isGenerating = false
                }
            },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isGenerating) "Generating..." else "Generate ZK Keys")
        }

        if (isGenerating) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (publicKey.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            // Info badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔐 ZK-Friendly Keys",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Baby JubJub Curve • EdDSA • Poseidon Hash",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Public Key Field
            Text(
                text = "Public Key (64 bytes: x + y)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    minLines = 2,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(16.dp))

            // Private Key Field
            Text(
                text = "Private Key (32 bytes)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    minLines = 2,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠️ Keep your private key safe! Never share it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}