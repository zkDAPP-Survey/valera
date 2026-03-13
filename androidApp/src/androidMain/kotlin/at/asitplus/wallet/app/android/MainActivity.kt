package at.asitplus.wallet.app.android

import MainView
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.registry.provider.RegistryManager
import androidx.lifecycle.lifecycleScope
import at.asitplus.wallet.app.android.dcapi.DCAPIInvocationData
import at.asitplus.wallet.app.android.zkdapp.ZkDAPPShareHelper
import at.asitplus.wallet.app.common.BuildContext
import at.asitplus.wallet.app.common.BuildType
import at.asitplus.wallet.lib.agent.CreatePresentationResult
import at.asitplus.wallet.lib.agent.PresentationExchangeCredentialDisclosure
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.PresentationResponseParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.data.CredentialPresentation
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.dif.Constraint
import at.asitplus.dif.ConstraintField
import at.asitplus.dif.DifInputDescriptor
import at.asitplus.dif.FormatContainerSdJwt
import at.asitplus.dif.FormatHolder
import at.asitplus.dif.PresentationDefinition
import at.asitplus.jsonpath.core.NodeListEntry
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.jsonpath.core.NormalizedJsonPathSegment
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.multipaz.prompt.AndroidPromptModel
import ui.navigation.PRESENTATION_REQUESTED_INTENT


class MainActivity : AbstractWalletActivity() {

    companion object {
        const val ZKDAPP_SHARE_REQUEST_INTENT = "zkdapp.SHARE_REQUEST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val promptModel = AndroidPromptModel()
        setContent {
            MainView(
                buildContext = BuildContext(
                    buildType = BuildType.valueOf(BuildConfig.BUILD_TYPE.uppercase()),
                    packageName = BuildConfig.APPLICATION_ID,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    osVersion = "Android ${Build.VERSION.RELEASE}"
                ),
                promptModel
            )
        }
    }

