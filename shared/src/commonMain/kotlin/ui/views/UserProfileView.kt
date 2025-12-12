package ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.asitplus.valera.resources.Res
import at.asitplus.valera.resources.button_label_save
import at.asitplus.valera.resources.content_description_navigate_to_settings
import at.asitplus.valera.resources.form_label_address
import at.asitplus.valera.resources.form_label_address_city
import at.asitplus.valera.resources.form_label_address_country
import at.asitplus.valera.resources.form_label_address_postal_code
import at.asitplus.valera.resources.form_label_address_state
import at.asitplus.valera.resources.form_label_address_street
import at.asitplus.valera.resources.form_label_birth_city
import at.asitplus.valera.resources.form_label_birth_country
import at.asitplus.valera.resources.form_label_birth_state
import at.asitplus.valera.resources.form_label_date_of_birth
import at.asitplus.valera.resources.form_label_expiry_date
import at.asitplus.valera.resources.form_label_first_name
import at.asitplus.valera.resources.form_label_identity_number
import at.asitplus.valera.resources.form_label_identity_number_example
import at.asitplus.valera.resources.form_label_issue_date
import at.asitplus.valera.resources.form_label_last_name
import at.asitplus.valera.resources.form_label_nationality
import at.asitplus.valera.resources.form_label_place_of_birth
import at.asitplus.valera.resources.form_label_sex
import at.asitplus.valera.resources.heading_label_personal_data_screen
import at.asitplus.valera.resources.info_text_date_format_hint
import at.asitplus.valera.resources.info_text_personal_data_saved_locally
import at.asitplus.valera.resources.info_text_personal_data_subtitle
import at.asitplus.valera.resources.label_id_card_back_photo
import at.asitplus.valera.resources.label_id_card_front_photo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.scope.Scope
import ui.composables.Logo
import ui.composables.PhotoInput
import ui.composables.ScreenHeading
import ui.composables.buttons.NavigateUpButton
import ui.viewmodels.UserProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileView(
    navigateUp: () -> Unit,
    onClickLogo: () -> Unit,
    onClickSettings: () -> Unit,
    koinScope: Scope,
    viewModel: UserProfileViewModel = koinViewModel(scope = koinScope),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.fillMaxWidth()) {
                        ScreenHeading(
                            title = stringResource(Res.string.heading_label_personal_data_screen),
                        )
                        Text(
                            text = stringResource(Res.string.info_text_personal_data_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Logo(onClick = onClickLogo)
                    Column(
                        modifier = Modifier.clickable(onClick = onClickSettings)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(Res.string.content_description_navigate_to_settings),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.firstName,
                onValueChange = viewModel::onFirstNameChanged,
                label = { Text(stringResource(Res.string.form_label_first_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = viewModel::onLastNameChanged,
                label = { Text(stringResource(Res.string.form_label_last_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            DropdownField(
                label = stringResource(Res.string.form_label_sex),
                value = state.sex,
                options = viewModel.sexOptions,
                onSelected = viewModel::onSexChanged
            )
            DateInputField(
                value = state.dateOfBirthInput,
                onValueChange = viewModel::onDateOfBirthChanged,
                label = stringResource(Res.string.form_label_date_of_birth),
                isError = state.dateInputError
            )
            DropdownField(
                label = stringResource(Res.string.form_label_nationality),
                value = state.nationality,
                options = viewModel.nationalityOptions,
                onSelected = viewModel::onNationalityChanged
            )
            SectionHeader(text = stringResource(Res.string.form_label_place_of_birth))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.birthCountry,
                    onValueChange = viewModel::onBirthCountryChanged,
                    label = { Text(stringResource(Res.string.form_label_birth_country)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.birthCity,
                    onValueChange = viewModel::onBirthCityChanged,
                    label = { Text(stringResource(Res.string.form_label_birth_city)) },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = state.birthState,
                onValueChange = viewModel::onBirthStateChanged,
                label = { Text(stringResource(Res.string.form_label_birth_state)) },
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(text = stringResource(Res.string.form_label_address))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.residenceCountry,
                    onValueChange = viewModel::onResidenceCountryChanged,
                    label = { Text(stringResource(Res.string.form_label_address_country)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.residenceState,
                    onValueChange = viewModel::onResidenceStateChanged,
                    label = { Text(stringResource(Res.string.form_label_address_state)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.residenceCity,
                    onValueChange = viewModel::onResidenceCityChanged,
                    label = { Text(stringResource(Res.string.form_label_address_city)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.residencePostalCode,
                    onValueChange = viewModel::onResidencePostalCodeChanged,
                    label = { Text(stringResource(Res.string.form_label_address_postal_code)) },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = state.residenceStreet,
                onValueChange = viewModel::onResidenceStreetChanged,
                label = { Text(stringResource(Res.string.form_label_address_street)) },
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(text = stringResource(Res.string.form_label_identity_number))
            OutlinedTextField(
                value = state.identityNumber,
                onValueChange = viewModel::onIdentityNumberChanged,
                label = { Text(stringResource(Res.string.form_label_identity_number)) },
                supportingText = { Text(stringResource(Res.string.form_label_identity_number_example)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DateInputField(
                    value = state.issueDateInput,
                    onValueChange = viewModel::onIssueDateChanged,
                    label = stringResource(Res.string.form_label_issue_date),
                    isError = state.issueInputError,
                    modifier = Modifier.weight(1f)
                )
                DateInputField(
                    value = state.expiryDateInput,
                    onValueChange = viewModel::onExpiryDateChanged,
                    label = stringResource(Res.string.form_label_expiry_date),
                    isError = state.expiryInputError,
                    modifier = Modifier.weight(1f)
                )
            }

            PhotoInput(
                label = stringResource(Res.string.label_id_card_front_photo),
                imageBytes = state.idCardFrontImage,
                onImageSelected = viewModel::onFrontImageChanged
            )
            PhotoInput(
                label = stringResource(Res.string.label_id_card_back_photo),
                imageBytes = state.idCardBackImage,
                onImageSelected = viewModel::onBackImageChanged
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = viewModel::saveProfile,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = stringResource(Res.string.button_label_save))
                }
            }

            if (state.isSaved) {
                Text(
                    text = stringResource(Res.string.info_text_personal_data_saved_locally),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = {
                        onSelected(it)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = { Text(stringResource(Res.string.info_text_date_format_hint)) },
        modifier = modifier
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
