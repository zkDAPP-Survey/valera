package ui.viewmodels

import androidx.lifecycle.ViewModel
import crypto.ZkKeyGenerator
import data.SignatureRequest
import data.storage.CryptoKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SignatureRequestsViewModel : ViewModel(), KoinComponent {

    private val cryptoKeyRepository: CryptoKeyRepository by inject()

    private val _requests = MutableStateFlow<List<SignatureRequest>>(emptyList())
    val requests: StateFlow<List<SignatureRequest>> = _requests.asStateFlow()

    private val _signatures = MutableStateFlow<Map<String, String>>(emptyMap())
    val signatures: StateFlow<Map<String, String>> = _signatures.asStateFlow()

    init {
        loadMockRequests()
    }

    private fun loadMockRequests() {
        _requests.value = listOf(
            SignatureRequest(
                id = "req_001",
                pollTitle = "Budget Allocation 2024",
                pollId = "poll_budget_2024",
                selectedOption = "Option A: Increase education budget by 15%",
                timestamp = Clock.System.now().toEpochMilliseconds()
            ),
            SignatureRequest(
                id = "req_002",
                pollTitle = "New Infrastructure Project",
                pollId = "poll_infrastructure_001",
                selectedOption = "Option B: Build new metro line connecting districts 5 and 7",
                timestamp = Clock.System.now().toEpochMilliseconds()
            ),
            SignatureRequest(
                id = "req_003",
                pollTitle = "Environmental Policy",
                pollId = "poll_env_2024",
                selectedOption = "Option C: Ban single-use plastics by 2026",
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    suspend fun signRequest(requestId: String) {
        try {
            val privateKey = cryptoKeyRepository.getPrivateKey()
            val publicKey = cryptoKeyRepository.getPublicKey()

            if (privateKey == null || publicKey == null) {
                println("❌ No keys found. Generate keys in Settings first")
                return
            }

            val request = _requests.value.find { it.id == requestId } ?: return

            println("🔐 Signing with Baby JubJub...")

            // Serialize request to JSON
            val messageJson = Json.encodeToString(request)
            val messageBytes = messageJson.encodeToByteArray()

            println("📝 Message: $messageJson")

            // ✅ Sign with Baby JubJub через ZkKeyGenerator
            val signatureHex = ZkKeyGenerator.signToHex(privateKey, messageBytes)

            // ✅ Verify immediately
            val isValid = ZkKeyGenerator.verifyHex(publicKey, messageBytes, signatureHex)

            println("✅ Signature: $signatureHex")
            println("✓ Verification: ${if (isValid) "PASSED ✓" else "FAILED ✗"}")

            _signatures.value = _signatures.value + (requestId to signatureHex)
            _requests.value = _requests.value.filter { it.id != requestId }

        } catch (e: Throwable) {
            println("❌ Error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun rejectRequest(requestId: String) {
        println("❌ Rejected request: $requestId")
        _requests.value = _requests.value.filter { it.id != requestId }
    }

    fun refreshRequests() {
        loadMockRequests()
        _signatures.value = emptyMap()
    }

    fun getSignature(requestId: String): String? {
        return _signatures.value[requestId]
    }
}