# Module 13: Files, serialization and backups

[Course index](README.md) · Previous: [Module 12](12-persistence.md) · Next: [Module 14: Custom drawing with Canvas](14-custom-drawing.md)

## Why it matters

Users change phones, reinstall apps, and want their data portable. This module covers how an
Android app reads and writes **user-chosen files** without storage permissions, how to design
a **file format that survives app updates**, and how to treat imported data as untrusted
input. These skills transfer to any app that exports or syncs data.

Read [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)
and the [kotlinx.serialization guide](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md)
(the "Basic serialization" and "JSON features" chapters).

## Concepts

### Scoped storage and the Storage Access Framework (SAF)

Modern Android doesn't let apps roam the file system. To save or open a file the user
chooses, you launch the **system file picker**:

- `ActivityResultContracts.CreateDocument(mimeType)` asks the user where to *save*.
- `ActivityResultContracts.OpenDocument()` asks the user which file to *open*.

Both return a **content `Uri`**, not a path. The file might be local, on an SD card or in
Google Drive. You read and write it with `contentResolver.openInputStream(uri)` /
`openOutputStream(uri)`. **No storage permission is needed**: the user's choice in the picker
is the permission.

### Activity results in Compose

`rememberLauncherForActivityResult(contract) { result -> … }` registers a launcher that
survives recomposition and configuration changes. Call `launcher.launch(input)` from an event
handler.

### Designing a file format that lasts

A backup written today must import into next year's app, and ideally the reverse. Rules:

1. **Identify the file** (`"app": "…"`), so random JSON isn't mistaken for a backup.
2. **Version it** (`formatVersion`). Bump only for changes old readers can't handle, and
   reject versions newer than you understand with a clear message.
3. **Be tolerant when reading**: ignore unknown fields, default missing ones. Then a newer
   app can add fields without breaking older backups.
4. **Never rename or remove** a field once it's shipped. Add new ones with defaults.
5. **Validate everything** read from outside. A user can hand-edit the file, or pick a
   500 MB video by mistake.
6. **All or nothing**: parse and validate fully before writing anything, then write in a
   transaction.

### Auto Backup

Separately, Android's **Auto Backup** can copy app data to the user's Google account and
restore it on a new device, with no code. You declare what to include in XML rules.

## Read the code

### The format

[data/backup/BackupFile.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/BackupFile.kt)

