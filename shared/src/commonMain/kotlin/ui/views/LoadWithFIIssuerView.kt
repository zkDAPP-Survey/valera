package ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.asitplus.valera.resources.Res
import at.asitplus.valera.resources.button_label_cancel
import at.asitplus.valera.resources.button_label_confirm
import at.asitplus.valera.resources.button_label_scan_document
import at.asitplus.valera.resources.button_label_submit_request
import at.asitplus.valera.resources.heading_label_fiissuer_screen
import at.asitplus.valera.resources.prompt_confirm_fiissuer_request
import at.asitplus.valera.resources.prompt_scan_fiissuer_document
import at.asitplus.valera.resources.text_label_credential_type
import at.asitplus.valera.resources.text_label_optional_for_now
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.Scope
import ui.composables.FIIssuerAttachmentInputSection
import ui.composables.Logo
import ui.composables.buttons.NavigateUpButton
import ui.viewmodels.LoadWithFIIssuerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadWithFIIssuerView(
    navigateUp: () -> Unit,
    onClickLogo: () -> Unit,
    onClickSettings: () -> Unit,
    onSuccess: () -> Unit,
    koinScope: Scope,
    vm: LoadWithFIIssuerViewModel = koinViewModel(scope = koinScope),
) {
    val state by vm.uiState.collectAsState()
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showDocumentScanner by remember { mutableStateOf(false) }
    var scannerCaptureTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.selectedCredentialType, state.selectedCredentialTypeDetails) {
        if (state.selectedCredentialTypeDetails == null) {
            showDocumentScanner = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.heading_label_fiissuer_screen),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    Logo(onClick = onClickLogo)
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable(onClick = onClickSettings),
                    )
                    Spacer(Modifier.width(15.dp))
                },
                navigationIcon = {
                    NavigateUpButton(navigateUp)
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = !state.isLoading && !state.isSubmitting && state.selectedCredentialType != null,
                    ) {
                        Text(stringResource(Res.string.button_label_submit_request))
                    }
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isLoading && state.credentialTypeNames.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            CredentialTypeDropdown(
                credentialTypeNames = state.credentialTypeNames,
                selectedCredentialType = state.selectedCredentialType,
                onSelect = vm::selectCredentialType,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (state.selectedCredentialType != null) {
                Button(
                    onClick = { showDocumentScanner = true },
                    enabled = !state.isLoading && !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.button_label_scan_document))
                }

                Text(
                    text = "Claim details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp),
                )

                state.selectedCredentialTypeDetails?.requiredClaimKeys.orEmpty().forEach { key ->
                    val currentValue = state.claimValues[key].orEmpty()
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = { vm.updateClaimValue(key, it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        label = { Text(key) },
                        supportingText = { Text(stringResource(Res.string.text_label_optional_for_now)) },
                    )
                }

                FIIssuerAttachmentInputSection(
                    label = "Document photos",
                    description = "Please add clear photos of the document pages or cards.",
                    attachments = state.attachments,
                    onGalleryImagesSelected = vm::addAttachments,
                    onCameraImageSelected = vm::addAttachment,
                    onRemoveAttachment = vm::removeAttachment,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            if (state.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(Res.string.button_label_submit_request)) },
            text = { Text(stringResource(Res.string.prompt_confirm_fiissuer_request)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                                vm.submitFiIssuerRequest(onSuccess)
                    }
                ) {
                    Text(stringResource(Res.string.button_label_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(Res.string.button_label_cancel))
                }
            }
        )
    }

    if (showDocumentScanner) {
        AlertDialog(
            onDismissRequest = { showDocumentScanner = false },
            title = { Text(stringResource(Res.string.button_label_scan_document)) },
            text = {
                Column {
                    Text(stringResource(Res.string.prompt_scan_fiissuer_document))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .padding(top = 16.dp),
                    ) {
                        ui.views.DocumentScannerView(
                            onScannedPhoto = { imageBytes: ByteArray ->
                                vm.replaceScannedAttachment(imageBytes)
                                showDocumentScanner = false
                            },
                            onScannedText = { scannedText: String ->
                                vm.applyScannedDocumentTextFromScan(scannedText)
                            },
                            captureTrigger = scannerCaptureTrigger,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = { scannerCaptureTrigger++ }) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Capture")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDocumentScanner = false }) {
                    Text(stringResource(Res.string.button_label_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialTypeDropdown(
    credentialTypeNames: List<String>,
    selectedCredentialType: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (credentialTypeNames.isNotEmpty()) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            singleLine = true,
            readOnly = true,
            value = selectedCredentialType.orEmpty(),
            onValueChange = {},
            enabled = credentialTypeNames.isNotEmpty(),
            label = { Text(stringResource(Res.string.text_label_credential_type)) },
            placeholder = { Text("Select credential type") },
            supportingText = if (selectedCredentialType == null) {
                {
                    Text(
                        text = "Choose a credential type to view the required claims and continue.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            credentialTypeNames.forEach { typeName ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(typeName) },
                    onClick = {
                        onSelect(typeName)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun LoadWithFIIssuerViewModel.submitFiIssuerRequest(onSuccess: () -> Unit) {
    submit(onSuccess)
}

private fun LoadWithFIIssuerViewModel.applyScannedDocumentTextFromScan(scannedText: String) {
    applyScannedDocumentText(scannedText)
}

