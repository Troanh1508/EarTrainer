package com.example.eartrainer.ui.about

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** The license texts ship with the app and are the texts they claim to be. */
class OpenSourceLicenseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyLicenseTextLoads() {
        val expected = mapOf(
            OpenSourceLicense.APACHE_2_0 to "Apache License",
            OpenSourceLicense.MIT_FEATHER to "Cole Bemis",
            OpenSourceLicense.CC0_1_0 to "CC0 1.0 Universal",
        )
        for (license in OpenSourceLicense.entries) {
            val text = license.readText(context)
            assertTrue("$license: expected \"${expected[license]}\"", text.contains(expected.getValue(license)))
        }
    }
}
