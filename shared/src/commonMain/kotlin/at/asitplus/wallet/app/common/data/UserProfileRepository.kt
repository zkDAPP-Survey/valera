package at.asitplus.wallet.app.common.data

import at.asitplus.wallet.app.common.Configuration
import at.asitplus.wallet.app.common.ErrorService
import at.asitplus.wallet.lib.data.vckJsonSerializer
import data.storage.DataStoreService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate

@Serializable
data class UserProfileData(
    val firstName: String = "",
    val lastName: String = "",
    val sex: String = "",
    val dateOfBirth: LocalDate? = null,
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
    val issueDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val idCardFrontImage: ByteArray? = null,
    val idCardBackImage: ByteArray? = null,
)

interface UserProfileRepository {
    val userProfile: Flow<UserProfileData?>
    suspend fun save(profile: UserProfileData): Result<Unit>
    suspend fun clear(): Result<Unit>
}

class DataStoreUserProfileRepository(
    private val dataStoreService: DataStoreService,
    private val errorService: ErrorService,
) : UserProfileRepository {

    override val userProfile: Flow<UserProfileData?> =
        dataStoreService.getPreference(Configuration.DATASTORE_KEY_USER_PROFILE).map { value ->
            value?.let { vckJsonSerializer.decodeFromString<UserProfileData>(it) }
        }.catch {
            errorService.emit(it)
            emit(null)
        }

    override suspend fun save(profile: UserProfileData): Result<Unit> = runCatching {
        val serialized = vckJsonSerializer.encodeToString(profile)
        dataStoreService.setPreference(
            value = serialized,
            key = Configuration.DATASTORE_KEY_USER_PROFILE
        )
    }.onFailure { errorService.emit(it) }

    override suspend fun clear(): Result<Unit> = runCatching {
        dataStoreService.deletePreference(Configuration.DATASTORE_KEY_USER_PROFILE)
    }.onFailure { errorService.emit(it) }
}
