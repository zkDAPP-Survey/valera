package data.storage

interface CryptoKeyRepository {
    suspend fun saveKeys(privateKey: String, publicKey: String)
    suspend fun getPrivateKey(): String?
    suspend fun getPublicKey(): String?
    suspend fun hasKeys(): Boolean
    suspend fun deleteKeys()
}