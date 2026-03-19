package crypto

/**
 * Baby JubJub криптография через native Rust библиотеку
 */
object BabyJubJubNative {

    init {
        try {
            System.loadLibrary("valera_crypto")
            println("✅ Baby JubJub native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            println("❌ Failed to load Baby JubJub library: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    data class KeyPair(
        val privateKey: String,
        val publicKeyX: String,
        val publicKeyY: String
    )

    // Native методы (реализованы в Rust)
    private external fun generate_keys(): String
    private external fun sign_message(privateKey: String, messageHex: String): String
    private external fun verify_signature(
        publicKeyX: String,
        publicKeyY: String,
        messageHex: String,
        signatureHex: String
    ): Boolean
    private external fun free_string(ptr: Long)

    /**
     * Генерация ключей Baby JubJub
     */
    fun generateKeys(): KeyPair {

        val result = generate_keys()

        val parts = result.split("|")
        require(parts.size == 3) { "Invalid key generation result: $result" }

        println("🔑 Baby JubJub keys generated:")
        println("   Private: ${parts[0]}")
        println("   Public X: ${parts[1]}")
        println("   Public Y: ${parts[2]}")

        return KeyPair(
            privateKey = parts[0],
            publicKeyX = parts[1],
            publicKeyY = parts[2]
        )
    }

    /**
     * Подпись сообщения
     */
    fun sign(privateKeyHex: String, message: ByteArray): String {
        val messageHex = message.joinToString("") { "%02x".format(it) }
        val signature = sign_message(privateKeyHex, messageHex)

        println("✍️ Message signed:")
        println("   Message size: ${message.size} bytes")
        println("   Signature: ${signature.take(32)}...")

        return signature
    }

    /**
     * Верификация подписи
     */
    fun verify(
        publicKeyX: String,
        publicKeyY: String,
        message: ByteArray,
        signatureHex: String
    ): Boolean {
        val messageHex = message.joinToString("") { "%02x".format(it) }
        val result = verify_signature(publicKeyX, publicKeyY, messageHex, signatureHex)

        println("🔍 Signature verification: ${if (result) "✓ VALID" else "✗ INVALID"}")

        return result
    }
}