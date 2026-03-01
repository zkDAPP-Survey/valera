package crypto

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlin.random.Random

/**
 * Baby JubJub elliptic curve implementation for ZK-friendly signatures
 * Curve equation: x² + y² = 1 + d·x²·y²
 *
 * Based on: https://eips.ethereum.org/EIPS/eip-2494
 * Compatible with circomlib and iden3 implementations
 */
object BabyJubJub {

    // Prime field modulus (same as BN254 scalar field)
    val FIELD_MODULUS = BigInteger.parseString("21888242871839275222246405745257275088548364400416034343698204186575808495617", 10)

    // Curve parameter d = 168696/168700 mod p
    private val D_NUMERATOR = BigInteger.parseString("168696", 10)
    private val D_DENOMINATOR = BigInteger.parseString("168700", 10)
    val D: BigInteger = (D_NUMERATOR * D_DENOMINATOR.modInverse(FIELD_MODULUS)) % FIELD_MODULUS

    // Curve parameter a = 168698
    val A = BigInteger.parseString("168698", 10)

    // Base point (generator)
    val BASE_POINT_X = BigInteger.parseString("995203441582195749578291179787384436505546430278305826713579947235728471134", 10)
    val BASE_POINT_Y = BigInteger.parseString("5472060717959818805561601436314318772137091100104008585924551046643952123905", 10)

    // Order of the base point (subgroup order)
    val ORDER = BigInteger.parseString("21888242871839275222246405745257275088614511777268538073601725287587578984328", 10)

    // Cofactor
    val COFACTOR = BigInteger.parseString("8", 10)

    /**
     * Point on Baby JubJub curve
     */
    data class Point(val x: BigInteger, val y: BigInteger) {

        /**
         * Check if point is on the curve
         */
        fun isOnCurve(): Boolean {
            val x2 = (x * x) % FIELD_MODULUS
            val y2 = (y * y) % FIELD_MODULUS
            val left = (A * x2 + y2) % FIELD_MODULUS
            val right = (BigInteger.ONE + D * x2 % FIELD_MODULUS * y2) % FIELD_MODULUS
            return left == right
        }

        companion object {
            val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE)
        }

