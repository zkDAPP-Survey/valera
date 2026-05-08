package at.asitplus.wallet.app.common.fiissuer

class FIIssuerDocumentScanService {
    fun extractClaimValues(
        scannedText: String,
        claimKeys: List<String>,
    ): Map<String, String> {
        val lines = scannedText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyMap()

        val mrzValues = parseMrz(lines)
        val result = mutableMapOf<String, String>()
        claimKeys.forEach { key ->
            val canonicalKey = key.canonicalClaimKey()
            val value = mrzValues[canonicalKey]
                ?: findLabeledValue(lines, canonicalKey)
                ?: findUnlabeledValue(lines, canonicalKey)
            if (!value.isNullOrBlank()) {
                result[key] = value
            }
        }
        return result
    }

    private fun parseMrz(lines: List<String>): Map<String, String> {
        val mrzLines = lines
            .flatMap { it.split(Regex("\\s+")) + listOf(it) }
            .map { it.normalizeMrzLine() }
            .filter { it.isMrzLikeLine() }
            .distinct()

        val td3 = mrzLines.windowed(2).firstOrNull {
            it[0].length >= 30 &&
                    it[1].length >= 30 &&
                    (it[0].startsWith("P<") || it[0].startsWith("P"))
        }
        if (td3 != null) {
            val firstLine = td3[0].padEnd(44, '<')
            val secondLine = td3[1].padEnd(44, '<')
            val nameParts = firstLine.drop(5).split("<<", limit = 2)
            return buildMap {
                putNames(nameParts)
                val documentId = secondLine.take(9).cleanMrzValue()
                val countryCode = secondLine.substringSafe(10, 13).cleanMrzValue().toIso2CountryCode()
                put("document_number", documentId)
                put("document_id", documentId)
                put("nationality", countryCode)
                put("country_code", countryCode)
                put("issuing_country", firstLine.substringSafe(2, 5).cleanMrzValue().toIso2CountryCode())
                put("birth_date", secondLine.substringSafe(13, 19).formatMrzDate())
            put("sex", secondLine.substringSafe(20, 21).cleanMrzValue().toSexValue())
                put("expiry_date", secondLine.substringSafe(21, 27).formatMrzDate())
            }.filterValues { it.isNotBlank() }
        }

        val td1 = mrzLines.windowed(3).firstOrNull {
            it[0].length >= 30 && it[1].length >= 30 && it[2].length >= 30
        }
        if (td1 != null) {
            val firstLine = td1[0].padEnd(30, '<')
            val secondLine = td1[1].padEnd(30, '<')
            val nameParts = td1[2].split("<<", limit = 2)
            return buildMap {
                putNames(nameParts)
                val documentId = firstLine.substringSafe(5, 14).cleanMrzValue()
                val countryCode = secondLine.substringSafe(15, 18).cleanMrzValue().toIso2CountryCode()
                put("document_number", documentId)
                put("document_id", documentId)
                put("issuing_country", firstLine.substringSafe(2, 5).cleanMrzValue().toIso2CountryCode())
                put("birth_date", secondLine.substringSafe(0, 6).formatMrzDate())
                put("sex", secondLine.substringSafe(7, 8).cleanMrzValue().toSexValue())
                put("expiry_date", secondLine.substringSafe(8, 14).formatMrzDate())
                put("nationality", countryCode)
                put("country_code", countryCode)
            }.filterValues { it.isNotBlank() }
        }

        return parsePartialMrz(mrzLines)
    }

    private fun MutableMap<String, String>.putNames(nameParts: List<String>) {
        val familyName = nameParts.getOrNull(0).orEmpty().cleanMrzValue().toTitleCaseAscii()
        val givenName = nameParts.getOrNull(1).orEmpty().cleanMrzValue().toTitleCaseAscii()
        if (familyName.isNotBlank()) put("family_name", familyName)
        if (givenName.isNotBlank()) put("given_name", givenName)
    }

    private fun parsePartialMrz(mrzLines: List<String>): Map<String, String> = buildMap {
        val nameLine = mrzLines.firstOrNull { it.contains("<<") && it.any(Char::isLetter) }
        nameLine?.let { line ->
            when {
                line.startsWith("P<") && line.length > 5 -> line.drop(5)
                line.startsWith("P") && line.length > 4 -> line.drop(4)
                else -> line
            }
        }
            ?.split("<<", limit = 2)
            ?.let { putNames(it) }

        val dataLine = mrzLines.firstOrNull { line ->
            line.count(Char::isDigit) >= 12 && line.length >= 20
        }
        dataLine?.let { line ->
            val dates = Regex("\\d{6}").findAll(line).map { it.value }.toList()
            dates.getOrNull(0)?.formatMrzDate()?.let { put("birth_date", it) }
            dates.getOrNull(1)?.formatMrzDate()?.let { put("expiry_date", it) }
            Regex("[MF]").find(line)?.value?.toSexValue()?.let { put("sex", it) }
            Regex("[A-Z]{3}").find(line)?.value?.toIso2CountryCode()?.let {
                put("country_code", it)
                put("nationality", it)
            }
        }
    }.filterValues { it.isNotBlank() }

