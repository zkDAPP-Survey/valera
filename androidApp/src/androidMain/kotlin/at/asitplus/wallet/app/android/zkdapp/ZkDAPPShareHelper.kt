package at.asitplus.wallet.app.android.zkdapp

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

object ZkDAPPShareHelper {
    
    private const val TAG = "ZkDAPPShareHelper"
    private const val ZKDAPP_SCHEME = "zkdappsurveyfrontend"
    
    data class ShareRequest(
        val action: String?,
        val callback: String?,
        val credentialType: String?,
        val requestId: String?,
        val rawUri: Uri
    )
    
    @Serializable
    data class CredentialResponse(
        val type: String,
        val issuer: String,
        val issuanceDate: String,
        val credentialSubject: Map<String, String>,
        val proof: ProofData? = null
    )
    
    @Serializable
    data class ProofData(
        val type: String = "JsonWebSignature2020",
        val created: String,
        val verificationMethod: String,
        val proofPurpose: String = "assertionMethod",
        val jws: String
    )
    
    fun parseShareRequest(uri: Uri): ShareRequest? {
        return try {
            if (uri.scheme == "asitplus-wallet" && uri.host == "share") {
                ShareRequest(
                    action = uri.getQueryParameter("action"),
                    callback = uri.getQueryParameter("callback"),
                    credentialType = uri.getQueryParameter("type"),
                    requestId = uri.getQueryParameter("requestId"),
                    rawUri = uri
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Napier.e(e, tag = TAG) { "Error parsing share request: ${e.message}" }
            null
        }
    }
    
    fun sendCredentialToZkDAPP(
        context: Context,
        callbackUrl: String,
        credential: String,
        did: String
    ): Boolean {
        return try {
            val encodedCredential = URLEncoder.encode(credential, "UTF-8")
            val encodedDid = URLEncoder.encode(did, "UTF-8")
            val timestamp = System.currentTimeMillis()
            
            val fullUrl = buildString {
                append(callbackUrl)
                append(if (callbackUrl.contains("?")) "&" else "?")
                append("credential=").append(encodedCredential)
                append("&did=").append(encodedDid)
                append("&timestamp=").append(timestamp)
            }
            
            Napier.d(tag = TAG) { "Sending credential to zkDAPP: $fullUrl" }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(fullUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            try {
                context.startActivity(intent)
                Napier.i(tag = TAG) { "Successfully sent credential to zkDAPP" }
                true
            } catch (e: ActivityNotFoundException) {
                Napier.w(tag = TAG) { "zkDAPP Survey Frontend app not found: ${e.message}" }
                false
            } catch (e: Exception) {
                Napier.e(e, tag = TAG) { "Error starting activity: ${e.message}" }
                false
            }
            
        } catch (e: Exception) {
            Napier.e(e, tag = TAG) { "Error sending credential to zkDAPP: ${e.message}" }
            false
        }
    }
    
    fun createTestCredential(type: String = "AgeVerification"): String {
        return "{\"type\":\"PersonalInfo\",\"birthYear\":2000,\"birthMonth\":5,\"birthDay\":15,\"name\":\"Test User\"}"
    }
    
    fun isValidZkDAPPCallback(callbackUrl: String): Boolean {
        return try {
            val uri = Uri.parse(callbackUrl)
            uri.scheme == ZKDAPP_SCHEME && 
            (uri.host == "auth" || uri.host == "survey")
        } catch (e: Exception) {
            Napier.e(e, tag = TAG) { "Invalid callback URL: ${e.message}" }
            false
        }
    }
}
