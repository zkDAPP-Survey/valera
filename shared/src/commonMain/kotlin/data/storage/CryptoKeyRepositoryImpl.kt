package data.storage
import kotlinx.coroutines.flow.first
class CryptoKeyRepositoryImpl(
    private val dataStoreService: DataStoreService
) : CryptoKeyRepository {

    companion object {
        private const val PRIVATE_KEY = "crypto_private_key"
        private const val PUBLIC_KEY = "crypto_public_key"
    }

    override suspend fun saveKeys(privateKey: String, publicKey: String) {
        dataStoreService.setPreference(privateKey, PRIVATE_KEY)
        dataStoreService.setPreference(publicKey, PUBLIC_KEY)
    }

    override suspend fun getPrivateKey(): String? {
        return dataStoreService.getPreference(PRIVATE_KEY).first()
    }

    override suspend fun getPublicKey(): String? {
        return dataStoreService.getPreference(PUBLIC_KEY).first()
    }

    override suspend fun hasKeys(): Boolean {
        val privateKey = dataStoreService.getPreference(PRIVATE_KEY).first()
        val publicKey = dataStoreService.getPreference(PUBLIC_KEY).first()
        return privateKey != null && publicKey != null
    }

    override suspend fun deleteKeys() {
        dataStoreService.deletePreference(PRIVATE_KEY)
        dataStoreService.deletePreference(PUBLIC_KEY)
    }
}