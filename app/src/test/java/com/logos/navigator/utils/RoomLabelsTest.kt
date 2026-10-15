package com.logos.navigator.utils

import com.logos.navigator.data.model.Room
import org.junit.Assert.assertEquals
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

    @Test
    fun roomNumberLabelsPreserveEveryMergedCabinetNumber() {
        val room = Room(
            id = "room_education_department",
            name = "Учебная часть",
            number = "141, 142, 147, 159, 160",
            floor = 1,
            category = "administrative",
        )

        assertEquals(listOf("141", "142", "147", "159", "160"), room.numberLabels())
    }

    @Test
    fun matchingNumberLabelKeepsTheEnteredMergedRoomNumberRelevant() {
        val room = Room(
            id = "room_education_department",
            name = "Учебная часть",
            nameEn = "Academic Office",
            number = "141, 142, 147, 159, 160",
            floor = 1,
            category = "administrative",
        )

        assertEquals("142", room.matchingNumberLabel("142"))
        assertEquals("142", room.matchingNumberLabel("кабинет 142"))
        assertEquals("141", room.matchingNumberLabel("14"))
        assertNull(room.matchingNumberLabel("бухгалтерия"))
    }

    @Test
    fun mergedAcademicOfficeMatchesEachCabinetNumber() {
        val number = "141, 142, 147, 159, 160"
        val searchableValues = listOf(
            "Учебная часть",
            "Academic Office",
            "Каб. 141, 142, 147, 159, 160",
            "Room 141, 142, 147, 159, 160",
        )

        listOf("141", "142", "147", "159", "160").forEach { cabinetNumber ->
            assertNotNull(roomSearchScore(cabinetNumber, number, searchableValues))
        }
        assertNotNull(roomSearchScore("kab 142", number, searchableValues))
    }
}