- [Lines 14–37](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/BackupFile.kt#L14):
  the KDoc states the compatibility rules. `@Required` on `app` and `formatVersion` makes
  them mandatory even though they have defaults.
- [Lines 32–34](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/BackupFile.kt#L32):
  `APP = "com.example.eartrainer"`. The package was renamed to `com.troanhmolo`, but this
  stays. The comment explains: it's part of the file format, not the app ID. This is the
  rule "identifiers in persisted formats are forever" in practice.
- [`SettingValue`, lines 52–62](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/BackupFile.kt#L52):
  a **flat** object with optional fields instead of a sealed/polymorphic type, so "a value type
  added later is skipped by older readers instead of failing the whole file". That's a
  deliberate trade-off between type safety and forward compatibility.
- [`BackupCodec`, lines 78–101](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/BackupFile.kt#L78):
  `Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }`. Parse
  errors become a typed `BackupException(NOT_A_BACKUP)`, so the UI can show a helpful message.
- History rows reuse the Room entities (`@Serializable` on
  [IntervalAttemptEntity](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/HistoryEntities.kt#L20)).
  `@Transient` on `id` leaves the database key out of the file, so imported rows get fresh
  ids. That's convenient, and it's also why the entity comment warns about renaming
  properties.

### Export and import

[data/backup/DataBackup.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/DataBackup.kt)

- [`export`, lines 52–67](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/DataBackup.kt#L52):
  `withContext(ioDispatcher)` for blocking I/O. `database.withTransaction { }` reads all six
  tables as one consistent snapshot, so an answer saved mid-export can't appear in one table
  and not another. `ioDispatcher` is a constructor parameter, so tests can inject a test
  dispatcher.
- [`import`, lines 69–92](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/DataBackup.kt#L69):
  "Parsed and checked in full before anything is written, so a bad file changes nothing."
  Rows are merged, not replaced: an answer's identity is `(sessionId, answeredAtMillis)`, so
  importing the same backup twice adds nothing. Invalid rows are counted, not fatal.
- [`readLimited`, lines 127–136](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/DataBackup.kt#L127):
  refuses files over 64 MB *while reading*, before they can exhaust memory.
- [HistoryValidation.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/HistoryValidation.kt):
  `isValid()` checks each imported row (known enum names, MIDI in range…). Imported data is
  untrusted.

### Talking to the file picker

- [BackupRoute, lines 51–64](../../app/src/main/java/com/troanhmolo/eartrainer/ui/backup/BackupScreen.kt#L51):
  two launchers. `IMPORT_MIME_TYPES` accepts several types because "file managers label JSON
  files inconsistently". Again, real-world knowledge written down where it's used.
- [DocumentStreams.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/backup/DocumentStreams.kt):
  the `ContentResolver` hidden behind an interface, so `BackupViewModel` is testable on the
  JVM. Note the `"wt"` (write + truncate) mode with a fallback to `"w"`: without truncation,
  writing a shorter file over a longer one leaves old bytes at the end.
- [BackupViewModel](../../app/src/main/java/com/troanhmolo/eartrainer/ui/backup/BackupViewModel.kt):
  URIs are passed as `String`s so the ViewModel doesn't depend on `android.net.Uri`.

### Auto Backup rules

[backup_rules.xml](../../app/src/main/res/xml/backup_rules.xml) (Android 11 and lower) and
[data_extraction_rules.xml](../../app/src/main/res/xml/data_extraction_rules.xml)
(Android 12+) include the database and the DataStore folder. They're referenced from the
manifest's `<application>` attributes.

## Exercises

Branch: `git switch -c learning/module-13`.

1. **Round trip.** Export a backup, open it in a text editor on your computer, and find your
   interval answers. Delete the app's data (*Settings → Apps → Ear Trainer → Storage → Clear
   data*), import the file, and check that Progress is back.
2. **Attack your own importer.** Try importing: an empty file, a photo, valid JSON from another
   app, a backup with `"formatVersion": 99`, and a backup where you changed one interval's
   name to `"BANANA"`. What does each produce? Find the code path for each.
3. **Forward compatibility test.** In
   [BackupCodecTest](../../app/src/test/java/com/troanhmolo/eartrainer/data/backup/BackupCodecTest.kt),
   add a test that decodes a backup containing an unknown top-level field and an unknown field
   inside an interval row. It should succeed.
4. **Add a field the right way.** Continue Module 12's exercise 3 (the `instrument` column).
   Does the backup format need a version bump? Write a test that imports an *old* backup
   (without `instrument`) and checks the default is applied.
5. **Stretch: CSV export.** Add "Export as CSV" for interval history, for spreadsheet users.
   Use `CreateDocument("text/csv")`. Handle commas and quotes in fields correctly. Should it go
   through `AppDataBackup` or something new? Write tests for the CSV writer.

## Checkpoint

1. Why doesn't the backup feature need a storage permission?
2. Why does the importer parse the whole file before writing anything?
3. What does `ignoreUnknownKeys = true` buy you, and when would you *not* want it?
4. Why wasn't `BackupFile.APP` updated when the package was renamed?
5. Why does export read all tables in one transaction?

<details>
<summary>Answers</summary>

1. The user picks the location in the system file picker, which grants the app access to that
   one document through a content URI.
2. So a malformed or unreadable file changes nothing. The import is all or nothing, not half
   applied.
3. Newer backups with extra fields still import into older app versions. You might not want
   it for strict protocols where an unknown field signals a real error you must not ignore.
4. It identifies the file format, and changing it would make every previously exported backup
   fail the `app` check.
5. To get a consistent snapshot. Otherwise a write between table reads could leave the backup
   describing a state that never existed.

</details>
