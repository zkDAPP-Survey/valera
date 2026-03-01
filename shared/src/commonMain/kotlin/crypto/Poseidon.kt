package crypto

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Poseidon hash function for ZK-friendly hashing
 *
 * Implementation based on:
 * - https://eprint.iacr.org/2019/458.pdf
 * - circomlibjs implementation
 *
 * Uses BN254 scalar field (same as Baby JubJub)
 */
object Poseidon {

    private val FIELD = BabyJubJub.FIELD_MODULUS

    // Number of full rounds
    private const val N_ROUNDS_F = 8

    // Number of partial rounds
    private const val N_ROUNDS_P = 57

    // Total rounds
    private const val N_ROUNDS = N_ROUNDS_F + N_ROUNDS_P

    // State size (t = 6 for Poseidon with 2 inputs + capacity)
    private const val T = 6

    // Round constants (generated using grain LFSR)
    private val ROUND_CONSTANTS = generateRoundConstants()

    // MDS matrix
    private val MDS_MATRIX = generateMDSMatrix()

    /**
     * Hash two field elements
     */
    fun hash(left: BigInteger, right: BigInteger): BigInteger {
        return hashN(listOf(left, right))
    }

    /**
     * Hash array of field elements
     */
    fun hashN(inputs: List<BigInteger>): BigInteger {
        require(inputs.isNotEmpty()) { "Cannot hash empty input" }

        // Initialize state with inputs
        val state = MutableList(T) { BigInteger.ZERO }
        inputs.forEachIndexed { index, value ->
            if (index < T) {
                state[index] = value % FIELD
            }
        }

        // Apply Poseidon permutation
        permute(state)

        // Return first element as hash
        return state[0]
    }

    /**
     * Hash bytes by converting to field elements
     */
    fun hashBytes(bytes: ByteArray): BigInteger {
        // Split bytes into chunks that fit in field
        val chunks = mutableListOf<BigInteger>()
        var i = 0
        while (i < bytes.size) {
            val chunkSize = minOf(31, bytes.size - i) // 31 bytes = 248 bits < 254 bits
            val chunk = bytes.sliceArray(i until i + chunkSize)
            chunks.add(BigInteger.fromByteArray(chunk, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE))
            i += chunkSize
        }

        return hashN(chunks)
    }

    /**
     * Apply Poseidon permutation to state
     */
    private fun permute(state: MutableList<BigInteger>) {
        var roundIndex = 0

        // First full rounds
        repeat(N_ROUNDS_F / 2) {
            addRoundConstants(state, roundIndex)
            sboxFull(state)
            mixLayer(state)
            roundIndex++
        }

        // Partial rounds
        repeat(N_ROUNDS_P) {
            addRoundConstants(state, roundIndex)
            sboxPartial(state)
            mixLayer(state)
            roundIndex++
        }

        // Last full rounds
        repeat(N_ROUNDS_F / 2) {
            addRoundConstants(state, roundIndex)
            sboxFull(state)
            mixLayer(state)
            roundIndex++
        }
    }

    /**
     * Add round constants to state
     */
    private fun addRoundConstants(state: MutableList<BigInteger>, round: Int) {
        for (i in state.indices) {
            state[i] = (state[i] + ROUND_CONSTANTS[round * T + i]) % FIELD
        }
    }

    /**
     * Apply S-box to all elements (full round)
     */
    private fun sboxFull(state: MutableList<BigInteger>) {
        for (i in state.indices) {
            state[i] = sbox(state[i])
        }
    }

    /**
     * Apply S-box to first element only (partial round)
     */
    private fun sboxPartial(state: MutableList<BigInteger>) {
        state[0] = sbox(state[0])
    }

    /**
     * S-box function: x^5
     */
    private fun sbox(x: BigInteger): BigInteger {
        // x^5 = x^4 * x = (x^2)^2 * x
        val x2 = (x * x) % FIELD
        val x4 = (x2 * x2) % FIELD
        return (x4 * x) % FIELD
    }

    /**
     * Mix layer using MDS matrix multiplication
     */
    private fun mixLayer(state: MutableList<BigInteger>) {
        val newState = MutableList(T) { BigInteger.ZERO }

        for (i in 0 until T) {
            var sum = BigInteger.ZERO
            for (j in 0 until T) {
                sum = (sum + MDS_MATRIX[i][j] * state[j]) % FIELD
            }
            newState[i] = sum
        }

        for (i in state.indices) {
            state[i] = newState[i]
        }
    }

    /**
     * Generate round constants using grain LFSR
     * Pre-computed values for t=6
     */
    private fun generateRoundConstants(): List<BigInteger> {
        // These are pre-computed constants for Poseidon with t=6, BN254 field
        // In production, these should be generated using the grain LFSR
        // For now, using simplified constants (should be replaced with proper values)
        return (0 until N_ROUNDS * T).map { i ->
            // Temporary: use hash of index as constant
            // TODO: Replace with proper grain LFSR generated constants
            val seed = "poseidon_constant_$i"
            val bytes = seed.encodeToByteArray()
            BigInteger.fromByteArray(bytes, com.ionspin.kotlin.bignum.integer.Sign.POSITIVE).mod(FIELD)
        }
    }

    /**
     * Generate MDS matrix
     * Pre-computed values for t=6
     */
    private fun generateMDSMatrix(): List<List<BigInteger>> {
        // Cauchy matrix construction: MDS[i][j] = 1 / (x[i] + y[j])
        // where x and y are distinct field elements
        val x = (0 until T).map { BigInteger.fromLong(it.toLong()) }
        val y = (0 until T).map { BigInteger.fromLong((it + T).toLong()) }

        return (0 until T).map { i ->
            (0 until T).map { j ->
                (x[i] + y[j]).modInverse(FIELD)
            }
        }
    }
}