package com.logos.navigator.utils

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
}
