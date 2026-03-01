package crypto

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * EdDSA signature scheme on Baby JubJub curve using Poseidon hash
 *
 * Compatible with circomlib EdDSAPoseidonVerifier
 * Based on: https://iden3-communication.io/w3c/proofs/bjj/
 */
object EdDSABabyJubJub {

    data class Signature(
        val R: BabyJubJub.Point,  // Random point (commitment)
        val S: BigInteger          // Scalar (response)
    ) {
        /**
         * Encode signature to bytes (96 bytes: Rx + Ry + S)
         * Using uncompressed format to avoid decompression issues
         */
        fun toBytes(): ByteArray {
            val rxBytes = BabyJubJub.bigIntegerToBytes32(R.x)
            val ryBytes = BabyJubJub.bigIntegerToBytes32(R.y)
            val sBytes = BabyJubJub.bigIntegerToBytes32(S)

            return rxBytes + ryBytes + sBytes // 96 bytes total
        }

        companion object {
            /**
             * Decode signature from bytes (96 bytes)
             */
            fun fromBytes(bytes: ByteArray): Signature {
                require(bytes.size == 96) { "Signature must be 96 bytes, got ${bytes.size}" }

                val rxBytes = bytes.sliceArray(0 until 32)
                val ryBytes = bytes.sliceArray(32 until 64)
                val sBytes = bytes.sliceArray(64 until 96)

                val Rx = BabyJubJub.bytes32ToBigInteger(rxBytes) % BabyJubJub.FIELD_MODULUS
                val Ry = BabyJubJub.bytes32ToBigInteger(ryBytes) % BabyJubJub.FIELD_MODULUS
                val S = BabyJubJub.bytes32ToBigInteger(sBytes) % BabyJubJub.ORDER

                val R = BabyJubJub.Point(Rx, Ry)

                return Signature(R, S)
            }

            /**
             * Decode signature from hex string
             */
            fun fromHex(hex: String): Signature {
                return fromBytes(BabyJubJub.hexToBytes(hex))
            }
        }

        fun toHex(): String = BabyJubJub.bytesToHex(toBytes())
    }

    /**
     * Derive deterministic nonce using Poseidon hash
     * r = H(privKey || message)
     */
    private fun deriveNonce(privateKey: BigInteger, messageHash: BigInteger): BigInteger {
        return Poseidon.hash(privateKey, messageHash) % BabyJubJub.ORDER
    }

    /**
     * Sign a message with private key using EdDSA
     *
     * Algorithm:
     * 1. Compute public key A = s * G
     * 2. Compute message hash M = H(message)
     * 3. Compute nonce r = H(s || M)
     * 4. Compute R = r * G
     * 5. Compute challenge e = H(R || A || M)
     * 6. Compute S = r + e * s (mod ORDER)
     * 7. Return signature (R, S)
     *
     * @param privateKey Private key scalar
     * @param message Message bytes to sign
     * @return EdDSA signature
     */
    fun sign(privateKey: BigInteger, message: ByteArray): Signature {
        // Derive public key: A = s * G
        val publicKey = BabyJubJub.derivePublicKey(privateKey)

        // Hash the message using Poseidon
        val messageHash = Poseidon.hashBytes(message)

        // Generate deterministic nonce: r = H(s || M)
        val r = deriveNonce(privateKey, messageHash)

        // Compute R = r * G
        val basePoint = BabyJubJub.Point(BabyJubJub.BASE_POINT_X, BabyJubJub.BASE_POINT_Y)
        val R = BabyJubJub.scalarMult(r, basePoint)

        // Compute challenge: e = H(R.x || R.y || A.x || A.y || M)
        val challenge = computeChallenge(R, publicKey, messageHash)

        // Compute S = r + e * s (mod ORDER)
        val S = (r + challenge * privateKey) % BabyJubJub.ORDER

        return Signature(R, S)
    }

    /**
     * Verify EdDSA signature
     *
     * Algorithm:
     * 1. Compute message hash M = H(message)
     * 2. Compute challenge e = H(R || A || M)
     * 3. Check if S * G == R + e * A
     *
     * @param publicKey Public key point
     * @param message Original message bytes
     * @param signature Signature to verify
     * @return true if signature is valid
     */
    fun verify(publicKey: BabyJubJub.Point, message: ByteArray, signature: Signature): Boolean {
        return try {
            // Verify public key is on curve
            if (!publicKey.isOnCurve()) {
                println("Public key is not on curve")
                return false
            }

            // Verify R is on curve
            if (!signature.R.isOnCurve()) {
                println("R point is not on curve")
                return false
            }

            // Hash the message
            val messageHash = Poseidon.hashBytes(message)

            // Compute challenge: e = H(R.x || R.y || A.x || A.y || M)
            val challenge = computeChallenge(signature.R, publicKey, messageHash)

            // Compute left side: S * G
            val basePoint = BabyJubJub.Point(BabyJubJub.BASE_POINT_X, BabyJubJub.BASE_POINT_Y)
            val left = BabyJubJub.scalarMult(signature.S, basePoint)

            // Compute right side: R + e * A
            val eA = BabyJubJub.scalarMult(challenge, publicKey)
            val right = BabyJubJub.addPoints(signature.R, eA)

            // Verify: S*G == R + e*A
            val isValid = left.x == right.x && left.y == right.y

            if (!isValid) {
                println("Signature verification failed: S*G != R + e*A")
            }

            isValid

        } catch (e: Exception) {
            println("Verification error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Compute challenge using Poseidon hash
     * e = H(R.x || R.y || A.x || A.y || M)
     */
    private fun computeChallenge(
        R: BabyJubJub.Point,
        publicKey: BabyJubJub.Point,
        messageHash: BigInteger
    ): BigInteger {
        // Hash all components together
        val inputs = listOf(R.x, R.y, publicKey.x, publicKey.y, messageHash)
        return Poseidon.hashN(inputs) % BabyJubJub.ORDER
    }
}