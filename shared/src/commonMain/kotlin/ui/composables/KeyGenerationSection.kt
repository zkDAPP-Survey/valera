package ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    var isImporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copySuccess by remember { mutableStateOf<String?>(null) }

    // import dialog state
    var showImportDialog by remember { mutableStateOf(false) }
    var importPublicKey by remember { mutableStateOf("") }
    var importPrivateKey by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current  // ← Используем встроенный API

    // Загружаем существующие ключи при старте
    LaunchedEffect(Unit) {
        val existingPrivateKey = cryptoKeyRepository.getPrivateKey()
        val existingPublicKey = cryptoKeyRepository.getPublicKey()

        if (existingPrivateKey != null && existingPublicKey != null) {
            privateKey = existingPrivateKey
            publicKey = existingPublicKey
        }
    }

    if (showImportDialog) {
        AlertDialog(
            title = { Text("Import key pair") },
            text = {
                Column {
                    Text(
                        "Paste your existing public/private keys. They will be stored on this device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = importPublicKey,
                        onValueChange = {
                            importPublicKey = it
                            if (importError != null) importError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Public key") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 2,
                        maxLines = 4,
                        singleLine = false,
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = importPrivateKey,
                        onValueChange = {
                            importPrivateKey = it
                            if (importError != null) importError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Private key") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 2,
                        maxLines = 4,
                        singleLine = false,
                    )

                    if (importError != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = importError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            onDismissRequest = {
                if (!isImporting) {
                    showImportDialog = false
                    importError = null
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        scope.launch {
                            val pub = importPublicKey.trim()
                            val priv = importPrivateKey.trim()

                            if (pub.isEmpty() || priv.isEmpty()) {
                                importError = "Both public and private keys are required."
                                return@launch
                            }

                            isImporting = true
                            importError = null
                            error = null

                            try {
                                cryptoKeyRepository.saveKeys(priv, pub) // порядок важен: private, public

                                // обновляем UI
                                publicKey = pub
                                privateKey = priv

                                // закрываем диалог
                                showImportDialog = false
                                importPublicKey = ""
                                importPrivateKey = ""
                            } catch (e: Exception) {
                                importError = "Failed to import keys: ${e.message}"
                            } finally {
                                isImporting = false
                            }
                        }
                    }
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        showImportDialog = false
                        importError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = modifier) {
        // Generate button
        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    error = null
                    try {
                        val keys = withContext(Dispatchers.Default) {
                            println("🔑 Generating Baby JubJub keys...")
                            val keyPair = ZkKeyGenerator.generate()
                            println("✅ Keys generated successfully!")
                            println("   Private: ${keyPair.privateKey}")
                            println("   Public: ${keyPair.publicKey}")
                            Pair(keyPair.privateKey, keyPair.publicKey)
                        }
                        privateKey = keys.first
                        publicKey = keys.second

                        // Сохраняем ключи
                        cryptoKeyRepository.saveKeys(privateKey, publicKey)
                        println("💾 Keys saved to storage")
                    } catch (e: Exception) {
                        error = "Failed to generate keys: ${e.message}"
                        println("❌ Error generating keys: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        isGenerating = false
                    }
                }
            },
            enabled = !isGenerating && !isImporting && privateKey.isEmpty(),  // ← Деактивируется после генерации/импорта
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isGenerating) "Generating..."
                else if (privateKey.isNotEmpty()) "Keys Already Generated"
                else "Generate keys"
            )
        }

        // Import button (active only if keys are NOT present)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                importPublicKey = ""
                importPrivateKey = ""
                importError = null
                showImportDialog = true
            },
            enabled = !isGenerating && !isImporting && privateKey.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Import key pair")
        }

        if (isGenerating) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (publicKey.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))

            // Public Key Field
            Text(
                text = "Public Key",
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
                    maxLines = 4
                )
            }

            // Copy button for Public Key
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                    copySuccess = "Public key copied!"
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (copySuccess == "Public key copied!") {
                            copySuccess = null
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Copy Public Key", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Private Key Field
            Text(
                text = "Private Key",
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
                    maxLines = 4
                )
            }

            // Copy button for Private Key
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(privateKey))
                    copySuccess = "Private key copied!"
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (copySuccess == "Private key copied!") {
                            copySuccess = null
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Copy Private Key", style = MaterialTheme.typography.labelMedium)
            }

            // Copy success message
            if (copySuccess != null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ $copySuccess",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠️ Keep your private key safe! Never share it with anyone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}