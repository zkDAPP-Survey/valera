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
            val value = constraint.value.firstOrNull()?.value?.let {
                when (it) {
                    is JsonPrimitive -> it.content
                    else -> it.toString()
                }
            } ?: when (storeEntry) {
                is SubjectCredentialStore.StoreEntry.SdJwt -> storeEntry.disclosures.values.firstOrNull {
                    val claimName = it?.claimName ?: return@firstOrNull false
                    claimName == memberName || claimName.endsWith(".$memberName")
                }?.claimValue?.let {
                    (it as? JsonPrimitive)?.content ?: ""
                } ?: ""

                else -> ""
            }
            val enabled = when (storeEntry) {
                is SubjectCredentialStore.StoreEntry.SdJwt ->
                    storeEntry.disclosures.values.firstOrNull {
                        val claimName = it?.claimName ?: return@firstOrNull false
                        claimName == memberName || claimName.endsWith(".$memberName")
                    } != null
                            && optional == true

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
                val label = format?.getLocalization(entry.jsonPath)?.let { stringResource(it) } ?: entry.memberName
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
