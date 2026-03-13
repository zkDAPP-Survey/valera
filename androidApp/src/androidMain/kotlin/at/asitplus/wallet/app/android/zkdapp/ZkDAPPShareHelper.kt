package at.asitplus.wallet.app.android.zkdapp

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

object ZkDAPPShareHelper {
    
    private const val TAG = "ZkDAPPShareHelper"
    private const val ZKDAPP_SCHEME = "zkdappsurveyfrontend"
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StructuredCredentialQuery(
        val vct: String,
        val requestedClaims: List<String> = emptyList(),
    )

    @Serializable
    data class StructuredRequestOptions(
        val allowUserSelectSubset: Boolean = true,
    )

    @Serializable
    data class StructuredPresentationRequest(
        val version: String,
        val requestId: String,
        val presentationType: String,
        val aud: String,
        val nonce: String,
        val callbackUrl: String,
        val credentialQuery: StructuredCredentialQuery,
        val options: StructuredRequestOptions? = null,
    )

    @Serializable
    data class StructuredPresentationResponse(
        val status: String,
        val requestId: String? = null,
        val presentation: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
    )
    
    data class ShareRequest(
        val action: String?,
        val callback: String?,
        val credentialType: String?,
        val requestId: String?,
        val presentationType: String?,
        val audience: String?,
        val nonce: String?,
        val requestedClaims: List<String>,
        val structuredRequest: StructuredPresentationRequest?,
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
                val structuredRequest = parseStructuredRequest(uri.getQueryParameter("request"))

                ShareRequest(
                    action = uri.getQueryParameter("action"),
                    callback = structuredRequest?.callbackUrl ?: uri.getQueryParameter("callback"),
                    credentialType = structuredRequest?.credentialQuery?.vct ?: uri.getQueryParameter("type"),
                    requestId = structuredRequest?.requestId ?: uri.getQueryParameter("requestId"),
                    presentationType = structuredRequest?.presentationType,
                    audience = structuredRequest?.aud,
                    nonce = structuredRequest?.nonce,
                    requestedClaims = structuredRequest?.credentialQuery?.requestedClaims ?: emptyList(),
                    structuredRequest = structuredRequest,
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

    private fun parseStructuredRequest(rawRequest: String?): StructuredPresentationRequest? {
        if (rawRequest.isNullOrBlank()) return null

        return runCatching {
            json.decodeFromString<StructuredPresentationRequest>(rawRequest)
        }.onFailure {
            Napier.w(tag = TAG) { "Could not parse structured request payload: ${it.message}" }
        }.getOrNull()
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

    fun sendStructuredResponseToZkDAPP(
        context: Context,
        callbackUrl: String,
        response: StructuredPresentationResponse,
    ): Boolean {
        return try {
            val fullUrl = buildString {
                append(callbackUrl)
                append(if (callbackUrl.contains("?")) "&" else "?")
                append("status=").append(URLEncoder.encode(response.status, "UTF-8"))
                response.requestId?.let {
                    append("&requestId=").append(URLEncoder.encode(it, "UTF-8"))
                }
                response.presentation?.let {
                    append("&presentation=").append(URLEncoder.encode(it, "UTF-8"))
                }
                response.errorCode?.let {
                    append("&errorCode=").append(URLEncoder.encode(it, "UTF-8"))
                }
                response.errorMessage?.let {
                    append("&errorMessage=").append(URLEncoder.encode(it, "UTF-8"))
                }
            }

            Napier.d(tag = TAG) { "Sending structured response to zkDAPP: $fullUrl" }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(fullUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Napier.e(e, tag = TAG) { "Error sending structured response to zkDAPP: ${e.message}" }
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
