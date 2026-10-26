# Module 12: Persistence with DataStore and Room

[Course index](README.md) · Previous: [Module 11](11-navigation.md) · Next: [Module 13: Files, serialization and backups](13-files-and-backup.md)

## Why it matters

An ear trainer that forgets your settings and history is useless. Android gives you two
standard tools: **DataStore** for small key-value settings, and **Room** for structured data
you query (every answered question). Both expose data as `Flow`, so the UI updates itself when
data changes. This module also covers the hardest part of persistence: **changing the schema
without losing users' data**.

Read [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) and
[Save data in a local database using Room](https://developer.android.com/training/data-storage/room).

## Concepts

### Preferences DataStore

- A file of typed key-value pairs (`intPreferencesKey("…")`, `stringSetPreferencesKey("…")`).
- `dataStore.data: Flow<Preferences>` emits the current values and every change.
- `dataStore.edit { prefs -> ... }` updates atomically on a background thread.
- Exactly **one** DataStore instance per file per process. Two instances on the same file
  will corrupt it. Hence the single `preferencesDataStore(name = "settings")` delegate.
- It replaces the old `SharedPreferences`, which did disk I/O on the main thread.

### Room

Room is a layer over SQLite that checks your SQL at compile time:

- `@Entity`: a table. `@ColumnInfo(name = …)` fixes column names independently of property
  names, so renaming a Kotlin property doesn't rename a column.
- `@Dao`: an interface of queries. `@Insert` / `@Query("SELECT …")`. Room's compiler (run
  through KSP) generates the implementation.
- `suspend fun` DAO methods run off the main thread. **`Flow`-returning queries re-run
  automatically whenever a table they read changes.** That's what keeps the Progress and Home
  screens live.
- `@Database(entities = …, version = N)` ties it together.

### Schema migrations

Every installed copy of your app has a database at some old version. When you change the
schema you must provide a **migration** from each old version, or Room refuses to open the
database. (You could also allow it to wipe the data, which is unacceptable for user history.)

- **Auto-migrations**: for simple changes (add a table, add a column with a default), Room
  writes the migration itself, given the **exported schema JSON** of both versions. That's why
  `app/schemas/` is committed to git.
- **Manual migrations**: renames, splits and data transforms need hand-written SQL in a
  `Migration(from, to)`.
- **Migration tests** create a database at an old version, insert rows with raw SQL, run the
  migrations, and verify both the schema and the data.

### Storing enums: by name, never by ordinal

Store `Interval.MAJOR_THIRD.name` ("MAJOR_THIRD"), not its `ordinal` (3). Inserting a new
enum entry would shift ordinals and silently corrupt every stored row. Names survive
reordering. Renames still need care, which is why decoders here ignore unknown names.

## Read the code

### Settings stores

[IntervalSettingsStore.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStore.kt):

