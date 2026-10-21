import Foundation
import SQLite3

enum SQLiteError: Error, CustomStringConvertible {
    case open(String)
    case prepare(String, sql: String)
    case step(String)

    var description: String {
        switch self {
        case .open(let message): "Could not open database: \(message)"
        case .prepare(let message, let sql): "Could not prepare \(sql): \(message)"
        case .step(let message): "Statement failed: \(message)"
        }
    }
}

enum SQLValue {
    case int(Int64)
    case double(Double)
    case text(String)
    case null

    init(_ value: String?) { self = value.map(SQLValue.text) ?? .null }
    init(_ value: Int) { self = .int(Int64(value)) }
    init(_ value: Int64) { self = .int(value) }
    init(_ value: Bool) { self = .int(value ? 1 : 0) }
}

/// One result row, read by column name.
struct SQLRow {
    fileprivate let values: [String: SQLValue]

    func int(_ column: String) -> Int { Int(int64(column)) }

    func int64(_ column: String) -> Int64 {
        switch values[column] {
        case .int(let value): value
        case .double(let value): Int64(value)
        case .text(let text): Int64(text) ?? 0
        default: 0
        }
    }

    func bool(_ column: String) -> Bool { int64(column) != 0 }

    func string(_ column: String) -> String { optionalString(column) ?? "" }

    func optionalString(_ column: String) -> String? {
        switch values[column] {
        case .text(let text): text
        case .int(let value): String(value)
        case .double(let value): String(value)
        default: nil
        }
    }
}

/**
 * A thin wrapper over the SQLite C API. Not thread-safe on its own: `PracticeHistory` uses it
 * from one serial queue.
 */
final class SQLiteDatabase {
    private var handle: OpaquePointer?

    /// SQLite copies bound text, so callers' strings need not outlive the statement.
    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    init(path: String) throws {
        if sqlite3_open_v2(path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil) != SQLITE_OK {
            let message = handle.map { String(cString: sqlite3_errmsg($0)) } ?? "unknown error"
            sqlite3_close(handle)
            throw SQLiteError.open(message)
        }
    }

    /// An in-memory database, for tests.
    static func inMemory() throws -> SQLiteDatabase { try SQLiteDatabase(path: ":memory:") }

    deinit {
        sqlite3_close(handle)
    }

    var userVersion: Int {
        get { (try? query("PRAGMA user_version").first?.int("user_version")) ?? 0 }
        set { try? execute("PRAGMA user_version = \(newValue)") }
    }

    /// Runs one or more statements without parameters or results.
    func execute(_ sql: String) throws {
        var error: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(handle, sql, nil, nil, &error) != SQLITE_OK {
            let message = error.map { String(cString: $0) } ?? lastError
            sqlite3_free(error)
            throw SQLiteError.step(message)
        }
    }

    /// Runs a statement that returns no rows. Parameters bind to `?1`, `?2`, ... in order.
    func run(_ sql: String, _ arguments: [SQLValue] = []) throws {
        try withStatement(sql, arguments) { statement in
            let result = sqlite3_step(statement)
            if result != SQLITE_DONE && result != SQLITE_ROW { throw SQLiteError.step(lastError) }
        }
    }

    func query(_ sql: String, _ arguments: [SQLValue] = []) throws -> [SQLRow] {
        try withStatement(sql, arguments) { statement in
            var rows: [SQLRow] = []
            while true {
                let result = sqlite3_step(statement)
                if result == SQLITE_DONE { break }
                guard result == SQLITE_ROW else { throw SQLiteError.step(lastError) }
                var values: [String: SQLValue] = [:]
                for column in 0..<sqlite3_column_count(statement) {
                    let name = String(cString: sqlite3_column_name(statement, column))
                    switch sqlite3_column_type(statement, column) {
                    case SQLITE_INTEGER: values[name] = .int(sqlite3_column_int64(statement, column))
                    case SQLITE_FLOAT: values[name] = .double(sqlite3_column_double(statement, column))
                    case SQLITE_TEXT: values[name] = .text(String(cString: sqlite3_column_text(statement, column)))
                    default: values[name] = .null
                    }
                }
                rows.append(SQLRow(values: values))
            }
            return rows
        }
    }

    /// Runs [body] in a transaction, rolling back if it throws.
    func transaction<T>(_ body: () throws -> T) throws -> T {
        try execute("BEGIN IMMEDIATE")
        do {
            let result = try body()
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    private var lastError: String { String(cString: sqlite3_errmsg(handle)) }

    private func withStatement<T>(_ sql: String, _ arguments: [SQLValue], _ body: (OpaquePointer) throws -> T) throws -> T {
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(handle, sql, -1, &statement, nil) == SQLITE_OK, let statement else {
            throw SQLiteError.prepare(lastError, sql: sql)
        }
        defer { sqlite3_finalize(statement) }
        for (offset, argument) in arguments.enumerated() {
            let index = Int32(offset + 1)
            switch argument {
            case .int(let value): sqlite3_bind_int64(statement, index, value)
            case .double(let value): sqlite3_bind_double(statement, index, value)
            case .text(let text): sqlite3_bind_text(statement, index, text, -1, Self.transient)
            case .null: sqlite3_bind_null(statement, index)
            }
        }
        return try body(statement)
    }
}
