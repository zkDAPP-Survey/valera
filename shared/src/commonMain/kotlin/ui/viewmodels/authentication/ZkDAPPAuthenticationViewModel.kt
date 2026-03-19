package ui.viewmodels.authentication

import androidx.compose.ui.graphics.ImageBitmap
import at.asitplus.catchingUnwrapped
import at.asitplus.dif.Constraint
import at.asitplus.dif.ConstraintField
import at.asitplus.dif.DifInputDescriptor
import at.asitplus.dif.FormatContainerSdJwt
import at.asitplus.dif.FormatHolder
import at.asitplus.dif.PresentationDefinition
import at.asitplus.jsonpath.core.NodeListEntry
import at.asitplus.openid.TransactionDataBase64Url
import at.asitplus.wallet.app.common.WalletMain
import at.asitplus.wallet.lib.agent.CreatePresentationResult
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.PresentationResponseParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.data.CredentialPresentation
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.ktor.openid.OpenId4VpWallet
import at.asitplus.wallet.lib.openid.CredentialMatchingResult
import at.asitplus.wallet.lib.openid.PresentationExchangeMatchingResult
import io.github.aakira.napier.Napier

data class ZkDAPPRequestData(
    val callbackUrl: String,
    val credentialType: String?,
    val requestId: String?,
    val audience: String?,
    val nonce: String?,
    val requestedClaims: List<String>,
)

class ZkDAPPAuthenticationViewModel(
    spName: String?,
    spLocation: String,
    spImage: ImageBitmap?,
    val zkdappRequestData: ZkDAPPRequestData,
    navigateUp: () -> Unit,
    navigateToAuthenticationSuccessPage: (redirectUrl: String?) -> Unit,
    navigateToHomeScreen: () -> Unit,
    walletMain: WalletMain,
    onClickLogo: () -> Unit,
    onClickSettings: () -> Unit,
    val onSendResponse: (callbackUrl: String, requestId: String?, presentation: String?) -> Boolean, // Returns success/failure
) : AuthenticationViewModel(
    spName,
    spLocation,
    spImage,
    navigateUp,
    navigateToAuthenticationSuccessPage,
    navigateToHomeScreen,
    walletMain,
    onClickLogo,
    onClickSettings
) {
    override val transactionData: TransactionDataBase64Url? = null

    private fun requestedConstraintFields(): List<ConstraintField> =
        zkdappRequestData.requestedClaims.map { requestedClaim ->
            ConstraintField(
                path = requestedClaim.toConstraintPaths(),
                optional = true,
            )
        }

    override val presentationRequest: CredentialPresentationRequest by lazy {
        // Create a PresentationExchange request from the zkDAPP request
        val inputDescriptorId = zkdappRequestData.credentialType
            ?: zkdappRequestData.requestId
            ?: "zkdapp-input-descriptor"
        val requestedClaimFields = requestedConstraintFields()
        val inputDescriptor = DifInputDescriptor(
            id = inputDescriptorId,
            format = FormatHolder(sdJwt = FormatContainerSdJwt()),
            constraints = Constraint(
                fields = requestedClaimFields,
            )
        )

        CredentialPresentationRequest.PresentationExchangeRequest(
            presentationDefinition = PresentationDefinition(
                inputDescriptors = listOf(inputDescriptor),
            ),
        )
    }

    override suspend fun findMatchingCredentials(): Result<CredentialMatchingResult<SubjectCredentialStore.StoreEntry>> =
        catchingUnwrapped {
            val peRequest = presentationRequest as CredentialPresentationRequest.PresentationExchangeRequest
            val requestedFields = requestedConstraintFields()
            PresentationExchangeMatchingResult(
                presentationRequest = peRequest,
                matchingInputDescriptorCredentials = walletMain.holderAgent.matchInputDescriptorsAgainstCredentialStore(
                    inputDescriptors = peRequest.presentationDefinition.inputDescriptors,
                    fallbackFormatHolder = null,
                ).getOrThrow().mapValues { (_, credentialMatches) ->
                    credentialMatches.mapValues { (_, constraints) ->
                        if (constraints.isNotEmpty() || requestedFields.isEmpty()) {
                            constraints
                        } else {
                            requestedFields.associateWith { emptyList<NodeListEntry>() }
                        }
                    }
                }
            )
        }

    override suspend fun finalizationMethod(credentialPresentation: CredentialPresentation): OpenId4VpWallet.AuthenticationResult {
        val presentationResponse = walletMain.holderAgent.createPresentation(
            request = PresentationRequestParameters(
                nonce = zkdappRequestData.nonce ?: "",
                audience = zkdappRequestData.audience ?: "zkdapp-survey-frontend",
            ),
            credentialPresentation = credentialPresentation,
        ).getOrElse {
            Napier.e(it, tag = "ZkDAPPAuthVM") { "Could not create presentation" }
            throw it
        }

        val presentation = when (presentationResponse) {
            is PresentationResponseParameters.PresentationExchangeParameters -> {
                val result = presentationResponse.presentationResults.firstOrNull()
                    ?: throw IllegalStateException("No presentation results returned")
                when (result) {
                    is CreatePresentationResult.SdJwt -> result.serialized
                    is CreatePresentationResult.Signed -> result.serialized
                    is CreatePresentationResult.DeviceResponse -> throw IllegalStateException(
                        "Expected SD-JWT but got ISO mDL device response"
                    )
                }
            }
            else -> {
                throw IllegalStateException("Unsupported presentation response type")
            }
        }

        // Send the presentation back to zkDAPP using the callback
        val success = onSendResponse(zkdappRequestData.callbackUrl, zkdappRequestData.requestId, presentation)
        if (!success) {
            Napier.w(tag = "ZkDAPPAuthVM") { "Failed to send response to zkDAPP callback" }
        }

        return OpenId4VpWallet.AuthenticationSuccess()
    }
}

private fun String.toConstraintPaths(): List<String> {
    val claim = trim()
    if (claim.isEmpty()) return emptyList()

    val baseClaims = linkedSetOf(claim)

    when (claim) {
        "gender" -> baseClaims += "sex"
        "nationality" -> baseClaims += "nationalities"
        "birth_date" -> {
            baseClaims += "date_of_birth"
            baseClaims += "birthdate"
            baseClaims += "dob"
        }
        "issue_date" -> {
            baseClaims += "issuance_date"
            baseClaims += "iat"
        }
        "expiry_date" -> {
            baseClaims += "expiration_date"
            baseClaims += "exp"
        }
        "resident_address" -> {
            baseClaims += "address"
            baseClaims += "address.formatted"
        }
        "resident_city" -> baseClaims += "address.locality"
        "resident_postal_code" -> baseClaims += "address.postal_code"
        "resident_country" -> baseClaims += "address.country"
        "resident_state" -> baseClaims += "address.region"
        "resident_street" -> baseClaims += "address.street"
        "resident_house_number" -> baseClaims += "address.house_number"
    }

    if (claim.startsWith("age_over_")) {
        val suffix = claim.removePrefix("age_over_")
        baseClaims += "age_equal_or_over_${suffix}"
        baseClaims += "age_equal_or_over.equal_or_over_${suffix}"
    }

    val prefixes = listOf("", "credentialSubject.", "vc.credentialSubject.")
    val candidates = linkedSetOf<String>()
    baseClaims.forEach { base ->
        prefixes.forEach { prefix ->
            candidates += "$.${prefix}${base}"
        }
    }

    return candidates.toList()
}
