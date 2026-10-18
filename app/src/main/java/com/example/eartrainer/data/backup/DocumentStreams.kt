package com.example.eartrainer.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** Opens documents the user picked with the system file picker, identified by their URI. */
interface DocumentStreams {
    /** @throws java.io.IOException or [SecurityException] if the document cannot be opened. */
    fun openInput(uri: String): InputStream

    /** Opens the document for writing, replacing its content. */
    fun openOutput(uri: String): OutputStream
}

class ContentResolverDocuments(private val resolver: ContentResolver) : DocumentStreams {
    override fun openInput(uri: String): InputStream =
        resolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)

    override fun openOutput(uri: String): OutputStream {
        val parsed = Uri.parse(uri)
        // "wt" truncates an existing file; some providers only support plain "w".
        val stream = try {
            resolver.openOutputStream(parsed, "wt")
        } catch (_: IllegalArgumentException) {
            resolver.openOutputStream(parsed, "w")
        } catch (_: FileNotFoundException) {
            resolver.openOutputStream(parsed, "w")
        }
        return stream ?: throw FileNotFoundException(uri)
    }
}
