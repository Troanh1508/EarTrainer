package com.troanhmolo.eartrainer.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceNoticesTest {

    private val all = OpenSourceNotices.sounds + OpenSourceNotices.icons + OpenSourceNotices.libraries

    @Test
    fun everyLicenseInUseIsListedOnceWithItsText() {
        assertEquals(listOf(OpenSourceLicense.CC0_1_0, OpenSourceLicense.APACHE_2_0, OpenSourceLicense.MIT_FEATHER), OpenSourceNotices.licenses)
        assertTrue(OpenSourceNotices.licenses.all { it.rawText != null || it.assetText != null })
    }

    @Test
    fun licenseTextsReflowIntoParagraphs() {
        assertEquals("One line wrapped.\n\nNext paragraph.", reflow("  One line\n   wrapped.\n\n  \nNext\nparagraph.\n"))
    }

    @Test
    fun everyNoticeNamesItsWorkAndRightsHolder() {
        assertTrue(all.all { it.name.isNotBlank() && it.holder.isNotBlank() })
        assertEquals(all.size, all.map { it.name }.distinct().size)
    }
}
