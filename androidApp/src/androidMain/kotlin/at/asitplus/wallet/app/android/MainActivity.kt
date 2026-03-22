package at.asitplus.wallet.app.android

import MainView
import Globals
import ZkDAPPCallbackData
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
import ui.navigation.routes.ZkDAPPAuthenticationRoute


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
            val requestedClaims = request.requestedClaims.ifEmpty {
                defaultRequestedClaimsForCredentialType(request.credentialType)
            }

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
                "zkDAPP requesting credential, requestedClaims: $requestedClaims, callback: $callback"
            }

            // Store the zkDAPP callback data in Globals for the authentication flow
            Globals.zkdappCallbackData.value = ZkDAPPCallbackData(
                callbackUrl = callback,
                requestId = request.requestId,
                audience = request.audience,
                nonce = request.nonce,
                requestedClaims = requestedClaims,
                credentialType = request.credentialType,
                sendResponse = { callbackUrl, requestId, presentation ->
                    sendZkDAPPResponse(callbackUrl, requestId, presentation)
                }
            )

            // Navigate to the authentication flow
            Globals.appLink.value = "zkdapp://authenticate"

        } catch (e: Exception) {
            Napier.e(e, tag = "MainActivity") { "Error handling zkDAPP share request: ${e.message}" }
            finish()
        }
    }

    private fun defaultRequestedClaimsForCredentialType(credentialType: String?): List<String> {
        return when (credentialType?.trim()) {
            "urn:eudi:pid:1" -> listOf("family_name", "given_name", "birth_date")
            "org.iso.18013.5.1.mDL" -> listOf(
                "family_name",
                "given_name",
                "birth_date",
                "issue_date",
                "expiry_date",
                "issuing_country",
                "issuing_authority",
            )
            "eu.europa.ec.eudi.healthid.1" -> listOf(
                "one_time_token",
                "affiliation_country",
                "issue_date",
                "expiry_date",
                "issuing_authority",
                "issuing_country",
            )
            "org.iso.18013.5.1.age_verification" -> listOf("age_over_18")
            else -> emptyList()
        }
    }

    private fun sendZkDAPPResponse(callbackUrl: String, requestId: String?, presentation: String?): Boolean {
        return ZkDAPPShareHelper.sendStructuredResponseToZkDAPP(
            context = this,
            callbackUrl = callbackUrl,
            response = ZkDAPPShareHelper.StructuredPresentationResponse(
                status = "success",
                requestId = requestId,
                presentation = presentation,
            )
        )
    }
}