package com.logos.navigator.update

private val versionPattern = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9.-]+)?$")

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = parseVersion(candidate) ?: return false
    val currentParts = parseVersion(current) ?: return false

    for (index in candidateParts.indices) {
        if (candidateParts[index] != currentParts[index]) {
            return candidateParts[index] > currentParts[index]
        }
    }
    return false
}

private fun parseVersion(value: String): IntArray? {
    val match = versionPattern.matchEntire(value) ?: return null
    val parts = IntArray(3)
    for (index in 1..3) {
        parts[index - 1] = match.groupValues[index].toIntOrNull() ?: return null
    }
    return parts
}
