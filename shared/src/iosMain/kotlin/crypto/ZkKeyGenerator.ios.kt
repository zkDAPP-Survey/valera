package crypto

actual object ZkKeyGenerator {

    actual data class KeyPair(
        actual val privateKey: String,
        actual val publicKey: String
    )

    actual fun generate(): KeyPair {
        TODO("iOS implementation not yet available")
    }

    actual fun sign(privateKeyHex: String, message: ByteArray): ByteArray {
        TODO("iOS implementation not yet available")
    }

    actual fun signToHex(privateKeyHex: String, message: ByteArray): String {
        TODO("iOS implementation not yet available")
    }

    actual fun verify(publicKeyHex: String, message: ByteArray, signatureBytes: ByteArray): Boolean {
        TODO("iOS implementation not yet available")
    }

    actual fun verifyHex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean {
        TODO("iOS implementation not yet available")
    }
}