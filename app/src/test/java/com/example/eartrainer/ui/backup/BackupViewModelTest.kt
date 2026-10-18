package com.example.eartrainer.ui.backup

import com.example.eartrainer.data.backup.AppDataBackup
import com.example.eartrainer.data.backup.BackupException
import com.example.eartrainer.data.backup.DocumentStreams
import com.example.eartrainer.data.backup.ImportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeDocuments : DocumentStreams {
        val written = mutableMapOf<String, ByteArrayOutputStream>()
        var readable = mutableMapOf("content://backup" to "{}")

        override fun openInput(uri: String): InputStream =
            ByteArrayInputStream((readable[uri] ?: throw FileNotFoundException(uri)).toByteArray())

        override fun openOutput(uri: String): OutputStream {
            if (uri.startsWith("content://read-only")) throw SecurityException(uri)
            return ByteArrayOutputStream().also { written[uri] = it }
        }
    }

    private class FakeBackup : AppDataBackup {
        var importResult: () -> ImportResult = { ImportResult(added = 3, alreadyPresent = 1, unreadable = 0, settingsRestored = true) }
        var gate: CompletableDeferred<Unit>? = null
        var exports = 0

        override suspend fun export(output: OutputStream): Int {
            gate?.await()
            exports++
            output.use { it.write("backup".toByteArray()) }
            return 42
        }

        override suspend fun import(input: InputStream): ImportResult = input.use { importResult() }
    }

    private val documents = FakeDocuments()
    private val backup = FakeBackup()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BackupViewModel(backup, documents, ioDispatcher = dispatcher)

    @Test
    fun exportWritesToThePickedDocumentAndReportsTheAnswerCount() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onExportTo("content://new")
        assertEquals("backup", documents.written.getValue("content://new").toString())
        assertEquals(BackupUiState(message = BackupMessage.Exported(42)), viewModel.state.value)

        viewModel.onMessageShown()
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun importReportsWhatChanged() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onImportFrom("content://backup")
        assertEquals(BackupMessage.Imported(ImportResult(3, 1, 0, true)), viewModel.state.value.message)
    }

    @Test
    fun failuresAreReportedByReason() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onImportFrom("content://missing")
        assertEquals(BackupMessage.Failed(BackupAction.IMPORT, FailureReason.FILE_ERROR), viewModel.state.value.message)

        viewModel.onExportTo("content://read-only/file")
        assertEquals(BackupMessage.Failed(BackupAction.EXPORT, FailureReason.FILE_ERROR), viewModel.state.value.message)

        BackupException.Reason.entries.forEach { reason ->
            backup.importResult = { throw BackupException(reason) }
            viewModel.onImportFrom("content://backup")
            assertEquals(FailureReason.valueOf(reason.name), (viewModel.state.value.message as BackupMessage.Failed).reason)
        }
    }

    @Test
    fun onlyOneActionRunsAtATime() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { backup.gate = it }
        val viewModel = viewModel()
        viewModel.onExportTo("content://first")
        assertEquals(BackupUiState(working = BackupAction.EXPORT), viewModel.state.value)

        viewModel.onExportTo("content://second")
        viewModel.onImportFrom("content://backup")
        assertNull(documents.written["content://second"])

        gate.complete(Unit)
        assertEquals(1, backup.exports)
        assertEquals(BackupMessage.Exported(42), viewModel.state.value.message)
    }
}
