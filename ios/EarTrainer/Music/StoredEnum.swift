import Foundation

/**
 * Enums whose raw value is the stable name stored in history, settings and backups. The names
 * match the Android app's enum constants, so data moves between the two apps unchanged.
 */
protocol StoredEnum: RawRepresentable, CaseIterable, Hashable where RawValue == String, AllCases == [Self] {}

extension StoredEnum {
    var name: String { rawValue }

    /// Position in declaration order, like a Kotlin enum's ordinal.
    var ordinal: Int { Self.allCases.firstIndex(of: self)! }

    static func named(_ name: String) -> Self? { Self(rawValue: name) }
}

/// Modulo that is never negative, like Kotlin's `Int.mod`.
func positiveMod(_ value: Int, _ divisor: Int) -> Int {
    let remainder = value % divisor
    return remainder >= 0 ? remainder : remainder + divisor
}

extension String {
    /// The string with its first character upper-cased, e.g. "two eighths" → "Two eighths".
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
