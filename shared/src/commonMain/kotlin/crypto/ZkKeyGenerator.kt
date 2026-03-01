package crypto

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * ZK-friendly key generation and signing using Baby JubJub + EdDSA + Poseidon
 */
object ZkKeyGenerator {

    data class KeyPair(
        val privateKey: String,  // hex-encoded scalar
        val publicKey: String    // hex-encoded point (64 bytes: x + y)
    )

    /**
     * Generate a new ZK-friendly key pair for EdDSA
     */
    fun generate(): KeyPair {
        val privateKeyScalar = BabyJubJub.generatePrivateKey()
        val publicKeyPoint = BabyJubJub.derivePublicKey(privateKeyScalar)

        // Encode private key as hex string
        val privateKeyHex = privateKeyScalar.toString(16).padStart(64, '0')

        // Encode public key as uncompressed point (x + y) using fixed-size encoding
        val pubXBytes = BabyJubJub.bigIntegerToBytes32(publicKeyPoint.x)
        val pubYBytes = BabyJubJub.bigIntegerToBytes32(publicKeyPoint.y)

        val publicKeyHex = BabyJubJub.bytesToHex(pubXBytes + pubYBytes)

        return KeyPair(
            privateKey = privateKeyHex,
            publicKey = publicKeyHex
        )
    }

    /**
     * Parse public key from hex (64 bytes: x + y)
     */
    private fun parsePublicKey(publicKeyHex: String): BabyJubJub.Point {
        val bytes = BabyJubJub.hexToBytes(publicKeyHex)
        require(bytes.size == 64) { "Public key must be 64 bytes" }

        val xBytes = bytes.sliceArray(0 until 32)
        val yBytes = bytes.sliceArray(32 until 64)

        val x = BabyJubJub.bytes32ToBigInteger(xBytes) % BabyJubJub.FIELD_MODULUS
        val y = BabyJubJub.bytes32ToBigInteger(yBytes) % BabyJubJub.FIELD_MODULUS

        return BabyJubJub.Point(x, y)
    }

    /**
     * Sign message with private key using EdDSA on Baby JubJub
     */
    fun sign(privateKeyHex: String, message: ByteArray): ByteArray {
        val privateKey = BabyJubJub.hexToBigInteger(privateKeyHex)
        val signature = EdDSABabyJubJub.sign(privateKey, message)
        return signature.toBytes()
    }

    /**
     * Sign message and return hex-encoded signature
     */
    fun signToHex(privateKeyHex: String, message: ByteArray): String {
        val signatureBytes = sign(privateKeyHex, message)
        return BabyJubJub.bytesToHex(signatureBytes)
    }

    /**
     * Verify signature with public key
     */
    fun verify(publicKeyHex: String, message: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            // Parse public key
            val publicKey = parsePublicKey(publicKeyHex)

            // Parse signature
            val signature = EdDSABabyJubJub.Signature.fromBytes(signatureBytes)

            // Verify
            EdDSABabyJubJub.verify(publicKey, message, signature)
        } catch (e: Exception) {
            println("Verification failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Verify signature from hex strings
     */
    fun verifyHex(publicKeyHex: String, message: ByteArray, signatureHex: String): Boolean {
        val signatureBytes = BabyJubJub.hexToBytes(signatureHex)
        return verify(publicKeyHex, message, signatureBytes)
    }

    /**
     * Get public key from private key hex
     */
    fun getPublicKeyFromPrivate(privateKeyHex: String): String {
        val privateKey = BabyJubJub.hexToBigInteger(privateKeyHex)
        val publicKeyPoint = BabyJubJub.derivePublicKey(privateKey)

        val pubXBytes = BabyJubJub.bigIntegerToBytes32(publicKeyPoint.x)
        val pubYBytes = BabyJubJub.bigIntegerToBytes32(publicKeyPoint.y)

        return BabyJubJub.bytesToHex(pubXBytes + pubYBytes)
    }
}