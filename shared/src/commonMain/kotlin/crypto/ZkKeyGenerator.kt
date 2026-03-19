package crypto

/**
 * ZK-friendly key generation using Baby JubJub
 */
expect object ZkKeyGenerator {

    // ✅ Обычный класс вместо data class
    class KeyPair {
        val privateKey: String
        val publicKey: String
    }

    fun generate(): KeyPair

    fun sign(privateKeyHex: String, message: ByteArray): ByteArray

    fun signToHex(privateKeyHex: String, message: ByteArray): String

    fun verify(publicKeyHex: String, message: ByteArray, signatureBytes: ByteArray): Boolean

    fun verifyHex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean
}