    override fun populateLink(intent: Intent) {
        when (intent.action) {
            RegistryManager.ACTION_GET_CREDENTIAL -> {
                Globals.dcapiInvocationData.value =
                    DCAPIInvocationData(intent, ::sendCredentialResponseToDCAPIInvoker)
                Globals.appLink.value = intent.action
            }
            PRESENTATION_REQUESTED_INTENT -> {
                Globals.presentationStateModel.value = NdefDeviceEngagementService.presentationStateModel
                Globals.appLink.value = PRESENTATION_REQUESTED_INTENT
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    // Check if this is a zkDAPP credential share request
                    val shareRequest = ZkDAPPShareHelper.parseShareRequest(uri)
                    if (shareRequest != null) {
                        Napier.i(tag = "MainActivity") { "Received zkDAPP share request: ${shareRequest.action}" }
                        handleZkDAPPShareRequest(shareRequest)
                    } else {
                        // Handle other deep links
                        Globals.appLink.value = uri.toString()
                    }
                }
            }
            else -> {
                Globals.appLink.value = intent.data?.toString()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
            populateLink(intent)
    }
    
    private fun handleZkDAPPShareRequest(request: ZkDAPPShareHelper.ShareRequest) {
        try {
            val callback = request.callback
            val credentialType = request.credentialType
            
            if (callback == null) {
                Napier.e(tag = "MainActivity") { "No callback URL provided in zkDAPP request" }
                finish()
                return
            }
            
            if (!ZkDAPPShareHelper.isValidZkDAPPCallback(callback)) {
                Napier.e(tag = "MainActivity") { "Invalid callback URL: $callback" }
                finish()
                return
            }
            
            Napier.i(tag = "MainActivity") { 
                "zkDAPP requesting credential type: $credentialType, presentationType: ${request.presentationType}, requestedClaims: ${request.requestedClaims}, callback: $callback"
            }

            lifecycleScope.launch {
                try {
                    val success = sendPresentationToZkDAPP(callback, credentialType, request)
                    Napier.i(tag = "MainActivity") { "Presentation handled (callbackLaunchSuccess=$success)" }
                    if (success) {
                        // Give Android a short moment to dispatch the callback intent reliably.
                        delay(250)
                        moveTaskToBack(true)
                    }
                } catch (e: Exception) {
                    Napier.e(e, tag = "MainActivity") { "Unhandled exception in zkDAPP handler: ${e.message}" }
                    runCatching {
                        sendStructuredError(callback, request.requestId, "internal_error", e.message ?: "Unknown error")
                    }
                }
            }
            
        } catch (e: Exception) {
            Napier.e(e, tag = "MainActivity") { "Error handling zkDAPP share request: ${e.message}" }
            finish()
        }
    }

    private suspend fun sendPresentationToZkDAPP(
        callback: String,
        credentialType: String?,
        request: ZkDAPPShareHelper.ShareRequest,
    ): Boolean {
        val walletMain = withTimeoutOrNull(15_000L) {
            Globals.walletMain.filterNotNull().first()
        } ?: return sendStructuredError(callback, request.requestId, "wallet_not_ready", "Wallet initialization timed out after 15s")

        val requestedClaims = request.requestedClaims
        val inputDescriptorId = request.requestId ?: "zkdapp-input-descriptor"
        val inputDescriptor = DifInputDescriptor(
            id = inputDescriptorId,
            format = FormatHolder(sdJwt = FormatContainerSdJwt()),
            constraints = Constraint(
                fields = requestedClaims.map {
                    ConstraintField(
                        path = listOf("$.${it}"),
                    )
                }
            )
        )

        val matchingCredentials = walletMain.holderAgent.matchInputDescriptorsAgainstCredentialStore(
            inputDescriptors = listOf(inputDescriptor),
            fallbackFormatHolder = null,
        ).getOrElse {
            Napier.e(it, tag = "MainActivity") { "Could not match requested claims against credential store" }
            return sendStructuredError(callback, request.requestId, "credential_match_failed", "Could not match requested claims")
        }

        val requestedType = credentialType?.trim().orEmpty()
        val descriptorEntries = matchingCredentials[inputDescriptorId]?.entries.orEmpty()
        val sdJwtEntries = descriptorEntries.filter { (storeEntry, _) ->
            storeEntry is SubjectCredentialStore.StoreEntry.SdJwt
        }

        if (sdJwtEntries.isEmpty()) {
            return sendStructuredError(
                callback,
                request.requestId,
                "credential_not_found",
                "No SD-JWT credential matched the requested claim constraints"
            )
        }

        val selectedMatch = sdJwtEntries.firstOrNull { (storeEntry, _) ->
            val sdJwtStoreEntry = storeEntry as SubjectCredentialStore.StoreEntry.SdJwt
            requestedType.isBlank() ||
                sdJwtStoreEntry.sdJwt.verifiableCredentialType == requestedType ||
                sdJwtStoreEntry.scheme?.sdJwtType == requestedType
        } ?: run {
            val availableTypes = sdJwtEntries.map { (storeEntry, _) ->
                val sdJwtStoreEntry = storeEntry as SubjectCredentialStore.StoreEntry.SdJwt
                sdJwtStoreEntry.sdJwt.verifiableCredentialType
                    ?: sdJwtStoreEntry.scheme?.sdJwtType
                    ?: "unknown"
            }.distinct()

            return sendStructuredError(
                callback,
                request.requestId,
                "credential_type_mismatch",
                "Requested vct '$requestedType' did not match available SD-JWT types: ${availableTypes.joinToString(",")}" 
            )
        }

        val selectedCredential = selectedMatch.key
        val selectedConstraints = selectedMatch.value.filterValues { it.isNotEmpty() }
        val selectedPaths = selectedConstraints.values
            .mapNotNull { it.firstOrNull() }
            .map(NodeListEntry::normalizedJsonPath)
            .filter {
                requestedClaims.isEmpty() || getLastClaimName(it)?.let(requestedClaims::contains) == true
            }

        if (requestedClaims.isNotEmpty() && selectedPaths.isEmpty()) {
            return sendStructuredError(
                callback,
                request.requestId,
                "no_requested_claims_available",
                "None of the requested claims are available in matching credential"
            )
        }

        val credentialPresentation = CredentialPresentation.PresentationExchangePresentation(
            presentationRequest = CredentialPresentationRequest.PresentationExchangeRequest(
                presentationDefinition = PresentationDefinition(
                    inputDescriptors = listOf(inputDescriptor),
                ),
            ),
            inputDescriptorSubmissions = mapOf(
                inputDescriptorId to PresentationExchangeCredentialDisclosure(
                    selectedCredential,
                    selectedPaths,
                )
            )
        )

        val presentationResponse = walletMain.holderAgent.createPresentation(
            request = PresentationRequestParameters(
                nonce = request.nonce ?: "",
                audience = request.audience ?: "zkdapp-survey-frontend",
            ),
            credentialPresentation = credentialPresentation,
        ).getOrElse {
            Napier.e(it, tag = "MainActivity") { "Could not create presentation" }
            val cause = it.message ?: it::class.simpleName ?: "unknown"
            return sendStructuredError(
                callback,
                request.requestId,
                "presentation_failed",
                "Could not create presentation: $cause"
            )
        }

        val presentation = when (presentationResponse) {
            is PresentationResponseParameters.PresentationExchangeParameters -> {
                val result = presentationResponse.presentationResults.firstOrNull()
                    ?: return sendStructuredError(callback, request.requestId, "empty_presentation", "No presentation results returned")
                when (result) {
                    is CreatePresentationResult.SdJwt -> result.serialized
                    is CreatePresentationResult.Signed -> result.serialized
                    is CreatePresentationResult.DeviceResponse -> return sendStructuredError(
                        callback, request.requestId, "unexpected_presentation_type",
                        "Expected SD-JWT but got ISO mDL device response"
                    )
                }
            }
            else -> {
                return sendStructuredError(
                    callback,
                    request.requestId,
                    "unexpected_presentation_type",
                    "Unsupported presentation response type"
                )
            }
        }

        return ZkDAPPShareHelper.sendStructuredResponseToZkDAPP(
            context = this,
            callbackUrl = callback,
            response = ZkDAPPShareHelper.StructuredPresentationResponse(
                status = "success",
                requestId = request.requestId,
                presentation = presentation,
            )
        )
    }

    private fun sendStructuredError(
        callback: String,
        requestId: String?,
        errorCode: String,
        errorMessage: String,
    ): Boolean = ZkDAPPShareHelper.sendStructuredResponseToZkDAPP(
        context = this,
        callbackUrl = callback,
        response = ZkDAPPShareHelper.StructuredPresentationResponse(
            status = "error",
            requestId = requestId,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
    )

    private fun getLastClaimName(path: NormalizedJsonPath): String? {
        val segment = path.segments.lastOrNull() as? NormalizedJsonPathSegment.NameSegment
        return segment?.memberName
    }
}