    private fun findLabeledValue(lines: List<String>, canonicalKey: String): String? {
        val aliases = aliasesForClaim(canonicalKey)
        lines.forEachIndexed { index, line ->
            val normalizedLine = line.normalizedForMatching()
            val matchedAlias = aliases.firstOrNull { alias ->
                normalizedLine.startsWith("$alias ") ||
                        normalizedLine.startsWith("$alias:") ||
                        normalizedLine.startsWith("$alias.") ||
                        normalizedLine == alias
            }
            if (matchedAlias != null) {
                val inlineValue = line.substringAfter(':', "").ifBlank {
                    line.valueAfterNormalizedAlias(matchedAlias)
                }.trimLabelValue()
                return inlineValue.ifBlank { lines.getOrNull(index + 1)?.trimLabelValue() }
            }
        }
        return null
    }

    private fun findUnlabeledValue(lines: List<String>, canonicalKey: String): String? = when {
        canonicalKey.contains("date") -> lines.firstNotNullOfOrNull { extractDate(it) }
        canonicalKey.contains("document_number") ||
                canonicalKey.contains("document_id") ||
                canonicalKey.contains("administrative_number") ->
            lines.firstOrNull { it.matches(Regex(".*[A-Z0-9]{6,}.*", RegexOption.IGNORE_CASE)) }
                ?.replace(Regex("[^A-Za-z0-9]"), "")
        canonicalKey.contains("pin") ->
            lines.firstNotNullOfOrNull { line ->
                Regex("\\b\\d{9,12}\\b").find(line)?.value
            }
        canonicalKey.contains("country_code") || canonicalKey.contains("issuing_country") ->
            lines.firstOrNull { it.trim().matches(Regex("[A-Z]{2,3}", RegexOption.IGNORE_CASE)) }
                ?.trim()
                ?.uppercase()
                ?.toIso2CountryCode()
        canonicalKey.contains("sex") ->
            lines.firstNotNullOfOrNull { extractSex(it) }
        else -> null
    }

    private fun aliasesForClaim(canonicalKey: String): List<String> = when {
        canonicalKey.contains("family_name") || canonicalKey.contains("last_name") -> {
            listOf("family name", "surname", "last name", "name")
        }
        canonicalKey.contains("given_name") || canonicalKey.contains("first_name") -> {
            listOf("given names", "given name", "first name", "forenames")
        }
        canonicalKey.contains("birth_date") || canonicalKey.contains("date_of_birth") -> {
            listOf("date of birth", "birth date", "dob", "born")
        }
        canonicalKey.contains("expiry_date") || canonicalKey.contains("expiration_date") -> {
            listOf("expiry date", "expiration date", "valid until", "expires")
        }
        canonicalKey.contains("issuance_date") || canonicalKey.contains("issue_date") -> {
            listOf(
                "date of issue",
                "issue date",
                "issuing date",
                "issuance date",
                "issued",
                "vydane",
                "vydané",
                "datum vydania",
                "dátum vydania",
            )
        }
        canonicalKey.contains("pin") -> {
            listOf(
                "pin",
                "personal number",
                "personal id",
                "personal identification number",
                "identity number",
                "rodne cislo",
                "rodné číslo",
                "birth number",
            )
        }
        canonicalKey.contains("city") -> {
            listOf("city", "place of birth", "birth place", "miesto narodenia", "locality")
        }
        canonicalKey.contains("region") -> listOf("region", "kraj")
        canonicalKey.contains("district") -> listOf("district", "okres")
        canonicalKey.contains("document_number") || canonicalKey.contains("document_id") -> {
            listOf(
                "document id",
                "document number",
                "document no",
                "passport no",
                "passport number",
                "id number",
                "licence number",
                "license number",
                "number",
            )
        }
        canonicalKey.contains("issuing_authority") -> listOf("issuing authority", "authority")
        canonicalKey.contains("issuing_country") -> {
            listOf("issuing country", "country of issue", "issuing state", "country")
        }
        canonicalKey.contains("country_code") -> listOf("country code", "nationality", "country")
        canonicalKey.contains("nationality") -> listOf("nationality", "country code")
        canonicalKey.contains("sex") || canonicalKey.contains("gender") -> listOf("sex", "gender")
        canonicalKey.contains("address") -> listOf("address", "residence", "resident address")
        else -> listOf(canonicalKey.replace("_", " "))
    }.map { it.normalizedForMatching() }
}

