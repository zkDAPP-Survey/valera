package ui.composables.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.asitplus.dif.ConstraintField
import at.asitplus.jsonpath.core.NodeList
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.jsonpath.core.NormalizedJsonPathSegment
import at.asitplus.valera.resources.Res
import at.asitplus.valera.resources.text_label_check_all
import at.asitplus.wallet.app.common.thirdParty.at.asitplus.wallet.lib.data.getLocalization
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.data.ConstantIndex
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import ui.composables.LabeledCheckbox
import ui.composables.LabeledTextCheckbox

@Composable
fun AttributeSelectionGroup(
    credential: Map.Entry<SubjectCredentialStore.StoreEntry, Map<ConstraintField, NodeList>>,
    selection: SnapshotStateMap<String, Boolean>,
    format: ConstantIndex.CredentialScheme?
) {
    val storeEntry = credential.key
    val attributeSelectionList: List<AttributeSelectionElement> =
        credential.value.mapNotNull { constraint ->
            val path =
                constraint.value.firstOrNull()?.normalizedJsonPath
                    ?: constraint.key.path.firstOrNull()?.let { rawPath ->
                        rawPath.toNormalizedJsonPathOrNull()
                    }
                    ?: return@mapNotNull null
            val memberName = (path.segments.lastOrNull() as? NormalizedJsonPathSegment.NameSegment)?.memberName
                ?: return@mapNotNull null
            val optional = constraint.key.optional
            val matchedDisclosure = when (storeEntry) {
                is SubjectCredentialStore.StoreEntry.SdJwt -> storeEntry.disclosures.values.firstOrNull {
                    val claimName = it?.claimName ?: return@firstOrNull false
                    claimName.matchesRequestedClaim(memberName)
                }

                else -> null
            }

            val value = constraint.value.firstOrNull()?.value?.let {
                when (it) {
                    is JsonPrimitive -> it.content
                    is JsonObject -> extractValueFromJsonObject(it, memberName)
                    else -> it.toString()
                }
            } ?: when (storeEntry) {
                is SubjectCredentialStore.StoreEntry.SdJwt -> matchedDisclosure?.claimValue?.let {
                    when (it) {
                        is JsonPrimitive -> it.content
                        is JsonObject -> extractValueFromJsonObject(it, memberName)
                        else -> ""
                    }
                } ?: ""

                else -> ""
            }.formatClaimValue(memberName)
            val enabled = when (storeEntry) {
                is SubjectCredentialStore.StoreEntry.SdJwt ->
                    (matchedDisclosure != null || constraint.value.isNotEmpty()) && optional == true

                else -> optional == true
            }
            if (selection[memberName] == null) {
                selection[memberName] = !enabled
            }
            AttributeSelectionElement(memberName, path, value, enabled)
        }

    val allChecked = mutableStateOf(!selection.values.contains(false))
    val allCheckedEnabled = mutableStateOf(attributeSelectionList.firstOrNull { it.enabled } != null)

    val changeSelection: (Boolean, String) -> Unit = { bool, memberName ->
        if (attributeSelectionList.firstOrNull { it.memberName == memberName }?.enabled == true)
            selection[memberName] = bool
        if (selection.values.contains(false)) {
            allChecked.value = false
        } else {
            allChecked.value = true
        }
    }

    Card(
        modifier = Modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
        ) {
            LabeledCheckbox(
                label = stringResource(Res.string.text_label_check_all),
                checked = allChecked.value,
                onCheckedChange = { bool ->
                    attributeSelectionList.forEach { entry ->
                        changeSelection(bool, entry.memberName)
                    }
                    allChecked.value = bool
                },
                gapWidth = 0.dp,
                enabled = allCheckedEnabled.value
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            attributeSelectionList.forEach { entry ->
                val label = format
                    ?.getLocalization(entry.jsonPath)
                    ?.let { stringResource(it) }
                    ?: format
                        ?.getLocalization(entry.jsonPath.withCanonicalLastSegment(entry.memberName))
                        ?.let { stringResource(it) }
                    ?: canonicalClaimName(entry.memberName)
                LabeledTextCheckbox(
                    label = label,
                    text = entry.value,
                    checked = selection[entry.memberName] ?: true,
                    onCheckedChange = { bool -> changeSelection(bool, entry.memberName) },
                    enabled = entry.enabled
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

class AttributeSelectionElement(
    val memberName: String,
    val jsonPath: NormalizedJsonPath,
    val value: String,
    val enabled: Boolean
)

private fun String.toNormalizedJsonPathOrNull(): NormalizedJsonPath? {
    val normalized = removePrefix("$").removePrefix(".").trim()
    if (normalized.isEmpty()) return null
    val segments = normalized
        .split('.')
        .filter { it.isNotBlank() }
        .map { NormalizedJsonPathSegment.NameSegment(it) }
    if (segments.isEmpty()) return null
    return NormalizedJsonPath(segments)
}

private fun String.matchesRequestedClaim(requestedMemberName: String): Boolean {
    val disclosureTokens = claimPathTokens()
    val aliases = claimAliases(requestedMemberName)
    return disclosureTokens.any { it in aliases }
}

private fun claimAliases(claimName: String): Set<String> {
    val canonical = canonicalClaimName(claimName.trim())
    if (canonical.isEmpty()) return emptySet()

    val aliases = linkedSetOf(canonical)
    when (canonical) {
        "birth_date" -> {
            aliases += "birth_date"
            aliases += "date_of_birth"
            aliases += "birthdate"
            aliases += "dob"
        }

        "issuance_date" -> {
            aliases += "issue_date"
            aliases += "issuance_date"
            aliases += "iat"
        }

        "expiry_date" -> {
            aliases += "expiry_date"
            aliases += "expiration_date"
            aliases += "exp"
        }
    }
    return aliases
}

private fun canonicalClaimName(claimName: String): String = when (claimName) {
    "date_of_birth", "birthdate", "dob" -> "birth_date"
    "issue_date", "iat" -> "issuance_date"
    "expiration_date", "exp" -> "expiry_date"
    else -> claimName
}

private fun String.claimPathTokens(): Set<String> {
    val normalized = trim().removePrefix("$")
    if (normalized.isEmpty()) return emptySet()
    return normalized
    .split(Regex("[./#:\\[\\]\"' ]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { canonicalClaimName(it) }
        .toSet()
}

private fun extractValueFromJsonObject(jsonObject: JsonObject, memberName: String): String {
    val canonical = canonicalClaimName(memberName)
    // For address objects, try to get the formatted field
    if (canonical.contains("address", ignoreCase = true)) {
        // Try common address field names that contain formatted text
        return listOf("formatted", "street_address", "street", "full_address")
            .mapNotNull { fieldName -> 
                (jsonObject[fieldName] as? JsonPrimitive)?.content
            }
            .firstOrNull()
            ?: jsonObject.iterator().asSequence()
                .mapNotNull { (_, value) -> 
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
                .firstOrNull()
            ?: jsonObject.toString()
    }
    // For other objects, try to get the first string field
    return jsonObject.iterator().asSequence()
        .mapNotNull { (_, value) -> 
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
        .firstOrNull()
        ?: jsonObject.toString()
}

private fun String.formatClaimValue(memberName: String): String {
    val canonical = canonicalClaimName(memberName)
    if (canonical !in setOf("birth_date", "issuance_date", "expiry_date")) return this
    return toIsoDateOrOriginal()
}

private fun String.toIsoDateOrOriginal(): String {
    val raw = trim()
    if (raw.isEmpty()) return raw
    if (raw.contains('T')) return raw.substringBefore('T')
    val seconds = raw.toLongOrNull() ?: return raw
    val instant = runCatching {
        if (raw.length >= 13) Instant.fromEpochMilliseconds(seconds) else Instant.fromEpochSeconds(seconds)
    }.getOrNull() ?: return raw
    return instant.toLocalDateTime(TimeZone.UTC).date.toString()
}

private fun NormalizedJsonPath.withCanonicalLastSegment(memberName: String): NormalizedJsonPath {
    val canonical = canonicalClaimName(memberName)
    val updatedSegments = segments.toMutableList()
    val lastIndex = updatedSegments.indexOfLast { it is NormalizedJsonPathSegment.NameSegment }
    if (lastIndex == -1) return this
    updatedSegments[lastIndex] = NormalizedJsonPathSegment.NameSegment(canonical)
    return NormalizedJsonPath(updatedSegments)
}
