package at.asitplus.wallet.app.android.zkdapp

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLDecoder
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
        val voteHash: String? = null,
        val holderSignature: String? = null,
        val holderPublicKey: String? = null,
        val holderAlg: String? = null,
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
                val requestedClaims = structuredRequest?.credentialQuery?.requestedClaims
                    ?.takeIf { it.isNotEmpty() }
                    ?: parseRequestedClaims(uri)

                ShareRequest(
                    action = uri.getQueryParameter("action"),
                    callback = structuredRequest?.callbackUrl ?: uri.getQueryParameter("callback"),
                    credentialType = structuredRequest?.credentialQuery?.vct ?: uri.getQueryParameter("type"),
                    requestId = structuredRequest?.requestId ?: uri.getQueryParameter("requestId"),
                    presentationType = structuredRequest?.presentationType,
                    audience = structuredRequest?.aud,
                    nonce = structuredRequest?.nonce,
                    requestedClaims = requestedClaims,
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

        val candidates = buildList {
            add(rawRequest)
            runCatching { URLDecoder.decode(rawRequest, "UTF-8") }.getOrNull()?.let(::add)
            runCatching {
                URLDecoder.decode(
                    URLDecoder.decode(rawRequest, "UTF-8"),
                    "UTF-8"
                )
            }.getOrNull()?.let(::add)
            if (rawRequest.length > 1 && rawRequest.first() == '"' && rawRequest.last() == '"') {
                add(rawRequest.substring(1, rawRequest.length - 1).replace("\\\"", "\""))
            }
        }.distinct()

        candidates.forEach { candidate ->
            runCatching {
                json.decodeFromString<StructuredPresentationRequest>(candidate)
            }.onSuccess {
                return it
            }
        }

        Napier.w(tag = TAG) { "Could not parse structured request payload from ${candidates.size} candidate(s)" }
        return null
    }

    private fun parseRequestedClaims(uri: Uri): List<String> {
        val raw = uri.getQueryParameter("requestedClaims")?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()

        runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrNull()?.let { claims ->
            return claims.map { it.trim() }.filter { it.isNotEmpty() }
        }

        runCatching {
            json.decodeFromString<List<String>>(URLDecoder.decode(raw, "UTF-8"))
        }.getOrNull()?.let { claims ->
            return claims.map { it.trim() }.filter { it.isNotEmpty() }
        }

        return raw
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
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
            val callbackUri = Uri.parse(callbackUrl)
            val builder = callbackUri.buildUpon().clearQuery()

            callbackUri.queryParameterNames.forEach { name ->
                if (name != "voteHash") {
                    callbackUri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
            }

            builder.appendQueryParameter("status", response.status)
            response.requestId?.let { builder.appendQueryParameter("requestId", it) }
            response.presentation?.let { builder.appendQueryParameter("presentation", it) }
            response.voteHash?.let { builder.appendQueryParameter("voteHash", it) }
            response.holderSignature?.let { builder.appendQueryParameter("holderSignature", it) }
            response.holderPublicKey?.let { builder.appendQueryParameter("holderPublicKey", it) }
            response.holderAlg?.let { builder.appendQueryParameter("holderAlg", it) }
            response.errorCode?.let { builder.appendQueryParameter("errorCode", it) }
            response.errorMessage?.let { builder.appendQueryParameter("errorMessage", it) }

            val fullUrl = builder.build().toString()

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
