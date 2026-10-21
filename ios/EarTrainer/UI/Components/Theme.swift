import UIKit

/**
 * The app's colors: the Android app's calm indigo with a teal for "correct" and an orange for
 * weak spots, over the system's grouped backgrounds so screens feel at home on iOS.
 */
enum Palette {
    static let primary = UIColor(light: 0x4355B9, dark: 0xBAC3FF)
    static let onPrimary = UIColor(light: 0xFFFFFF, dark: 0x08218A)
    static let primaryContainer = UIColor(light: 0xDEE0FF, dark: 0x293CA0)
    static let onPrimaryContainer = UIColor(light: 0x00105C, dark: 0xDEE0FF)

    /// "Correct" in exercises.
    static let tertiary = UIColor(light: 0x006B5E, dark: 0x82D5C3)
    static let tertiaryContainer = UIColor(light: 0x9EF2DF, dark: 0x005046)
    static let onTertiaryContainer = UIColor(light: 0x00201B, dark: 0x9EF2DF)

    static let error = UIColor(light: 0xB3261E, dark: 0xF2B8B5)
    static let errorContainer = UIColor(light: 0xF9DEDC, dark: 0x8C1D18)
    static let onErrorContainer = UIColor(light: 0x410E0B, dark: 0xF9DEDC)

    /**
     * Marks weak spots in charts. Orange pairs with the indigo primary without relying on hue alone:
     * in the dark theme it is clearly darker than the primary, and both read as text on the surface.
     */
    static let weakSpot = UIColor(light: 0xB45309, dark: 0xE8832F)
    static let onWeakSpot = UIColor(light: 0xFFFFFF, dark: 0x2B1400)
    static let weakSpotContainer = UIColor(light: 0xFFF3E8, dark: 0x3B2414)
    static let onWeakSpotContainer = UIColor(light: 0x4A2A0C, dark: 0xFFDCC4)

    static let background = UIColor.systemGroupedBackground
    static let card = UIColor.secondarySystemGroupedBackground
    /// Unfilled parts of rings and bars.
    static let track = UIColor.tertiarySystemFill
    static let text = UIColor.label
    static let secondaryText = UIColor.secondaryLabel
    static let outline = UIColor.separator
}

extension UIColor {
    convenience init(light: UInt32, dark: UInt32) {
        self.init { traits in UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light) }
    }

    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

extension UIFont {
    /// The system font at a Dynamic Type style, in [weight].
    static func app(_ style: UIFont.TextStyle, weight: UIFont.Weight = .regular) -> UIFont {
        let size = UIFont.preferredFont(forTextStyle: style, compatibleWith: UITraitCollection(preferredContentSizeCategory: .large)).pointSize
        return UIFontMetrics(forTextStyle: style).scaledFont(for: .systemFont(ofSize: size, weight: weight))
    }

    /// Like `app`, with digits of equal width so counts do not shift as they change.
    static func appMonospacedDigits(_ style: UIFont.TextStyle, weight: UIFont.Weight = .regular) -> UIFont {
        let size = UIFont.preferredFont(forTextStyle: style, compatibleWith: UITraitCollection(preferredContentSizeCategory: .large)).pointSize
        return UIFontMetrics(forTextStyle: style).scaledFont(for: .monospacedDigitSystemFont(ofSize: size, weight: weight))
    }
}
