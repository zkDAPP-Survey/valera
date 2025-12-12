package ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.asitplus.valera.resources.Res
import at.asitplus.valera.resources.snackbar_personal_data_saved
import at.asitplus.wallet.app.common.ErrorService
import at.asitplus.wallet.app.common.SnackbarService
import at.asitplus.wallet.app.common.data.UserProfileData
import at.asitplus.wallet.app.common.data.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

data class UserProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val sex: String = "",
    val nationality: String = "",
    val birthCountry: String = "",
    val birthCity: String = "",
    val birthState: String = "",
    val residenceCountry: String = "",
    val residenceState: String = "",
    val residenceCity: String = "",
    val residenceStreet: String = "",
    val residencePostalCode: String = "",
    val identityNumber: String = "",
    val dateOfBirth: LocalDate? = null,
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val dateOfBirthInput: String = "",
    val issueDateInput: String = "",
    val expiryDateInput: String = "",
    val idCardFrontImage: ByteArray? = null,
    val idCardBackImage: ByteArray? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val dateInputError: Boolean = false,
    val issueInputError: Boolean = false,
    val expiryInputError: Boolean = false,
)

class UserProfileViewModel(
    private val repository: UserProfileRepository,
    private val snackbarService: SnackbarService,
    private val errorService: ErrorService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState = _uiState.asStateFlow()

    val nationalityOptions = listOf("USA", "UK", "SK", "AT", "DE", "FR", "IT", "ES")
    val sexOptions = listOf("Female", "Male", "Other")

    init {
        viewModelScope.launch {
            repository.userProfile.collect { profile ->
                profile?.let {
                    _uiState.update { state ->
                        state.copy(
                            firstName = it.firstName,
                            lastName = it.lastName,
                            nationality = it.nationality,
                            sex = it.sex,
                            birthCountry = it.birthCountry,
                            birthCity = it.birthCity,
                            birthState = it.birthState,
                            residenceCountry = it.residenceCountry,
                            residenceState = it.residenceState,
                            residenceCity = it.residenceCity,
                            residenceStreet = it.residenceStreet,
                            residencePostalCode = it.residencePostalCode,
                            identityNumber = it.identityNumber,
                            dateOfBirth = it.dateOfBirth,
                            issueDate = it.issueDate,
                            expiryDate = it.expiryDate,
                            dateOfBirthInput = it.dateOfBirth?.toString().orEmpty(),
                            issueDateInput = it.issueDate?.toString().orEmpty(),
                            expiryDateInput = it.expiryDate?.toString().orEmpty(),
                            idCardFrontImage = it.idCardFrontImage,
                            idCardBackImage = it.idCardBackImage,
                            isSaved = true
                        )
                    }
                }
            }
        }
    }

    fun onFirstNameChanged(value: String) = updateState { it.copy(firstName = value, isSaved = false) }
    fun onLastNameChanged(value: String) = updateState { it.copy(lastName = value, isSaved = false) }
    fun onSexChanged(value: String) = updateState { it.copy(sex = value, isSaved = false) }
    fun onDateOfBirthChanged(value: String) = updateState {
        it.copy(
            dateOfBirthInput = value,
            dateOfBirth = parseDate(value),
            dateInputError = value.isNotBlank() && parseDate(value) == null,
            isSaved = false
        )
    }
    fun onIssueDateChanged(value: String) = updateState {
        it.copy(
            issueDateInput = value,
            issueDate = parseDate(value),
            issueInputError = value.isNotBlank() && parseDate(value) == null,
            isSaved = false
        )
    }
    fun onExpiryDateChanged(value: String) = updateState {
        it.copy(
            expiryDateInput = value,
            expiryDate = parseDate(value),
            expiryInputError = value.isNotBlank() && parseDate(value) == null,
            isSaved = false
        )
    }
    fun onBirthCountryChanged(value: String) = updateState { it.copy(birthCountry = value, isSaved = false) }
    fun onBirthCityChanged(value: String) = updateState { it.copy(birthCity = value, isSaved = false) }
    fun onBirthStateChanged(value: String) = updateState { it.copy(birthState = value, isSaved = false) }
    fun onNationalityChanged(value: String) = updateState { it.copy(nationality = value, isSaved = false) }
    fun onResidenceCountryChanged(value: String) = updateState { it.copy(residenceCountry = value, isSaved = false) }
    fun onResidenceStateChanged(value: String) = updateState { it.copy(residenceState = value, isSaved = false) }
    fun onResidenceCityChanged(value: String) = updateState { it.copy(residenceCity = value, isSaved = false) }
    fun onResidenceStreetChanged(value: String) = updateState { it.copy(residenceStreet = value, isSaved = false) }
    fun onResidencePostalCodeChanged(value: String) = updateState { it.copy(residencePostalCode = value, isSaved = false) }
    fun onIdentityNumberChanged(value: String) = updateState { it.copy(identityNumber = value, isSaved = false) }
    fun onFrontImageChanged(bytes: ByteArray?) = updateState { it.copy(idCardFrontImage = bytes, isSaved = false) }
    fun onBackImageChanged(bytes: ByteArray?) = updateState { it.copy(idCardBackImage = bytes, isSaved = false) }

    private fun updateState(block: (UserProfileUiState) -> UserProfileUiState) {
        _uiState.update(block)
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.dateInputError || state.issueInputError || state.expiryInputError) {
            return
        }
        val profile = UserProfileData(
            firstName = state.firstName.trim(),
            lastName = state.lastName.trim(),
            sex = state.sex,
            dateOfBirth = state.dateOfBirth,
            nationality = state.nationality,
            birthCountry = state.birthCountry.trim(),
            birthCity = state.birthCity.trim(),
            birthState = state.birthState.trim(),
            residenceCountry = state.residenceCountry.trim(),
            residenceState = state.residenceState.trim(),
            residenceCity = state.residenceCity.trim(),
            residenceStreet = state.residenceStreet.trim(),
            residencePostalCode = state.residencePostalCode.trim(),
            identityNumber = state.identityNumber.trim(),
            issueDate = state.issueDate,
            expiryDate = state.expiryDate,
            idCardFrontImage = state.idCardFrontImage,
            idCardBackImage = state.idCardBackImage,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.save(profile).onSuccess {
                val message = getString(Res.string.snackbar_personal_data_saved)
                snackbarService.showSnackbar(message)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            }.onFailure {
                errorService.emit(it)
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun parseDate(value: String): LocalDate? = runCatching {
        if (value.isBlank()) null else LocalDate.parse(value)
    }.getOrNull()
}
