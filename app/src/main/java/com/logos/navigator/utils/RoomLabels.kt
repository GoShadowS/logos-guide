package com.logos.navigator.utils

import android.content.Context
import com.logos.navigator.R
import com.logos.navigator.data.model.DistanceUnit
import com.logos.navigator.data.model.Room
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

/** Подписи, зависящие от активной локали Android. */
fun Context.categoryLabel(category: String): String = getString(
    when (category.lowercase(Locale.ROOT)) {
        "classroom" -> R.string.category_classrooms
        "laboratory", "lab" -> R.string.category_laboratories
        "administrative" -> R.string.category_administrative
        "medical" -> R.string.category_medical
        "common" -> R.string.category_common
        "entrance" -> R.string.category_entrance
        else -> R.string.category_other
    },
)

fun Context.nodeTypeLabel(type: String): String = getString(
    when (type.lowercase(Locale.ROOT)) {
        "stairs" -> R.string.node_type_stairs
        "elevator" -> R.string.node_type_elevator
        "door" -> R.string.node_type_door
        "entrance" -> R.string.node_type_entrance
        "exit" -> R.string.node_type_exit
        "junction" -> R.string.node_type_junction
        else -> R.string.node_type_hallway
    },
)

fun Room.localizedName(context: Context): String {
    val language = context.resources.configuration.locales[0]?.language
    return if (language == "en") nameEn?.takeIf(String::isNotBlank) ?: name else name
}

fun Room.localizedDescription(context: Context): String? {
    val language = context.resources.configuration.locales[0]?.language
    return (if (language == "en") descriptionEn else description)
        ?.takeIf(String::isNotBlank)
}

/** Возвращает варианты строки для поиска на русском и в распространённых вариантах транслитерации. */
fun searchForms(value: String): Set<String> {
    val normalized = normalizeSearchText(value)
    if (normalized.isEmpty()) return emptySet()
    val rawText = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "")
    val rawTerms = rawText.split(SEARCH_TOKEN_SPLITTER).filter(String::isNotEmpty)
    val terms = (listOf(normalized) + rawTerms.map(::normalizeSearchText))
        .filter(String::isNotEmpty)
        .distinct()
    return buildSet {
        terms.forEach { term ->
            add(term)
            add(transliterateRussianToLatin(term))
            add(normalizeSearchText(transliterateLatinToRussian(term)))
        }
        rawTerms.forEach { term ->
            add(term.filter(Char::isLetterOrDigit))
            add(normalizeSearchText(transliterateRussianToLatin(term)))
        }
    }.filter(String::isNotEmpty).toSet()
}

/** Нормализует регистр, ё/е, диакритику и пробелы/знаки препинания. */
fun normalizeSearchText(value: String): String {
    val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD)
        .replace(MARKS, "")
    return withoutDiacritics.filter(Char::isLetterOrDigit)
}

private fun transliterateRussianToLatin(value: String): String = buildString(value.length) {
    value.forEach { character -> append(RU_TO_LATIN[character] ?: character) }
}

/** Принимает самые частые латинские сочетания, включая варианты sh/ch/zh/ya/yu. */
private fun transliterateLatinToRussian(value: String): String {
    val result = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val match = LATIN_SEQUENCES.firstOrNull { sequence -> value.startsWith(sequence.first, index) }
        if (match != null) {
            result.append(match.second)
            index += match.first.length
        } else {
            result.append(LATIN_TO_RU[value[index]] ?: value[index])
            index++
        }
    }
    return result.toString()
}

fun Context.formatDistance(meters: Double?, unit: DistanceUnit = DistanceUnit.METERS): String {
    if (meters == null || !meters.isFinite()) return getString(R.string.distance_unknown)
    return when (unit) {
        DistanceUnit.METERS -> if (meters < 1_000.0) {
            getString(R.string.meters_format, meters.toInt().coerceAtLeast(1))
        } else {
            getString(R.string.kilometers_format, meters / 1_000.0)
        }
        DistanceUnit.STEPS -> getString(R.string.steps_format, ceil(meters / 0.75).toInt().coerceAtLeast(1))
    }
}

private val MARKS = Regex("\\p{M}+")
private val SEARCH_TOKEN_SPLITTER = Regex("[^\\p{L}\\p{N}]+")
private val RU_TO_LATIN = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
    'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
    'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
    'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
    'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)
private val LATIN_SEQUENCES = listOf(
    "shch" to "щ", "sch" to "щ", "yo" to "ё", "jo" to "ё", "zh" to "ж", "kh" to "х",
    "ts" to "ц", "ch" to "ч", "sh" to "ш", "yu" to "ю", "ju" to "ю", "ya" to "я", "ja" to "я",
).sortedByDescending { it.first.length }
private val LATIN_TO_RU = mapOf(
    'a' to 'а', 'b' to 'б', 'c' to 'к', 'd' to 'д', 'e' to 'е', 'f' to 'ф', 'g' to 'г', 'h' to 'х',
    'i' to 'и', 'j' to 'й', 'k' to 'к', 'l' to 'л', 'm' to 'м', 'n' to 'н', 'o' to 'о', 'p' to 'п',
    'q' to 'к', 'r' to 'р', 's' to 'с', 't' to 'т', 'u' to 'у', 'v' to 'в', 'w' to 'в', 'x' to 'х',
    'y' to 'ы', 'z' to 'з',
)
