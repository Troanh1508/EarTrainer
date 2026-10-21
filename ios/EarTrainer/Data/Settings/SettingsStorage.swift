import Foundation

extension Notification.Name {
    /// Posted on the main queue whenever any stored setting changes.
    static let settingsDidChange = Notification.Name("EarTrainer.settingsDidChange")
}

/// One stored setting value, of one of the types the Android app's DataStore holds.
enum PreferenceValue: Hashable {
    case string(String)
    case int(Int)
    case bool(Bool)
    case stringSet(Set<String>)
}

/// Every stored setting, keyed by the same names as the Android app's DataStore, e.g. "rhythm.bars".
typealias Preferences = [String: PreferenceValue]

extension Dictionary where Key == String, Value == PreferenceValue {
    func string(_ key: String) -> String? {
        if case .string(let value) = self[key] { return value }
        return nil
    }

    func int(_ key: String) -> Int? {
        if case .int(let value) = self[key] { return value }
        return nil
    }

    func bool(_ key: String) -> Bool? {
        if case .bool(let value) = self[key] { return value }
        return nil
    }

    func stringSet(_ key: String) -> Set<String>? {
        if case .stringSet(let value) = self[key] { return value }
        return nil
    }

    /// The enum value stored under [key], or nil if missing or no longer a known name.
    func enumValue<E: StoredEnum>(_ key: String) -> E? { string(key).flatMap(E.named) }

    /// The known enum values stored as a set under [key], or nil if missing.
    func enumSet<E: StoredEnum>(_ key: String) -> [E]? { stringSet(key).map { names in E.allCases.filter { names.contains($0.name) } } }
}

/// All settings in one place, like the Android app's single Preferences DataStore.
final class SettingsStorage {
    private let defaults: UserDefaults
    private static let key = "settings"
    private(set) var preferences: Preferences

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // An unreadable value should not block practice; every setting then falls back to its default.
        if let data = defaults.data(forKey: Self.key), let stored = try? JSONDecoder().decode([String: SettingValue].self, from: data) {
            preferences = stored.compactMapValues(\.preferenceValue)
        } else {
            preferences = [:]
        }
    }

    /// Changes settings in place, saving and announcing the change if anything differs.
    func edit(_ body: (inout Preferences) -> Void) {
        var updated = preferences
        body(&updated)
        guard updated != preferences else { return }
        preferences = updated
        if let data = try? JSONEncoder().encode(updated.mapValues(SettingValue.init)) {
            defaults.set(data, forKey: Self.key)
        }
        NotificationCenter.default.post(name: .settingsDidChange, object: self)
    }
}

/// One group of settings, decoded from and encoded into the shared storage.
final class SettingsStore<T: Equatable> {
    private let storage: SettingsStorage
    private let decode: (Preferences) -> T
    private let encode: (T, inout Preferences) -> Void

    init(storage: SettingsStorage, decode: @escaping (Preferences) -> T, encode: @escaping (T, inout Preferences) -> Void) {
        self.storage = storage
        self.decode = decode
        self.encode = encode
    }

    var current: T { decode(storage.preferences) }

    func update(_ transform: (T) -> T) {
        storage.edit { preferences in encode(transform(decode(preferences)), &preferences) }
    }
}
