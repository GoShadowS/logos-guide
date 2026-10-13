package com.logos.navigator.utils

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLabelsTest {
    @Test
    fun searchFormsNormalizesAliasesAndRussianTransliteration() {
        val forms = searchForms("Метеорология 236")
        assertTrue(forms.contains("метеорология236"))
        assertTrue(forms.contains("meteorologiya236"))
        assertTrue(forms.contains("метеорология"))
        assertTrue(forms.contains("236"))
    }

    @Test
    fun searchFormsSupportsYoAndSeparatedEnglishTerms() {
        val russianName = searchForms("Тренажёрный центр")
        assertTrue(russianName.contains("trenazhyornyy"))
        assertTrue(searchForms("room 214").contains("214"))
        assertTrue(searchForms("yozh").contains("еж"))
    }

    @Test
    fun roomSearchRequiresEveryTermAndRanksExactRoomNumbersFirst() {
        val exactNumber = roomSearchScore(
            query = "kab 118",
            roomNumber = "118",
            searchableValues = listOf("Каб. 118", "Кабинет физики", "Physics classroom"),
        )
        val partialNumber = roomSearchScore(
            query = "kab 118",
            roomNumber = "1188",
            searchableValues = listOf("Каб. 1188", "Кабинет физики", "Physics classroom"),
        )
        val missingTerm = roomSearchScore(
            query = "kab 118",
            roomNumber = "119",
            searchableValues = listOf("Каб. 119", "Кабинет физики", "Physics classroom"),
        )

        assertNotNull(exactNumber)
        assertNotNull(partialNumber)
        assertTrue(exactNumber!! > partialNumber!!)
        assertNull(missingTerm)
    }

    @Test
    fun roomSearchMatchesPartialTransliterationAndAliases() {
        val score = roomSearchScore(
            query = "stolov",
            roomNumber = "126",
            searchableValues = listOf("Столовая", "cafeteria", "food hall"),
        )

        assertNotNull(score)
    }
}