- [Lines 23–27](../../app/src/main/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStore.kt#L23):
  the settings flow. `catch` turns an unreadable file into defaults ("should not block
  practice"). `distinctUntilChanged` matters because **any** key changing in the shared
  settings file re-emits `data`, including other exercises' keys.
- [`update`, line 29](../../app/src/main/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStore.kt#L29):
  decode → transform → encode inside one `edit`, so it's atomic.
- Key names are namespaced: `"interval_training.intervals"`. They're also part of the backup
  format and shared with iOS (root README), so they must never change.
- [`decode`, lines 43–64](../../app/src/main/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStore.kt#L43):
  **defensive decoding**. Unknown enum names are dropped (`mapNotNull`), out-of-range values fall
  back to defaults, and if the combination still violates `IntervalExerciseSettings`'
  invariants, `runCatching { … }.getOrDefault(defaults)` saves the day. Stored data is input
  from the past, and may be invalid.
- Tests: [IntervalSettingsStoreTest](../../app/src/test/java/com/troanhmolo/eartrainer/data/settings/IntervalSettingsStoreTest.kt)
  uses [InMemoryPreferences](../../app/src/test/java/com/troanhmolo/eartrainer/testing/InMemoryPreferences.kt),
  a 7-line fake DataStore backed by a `MutableStateFlow`. Implementing a small interface
  yourself is often simpler than mocking.

### The database

- [EarTrainerDatabase.kt](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/EarTrainerDatabase.kt):
  the KDoc keeps a **version history**. One table was added per exercise, each with an
  `AutoMigration`. Read [app/schemas/…/6.json](../../app/schemas/com.troanhmolo.eartrainer.data.history.EarTrainerDatabase/6.json)
  to see what Room exports.
- [HistoryEntities.kt, lines 14–38](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/HistoryEntities.kt#L14):
  the comment at the top says why enums are stored by name and why properties are only ever
  added with defaults (entities double as the backup format, Module 13). Note the `indices` on
  `session_id` and `interval`: those columns appear in `WHERE` and `GROUP BY` clauses.
- [IntervalAttemptDao, lines 193–242](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/HistoryEntities.kt#L193):
  read every query. Note:
  - `WHERE :style IS NULL OR style = :style`: one query serves "all styles" and "one style".
  - `SUM(is_correct)`: booleans are stored as 0/1, so summing counts the correct answers.
  - Aggregates are mapped to small row classes (`AccuracyRow`, `SessionTotalsRow`) by column
    alias (`AS item`, `AS attempts`).
- [SessionDao, line 485 onward](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/HistoryEntities.kt#L485):
  `UNION ALL` across six tables, and `strftime(…, 'localtime', 'start of day')` to bucket
  answers into the user's **local** calendar days. Read the KDoc on time zones. Date bucketing
  is a classic source of off-by-one-day bugs.

### The repository

[RoomPracticeHistory, line 183 onward](../../app/src/main/java/com/troanhmolo/eartrainer/data/history/PracticeHistory.kt#L183):
maps rows to domain types (`completeOver(Interval.entries)` fills in intervals never
practiced, so charts show all of them). Heavier processing uses `.flowOn(Dispatchers.Default)`.
Writes go through the application-scoped `write { }` from Module 6.

### Migration tests

[DatabaseMigrationTest.kt](../../app/src/androidTest/java/com/troanhmolo/eartrainer/data/history/DatabaseMigrationTest.kt#L44):
`MigrationTestHelper` creates a version-1 database, inserts a row with raw SQL, and runs each
migration up to 6, validating the schema against the exported JSON at each step. Then it checks
the old row is still there. Run it with `./gradlew connectedAndroidTest` on a device.

### Database Inspector

With the app running from Android Studio, open *App Inspection → Database Inspector*. You can
browse `interval_attempts` live and run your own SQL against the device's database.

## Exercises

Branch: `git switch -c learning/module-12`.

1. **Query by hand.** In the Database Inspector, write SQL for: your accuracy per interval
   style, your slowest interval on average (`response_ms`), and how often you replayed before
   answering (`play_count`). Then add one of them as a DAO method with a `Flow` return type.
2. **A setting end to end: playback speed.** Add a "Tempo" setting (Slow 0.8×, Normal 1×,
   Fast 1.25×) to `SoundSettings`. It scales `PlaybackTiming`'s durations.
   - Model: add the field with a default, plus validation.
   - Store: a new key in `SoundSettingsStore`, with defensive decode. Test it with
     `InMemoryPreferences`.
   - UI: chips next to the instrument choice (`InstrumentSetting.kt`).
   - Audio: `AudioTrackPlayer` currently gets a fixed `PlaybackTiming`. Make it read the
     current tempo for each prompt the same way it reads the instrument. Don't forget the
     render cache.
3. **Add a column (auto-migration).** Add `instrument` (`String`, default `"PIANO"`) to
   `IntervalAttemptEntity`, bump the version to 7, and add `AutoMigration(6, 7)`. Build so the
   `7.json` schema is exported. Extend `DatabaseMigrationTest` with a 6→7 test. Why must the
   column have a default value?
4. **A manual migration.** In a throwaway branch, rename the `response_ms` column to
   `response_millis` in version 8. What does the auto-migration need from you
   (`@RenameColumn` and an `AutoMigrationSpec`)? Write a manual `Migration` instead and
   compare.
5. **Ordinal disaster (thought experiment).** Suppose intervals were stored by `ordinal` and
   someone inserted `MINOR_NINTH` between `MAJOR_SEVENTH` and `OCTAVE`. What happens to every
   stored octave answer? To the progress chart?
6. **Stretch: a session table.** History stores attempts. Sessions are derived by
   `GROUP BY session_id`. Design (don't build yet) an explicit `sessions` table. What would you
   gain, what migration would backfill it from existing attempts, and which queries get
   simpler?

## Checkpoint

1. Why does the app create exactly one `preferencesDataStore` and share it?
2. How do Room `Flow` queries know when to emit again?
3. Why are Room schema JSON files committed to git?
4. What makes a migration "automatic", and when do you need a manual one?
5. Why does `decode` wrap construction in `runCatching`?
6. Why store enums by name?

<details>
<summary>Answers</summary>

1. DataStore requires a single instance per file per process. Multiple instances on the same
   file can corrupt it. Every settings store uses the same file under its own key prefix.
2. Room tracks which tables each query reads and invalidates the query when a write touches
   one of those tables, then re-runs it.
3. Auto-migrations are generated by comparing the old and new schema JSON, and migration
   tests validate against them. Without the old JSON, you can't generate or test a
   migration from that version.
4. Room can infer additive changes (new tables, new columns with defaults). Renames, deletes,
   type changes and data transforms need an `AutoMigrationSpec` or hand-written SQL.
5. Stored values may combine into settings that violate today's invariants (for example after
   an app update tightened a rule). Falling back to defaults beats crashing at launch.
6. Names survive reordering and insertion of new entries. Ordinals silently change meaning.

</details>
