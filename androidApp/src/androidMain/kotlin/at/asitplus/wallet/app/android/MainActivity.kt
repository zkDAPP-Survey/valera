package at.asitplus.wallet.app.android

import MainView
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.registry.provider.RegistryManager
import at.asitplus.wallet.app.android.dcapi.DCAPIInvocationData
import at.asitplus.wallet.app.android.zkdapp.ZkDAPPShareHelper
import at.asitplus.wallet.app.common.BuildContext
import at.asitplus.wallet.app.common.BuildType
import io.github.aakira.napier.Napier
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
            val credentialType = request.credentialType ?: "AgeVerification"
            
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
                "zkDAPP requesting credential type: $credentialType, callback: $callback" 
            }
            
            android.os.Handler(mainLooper).postDelayed({
                val success = sendTestCredentialToZkDAPP(callback, credentialType)
                Napier.i(tag = "MainActivity") { "Credential sent: $success, closing Valera" }
                
                android.os.Handler(mainLooper).postDelayed({
                    finish()
                }, 200)
            }, 100)
            
        } catch (e: Exception) {
            Napier.e(e, tag = "MainActivity") { "Error handling zkDAPP share request: ${e.message}" }
            finish()
        }
    }
    
    private fun sendTestCredentialToZkDAPP(callback: String, credentialType: String): Boolean {
        val testCredential = ZkDAPPShareHelper.createTestCredential(credentialType)
        val testDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        
        val success = ZkDAPPShareHelper.sendCredentialToZkDAPP(
            context = this,
            callbackUrl = callback,
            credential = testCredential,
            did = testDid
        )
        
        if (success) {
            Napier.i(tag = "MainActivity") { "Successfully sent credential to zkDAPP" }
        } else {
            Napier.e(tag = "MainActivity") { "Failed to send credential to zkDAPP" }
        }
        
        return success
    }
}