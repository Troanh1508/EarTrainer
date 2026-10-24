package com.example.eartrainer.ui.about

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.example.eartrainer.R

/** A license the app's bundled works are used under, and where its full text ships with the app. */
enum class OpenSourceLicense(
    @param:StringRes val title: Int,
    @param:RawRes val rawText: Int? = null,
    /** An asset holding the text instead, when it already ships with the work it covers. */
    val assetText: String? = null,
) {
    APACHE_2_0(R.string.license_apache_2_0, rawText = R.raw.license_apache_2_0),

    /** Feather's MIT license, which must be shown with its copyright line. */
    MIT_FEATHER(R.string.license_mit, rawText = R.raw.license_mit_feather),

    // The same text ships next to each instrument's samples.
    CC0_1_0(R.string.license_cc0, assetText = "instruments/upright-piano-kw/LICENSE-CC0.txt"),
    ;

    fun readText(context: Context): String = when {
        rawText != null -> context.resources.openRawResource(rawText).bufferedReader().use { it.readText() }
        assetText != null -> context.assets.open(assetText).bufferedReader().use { it.readText() }
        else -> error("$name has no text")
    }
}

/** One bundled work: what it is, who holds its rights, and its license. */
data class OpenSourceNotice(val name: String, val holder: String, val license: OpenSourceLicense)

/** Everything the app ships that someone else made, by kind. Names are proper nouns and stay untranslated. */
object OpenSourceNotices {
    val sounds = listOf(
        OpenSourceNotice("Upright Piano KW", "FreePats project, recorded by Gonzalo and Roberto", OpenSourceLicense.CC0_1_0),
        OpenSourceNotice("Spanish Classical Guitar", "FreePats project, recorded by Roberto", OpenSourceLicense.CC0_1_0),
    )

    val icons = listOf(
        OpenSourceNotice("Material Icons", "Google", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("Feather music icon (Dictation)", "Copyright (c) 2013-2023 Cole Bemis", OpenSourceLicense.MIT_FEATHER),
    )

    /** The release build's runtime libraries, from `./gradlew :app:dependencies`. */
    val libraries = listOf(
        OpenSourceNotice("Android Jetpack (AndroidX)", "The Android Open Source Project", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("Kotlin standard library", "JetBrains s.r.o. and Kotlin Programming Language contributors", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("kotlinx.coroutines", "JetBrains s.r.o. and contributors", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("kotlinx.serialization", "JetBrains s.r.o. and contributors", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("JetBrains Java Annotations", "JetBrains s.r.o.", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("Okio", "Square, Inc.", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("Guava ListenableFuture", "The Guava Authors", OpenSourceLicense.APACHE_2_0),
        OpenSourceNotice("JSpecify", "The JSpecify Authors", OpenSourceLicense.APACHE_2_0),
    )

    /** Each license used, once, in order of first use. */
    val licenses: List<OpenSourceLicense> get() = (sounds + icons + libraries).map { it.license }.distinct()
}