        override fun toString(): String {
            return "Point(x=${x.toString(16)}, y=${y.toString(16)})"
        }
    }

    /**
     * Point addition on Baby JubJub curve using twisted Edwards addition formula
     */
    fun addPoints(p1: Point, p2: Point): Point {
        val x1 = p1.x
        val y1 = p1.y
        val x2 = p2.x
        val y2 = p2.y

        // x3 = (x1*y2 + y1*x2) / (1 + d*x1*x2*y1*y2)
        val x1y2 = (x1 * y2) % FIELD_MODULUS
        val y1x2 = (y1 * x2) % FIELD_MODULUS
        val x1x2 = (x1 * x2) % FIELD_MODULUS
        val y1y2 = (y1 * y2) % FIELD_MODULUS

        val dx1x2y1y2 = (D * x1x2 % FIELD_MODULUS * y1y2) % FIELD_MODULUS

        val x3Num = (x1y2 + y1x2) % FIELD_MODULUS
        val x3Den = (BigInteger.ONE + dx1x2y1y2) % FIELD_MODULUS
        val x3 = (x3Num * x3Den.modInverse(FIELD_MODULUS)) % FIELD_MODULUS

        // y3 = (y1*y2 - a*x1*x2) / (1 - d*x1*x2*y1*y2)
        val y3Num = (y1y2 - A * x1x2 % FIELD_MODULUS + FIELD_MODULUS) % FIELD_MODULUS
        val y3Den = (BigInteger.ONE - dx1x2y1y2 + FIELD_MODULUS) % FIELD_MODULUS
        val y3 = (y3Num * y3Den.modInverse(FIELD_MODULUS)) % FIELD_MODULUS

        return Point(x3, y3)
    }

    /**
     * Scalar multiplication using double-and-add algorithm
     */
    fun scalarMult(scalar: BigInteger, point: Point): Point {
        var result = Point.IDENTITY
        var temp = point
        var k = scalar % ORDER

        while (k > BigInteger.ZERO) {
            if (k.and(BigInteger.ONE) == BigInteger.ONE) {
                result = addPoints(result, temp)
            }
            temp = addPoints(temp, temp)
            k = k shr 1
        }

        return result
    }

    /**
     * Generate a random private key (scalar in the subgroup)
     */
    fun generatePrivateKey(): BigInteger {
        val bytes = ByteArray(32)
        Random.Default.nextBytes(bytes)
        var key = BigInteger.fromByteArray(bytes, Sign.POSITIVE) % ORDER

        // Ensure key is not zero
        if (key == BigInteger.ZERO) {
            key = BigInteger.ONE
        }

        return key
    }

    /**
     * Derive public key from private key: P = s * G
     */
    fun derivePublicKey(privateKey: BigInteger): Point {
        val basePoint = Point(BASE_POINT_X, BASE_POINT_Y)
        return scalarMult(privateKey, basePoint)
    }

    /**
     * Compress point to 32 bytes (y-coordinate + sign of x)
     * Format: [y (31.5 bytes)] [sign bit (0.5 bytes)]
     */
    fun compressPoint(point: Point): ByteArray {
        val yBytes = point.y.toByteArray()
        val result = ByteArray(32)

        // Copy y coordinate (big-endian, right-aligned)
        val yLen = minOf(yBytes.size, 32)
        yBytes.copyInto(
            destination = result,
            destinationOffset = 32 - yLen,
            startIndex = yBytes.size - yLen
        )

        // Set sign bit (MSB of last byte) based on x coordinate LSB
        if (point.x.and(BigInteger.ONE) == BigInteger.ONE) {
            result[31] = (result[31].toInt() or 0x80).toByte()
        }

        return result
    }

    /**
     * Decompress point from 32 bytes
     */
    fun decompressPoint(bytes: ByteArray): Point {
        require(bytes.size == 32) { "Compressed point must be 32 bytes" }

        // Extract sign bit
        val signBit = (bytes[31].toInt() and 0x80) != 0

        // Extract y coordinate (clear sign bit)
        val yBytes = bytes.copyOf()
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()
        val y = BigInteger.fromByteArray(yBytes, Sign.POSITIVE) % FIELD_MODULUS

        // Recover x from curve equation: x² = (y² - 1) / (d*y² - a)
        val y2 = (y * y) % FIELD_MODULUS

        val numerator = (y2 - BigInteger.ONE + FIELD_MODULUS) % FIELD_MODULUS
        val denominator = (D * y2 - A + FIELD_MODULUS) % FIELD_MODULUS

        val x2 = (numerator * denominator.modInverse(FIELD_MODULUS)) % FIELD_MODULUS

        // Compute square root using Tonelli-Shanks
        var x = sqrtMod(x2, FIELD_MODULUS)

        // Choose correct root based on sign bit
        val xIsOdd = x.and(BigInteger.ONE) == BigInteger.ONE
        if (xIsOdd != signBit) {
            x = FIELD_MODULUS - x
        }

        val point = Point(x, y)
        require(point.isOnCurve()) { "Decompressed point is not on curve" }

        return point
    }

    /**
     * Modular exponentiation: base^exponent mod modulus
     */
    private fun modPow(base: BigInteger, exponent: BigInteger, modulus: BigInteger): BigInteger {
        var result = BigInteger.ONE
        var b = base % modulus
        var e = exponent

        while (e > BigInteger.ZERO) {
            if (e.and(BigInteger.ONE) == BigInteger.ONE) {
                result = (result * b) % modulus
            }
            e = e shr 1
            b = (b * b) % modulus
        }

        return result
    }

    /**
     * Compute modular square root using Tonelli-Shanks algorithm
     */
    private fun sqrtMod(n: BigInteger, p: BigInteger): BigInteger {
        // Check if square root exists using Euler's criterion
        val eulerCriterion = modPow(n, (p - BigInteger.ONE) / BigInteger.TWO, p)
        if (eulerCriterion != BigInteger.ONE) {
            throw IllegalArgumentException("No square root exists")
        }

        // Find q and s such that p - 1 = q * 2^s with q odd
        var q = p - BigInteger.ONE
        var s = 0
        while (q.and(BigInteger.ONE) == BigInteger.ZERO) {
            q = q shr 1
            s++
        }

        // Find a quadratic non-residue
        var z = BigInteger.TWO
        while (modPow(z, (p - BigInteger.ONE) / BigInteger.TWO, p) == BigInteger.ONE) {
            z = z + BigInteger.ONE
        }

        var m = s.toLong()
        var c = modPow(z, q, p)
        var t = modPow(n, q, p)
        var r = modPow(n, (q + BigInteger.ONE) / BigInteger.TWO, p)

        while (t != BigInteger.ONE) {
            // Find least i such that t^(2^i) = 1
            var temp = t
            var i = 0L
            while (temp != BigInteger.ONE && i < s) {
                temp = (temp * temp) % p
                i++
            }

            val shiftAmount = (m - i - 1).toInt()
            val b = modPow(c, BigInteger.ONE shl shiftAmount, p)
            m = i
            c = (b * b) % p
            t = (t * c) % p
            r = (r * b) % p
        }

        return r
    }

    /**
     * Convert bytes to hex string
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            when {
                value < 16 -> "0${value.toString(16)}"
                else -> value.toString(16)
            }
        }
    }

    /**
     * Convert hex string to BigInteger
     */
    fun hexToBigInteger(hex: String): BigInteger {
        return BigInteger.parseString(hex, 16)
    }

    /**
     * Convert hex string to bytes
     */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /**
     * Convert BigInteger to fixed-size byte array (32 bytes, big-endian)
     */
    fun bigIntegerToBytes32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val result = ByteArray(32)

        if (bytes.isEmpty()) {
            return result // all zeros
        }

        // Handle negative numbers (shouldn't happen, but just in case)
        if (bytes[0] < 0) {
            return result
        }

        // Copy bytes, right-aligned
        val copyLen = minOf(bytes.size, 32)
        val srcOffset = maxOf(0, bytes.size - 32)
        val dstOffset = maxOf(0, 32 - bytes.size)

        bytes.copyInto(
            destination = result,
            destinationOffset = dstOffset,
            startIndex = srcOffset,
            endIndex = srcOffset + copyLen
        )

        return result
    }

    /**
     * Convert fixed-size byte array to BigInteger
     */
    fun bytes32ToBigInteger(bytes: ByteArray): BigInteger {
        require(bytes.size == 32) { "Input must be 32 bytes" }
        return BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }

}