private fun String.canonicalClaimKey(): String = lowercase()
    .replace(".", "_")
    .replace("-", "_")
    .let {
        when (it) {
            "surname", "last_name" -> "family_name"
            "firstname", "first_name", "forename" -> "given_name"
            "passport_number", "document_no", "document_number" -> "document_id"
            "date_of_birth", "dob" -> "birth_date"
            "expiration_date" -> "expiry_date"
            "issuing_date", "issue_date" -> "issuance_date"
            "nationality" -> "country_code"
            "gender" -> "sex"
            else -> it
        }
    }

private fun String.normalizeMrzLine(): String = uppercase()
    .replace('«', '<')
    .replace('‹', '<')
    .replace('＜', '<')
    .replace('⟨', '<')
    .replace(Regex("[^A-Z0-9<]"), "")

private fun String.isMrzLikeLine(): Boolean =
    length >= 12 && (
            count { it == '<' } >= 2 ||
                    startsWith("P") ||
                    startsWith("I") ||
                    startsWith("A") ||
                    count(Char::isDigit) >= 6
            )

private fun String.normalizedForMatching(): String = lowercase()
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private fun String.trimLabelValue(): String = trim()
    .trim(':', '.', '-', ' ')
    .takeIf { it.length >= 2 }
    .orEmpty()

private fun String.valueAfterNormalizedAlias(alias: String): String {
    val words = alias.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    var searchStart = 0
    words.forEach { word ->
        val match = Regex(Regex.escape(word), RegexOption.IGNORE_CASE).find(this, searchStart) ?: return ""
        searchStart = match.range.last + 1
    }
    return drop(searchStart)
}

private fun String.cleanMrzValue(): String = replace('<', ' ')
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.toTitleCaseAscii(): String = lowercase()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .joinToString(" ") { word ->
        word.replaceFirstChar { char -> char.uppercase() }
    }

private fun String.toIso2CountryCode(): String = uppercase().let { code ->
    when (code) {
        "AUT" -> "AT"
        "BEL" -> "BE"
        "BGR" -> "BG"
        "HRV" -> "HR"
        "CYP" -> "CY"
        "CZE" -> "CZ"
        "DNK" -> "DK"
        "EST" -> "EE"
        "FIN" -> "FI"
        "FRA" -> "FR"
        "DEU", "D<<" -> "DE"
        "GRC" -> "GR"
        "HUN" -> "HU"
        "IRL" -> "IE"
        "ITA" -> "IT"
        "LVA" -> "LV"
        "LTU" -> "LT"
        "LUX" -> "LU"
        "MLT" -> "MT"
        "NLD" -> "NL"
        "POL" -> "PL"
        "PRT" -> "PT"
        "ROU" -> "RO"
        "SVK" -> "SK"
        "SVN" -> "SI"
        "ESP" -> "ES"
        "SWE" -> "SE"
        "UKR" -> "UA"
        else -> code.takeIf { it.length == 2 } ?: code
    }
}

private fun String.toSexValue(): String = when (uppercase()) {
    "F", "FEMALE", "W", "Ž", "Z" -> "female"
    "M", "MALE" -> "male"
    else -> this
}

private fun extractSex(text: String): String? {
    val normalized = text.normalizedForMatching()
    return when {
        Regex("\\bf\\b").containsMatchIn(normalized) -> "female"
        Regex("\\bm\\b").containsMatchIn(normalized) -> "male"
        "female" in normalized || "woman" in normalized || "zena" in normalized || "žena" in normalized -> "female"
        "male" in normalized || "man" in normalized || "muz" in normalized || "muž" in normalized -> "male"
        else -> null
    }
}

private fun String.substringSafe(startIndex: Int, endIndex: Int): String =
    if (length >= endIndex) substring(startIndex, endIndex) else ""

private fun String.formatMrzDate(): String {
    if (!matches(Regex("\\d{6}"))) return cleanMrzValue()
    val year = take(2).toIntOrNull() ?: return cleanMrzValue()
    val month = substring(2, 4)
    val day = substring(4, 6)
    val century = if (year > 30) "19" else "20"
    return "$century${take(2)}$month$day"
}

private fun extractDate(text: String): String? {
    Regex("\\b(\\d{4})(\\d{2})(\\d{2})\\b").find(text)?.let {
        return "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}"
    }
    Regex("(\\d{4})[-./ ](\\d{1,2})[-./ ](\\d{1,2})").find(text)?.let {
        return "${it.groupValues[1]}${it.groupValues[2].padStart(2, '0')}${it.groupValues[3].padStart(2, '0')}"
    }
    Regex("(\\d{1,2})[-./ ](\\d{1,2})[-./ ](\\d{2,4})").find(text)?.let {
        val year = it.groupValues[3].let { value -> if (value.length == 2) "20$value" else value }
        return "$year${it.groupValues[2].padStart(2, '0')}${it.groupValues[1].padStart(2, '0')}"
    }
    return null
}
