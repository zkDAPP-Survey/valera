package crypto

actual object ZkKeyGenerator {

    // ✅ Обычный класс с конструктором
    actual class KeyPair(
        actual val privateKey: String,
        actual val publicKey: String
    )

    actual fun generate(): KeyPair {
        val nativeKeys = BabyJubJubNative.generateKeys()
        return KeyPair(
            privateKey = nativeKeys.privateKey,
            publicKey = "${nativeKeys.publicKeyX}|${nativeKeys.publicKeyY}"
        )
    }

    actual fun sign(privateKeyHex: String, message: ByteArray): ByteArray {
        val signatureHex = BabyJubJubNative.sign(privateKeyHex, message)
        return signatureHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    actual fun signToHex(privateKeyHex: String, message: ByteArray): String {
        return BabyJubJubNative.sign(privateKeyHex, message)
    }

    actual fun verify(publicKeyHex: String, message: ByteArray, signatureBytes: ByteArray): Boolean {
        val signatureHex = signatureBytes.joinToString("") { "%02x".format(it) }
        return verifyHex(publicKeyHex, message, signatureHex)
    }

    actual fun verifyHex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean {
        val parts = publicKeyHex.split("|")
        require(parts.size == 2) { "Invalid public key format" }

        return BabyJubJubNative.verify(
            publicKeyX = parts[0],
            publicKeyY = parts[1],
            message = message,
            signatureHex = signatureHex
        )
    }
}