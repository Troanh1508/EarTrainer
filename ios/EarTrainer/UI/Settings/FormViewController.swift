import UIKit

struct FormSection {
    var header: String?
    var footer: String?
    var rows: [FormRow]
}

enum FormRow {
    /// A choice, checked when selected; tapping it calls [action].
    case option(title: String, selected: Bool, action: () -> Void)
    /// A labeled switch; the whole row toggles it, which gives a large touch target.
    case toggle(title: String, subtitle: String?, isOn: Bool, enabled: Bool, action: (Bool) -> Void)
    case button(title: String, enabled: Bool, action: () -> Void)
}

/// A settings-style list built from sections; subclasses rebuild them whenever settings change.
class FormViewController: UITableViewController {
    private var sections: [FormSection] = []
    private var observer: NSObjectProtocol?

    init(title: String) {
        super.init(style: .insetGrouped)
        self.title = title
        navigationItem.largeTitleDisplayMode = .never
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    /// The note shown above the first section, if any.
    var headerNote: String? { nil }

    func buildSections() -> [FormSection] { [] }

    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        if let note = headerNote {
            let label = UILabel(note, style: .subheadline, color: Palette.secondaryText)
            let header = UIView.padded(label, UIEdgeInsets(top: 8, left: 20, bottom: 4, right: 20))
            header.frame.size = header.systemLayoutSizeFitting(CGSize(width: view.bounds.width, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
            tableView.tableHeaderView = header
        }
        observer = NotificationCenter.default.addObserver(forName: .settingsDidChange, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.reload() }
        }
        reload()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Size the header note for the current width, so it wraps instead of being cut off.
        guard let header = tableView.tableHeaderView else { return }
        let size = header.systemLayoutSizeFitting(CGSize(width: tableView.bounds.width, height: 0), withHorizontalFittingPriority: .required, verticalFittingPriority: .fittingSizeLevel)
        if header.frame.height != size.height {
            header.frame.size.height = size.height
            tableView.tableHeaderView = header
        }
    }

    func reload() {
        sections = buildSections()
        tableView.reloadData()
    }

    override func numberOfSections(in tableView: UITableView) -> Int { sections.count }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { sections[section].rows.count }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { sections[section].header }

    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { sections[section].footer }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
        var content = UIListContentConfiguration.subtitleCell()
        cell.accessoryView = nil
        cell.accessoryType = .none
        cell.selectionStyle = .default
        switch sections[indexPath.section].rows[indexPath.row] {
        case .option(let title, let selected, _):
            content.text = title
            cell.accessoryType = selected ? .checkmark : .none
            cell.accessibilityTraits = selected ? [.button, .selected] : .button
        case .toggle(let title, let subtitle, let isOn, let enabled, let action):
            content.text = title
            content.secondaryText = subtitle
            content.secondaryTextProperties.color = Palette.secondaryText
            content.textToSecondaryTextVerticalPadding = 4
            let toggle = UISwitch()
            toggle.isOn = isOn
            toggle.isEnabled = enabled
            toggle.addAction(UIAction { [weak toggle] _ in action(toggle?.isOn ?? isOn) }, for: .valueChanged)
            cell.accessoryView = toggle
            cell.selectionStyle = enabled ? .default : .none
            content.textProperties.color = enabled ? Palette.text : .tertiaryLabel
        case .button(let title, let enabled, _):
            content.text = title
            content.textProperties.color = enabled ? Palette.primary : .tertiaryLabel
            cell.selectionStyle = enabled ? .default : .none
            cell.accessibilityTraits = enabled ? .button : [.button, .notEnabled]
        }
        cell.contentConfiguration = content
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        switch sections[indexPath.section].rows[indexPath.row] {
        case .option(_, _, let action):
            action()
        case .toggle(_, _, let isOn, let enabled, let action):
            if enabled { action(!isOn) }
        case .button(_, let enabled, let action):
            if enabled { action() }
        }
    }
}

/// Settings for one exercise, saved as soon as they change; changing them starts a new session.
class ExerciseSettingsViewController<S: Equatable>: FormViewController {
    let store: SettingsStore<S>
    private let defaults: S

    init(store: SettingsStore<S>, defaults: S) {
        self.store = store
        self.defaults = defaults
        super.init(title: Strings.settingsTitle)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override var headerNote: String? { Strings.newSessionNote }

    /// The sections particular to this exercise.
    func exerciseSections(_ settings: S) -> [FormSection] { [] }

    /// Whether to ask more often about recent mistakes.
    func focus(_ settings: S) -> Bool { false }

    func withFocus(_ settings: S, _ focus: Bool) -> S { settings }

    override func buildSections() -> [FormSection] {
        let settings = store.current
        let focusSection = FormSection(header: nil, footer: Strings.focusDescription, rows: [
            .toggle(title: Strings.focusTitle, subtitle: nil, isOn: focus(settings), enabled: true) { [weak self] isOn in
                self?.update { self?.withFocus($0, isOn) ?? $0 }
            },
        ])
        let restore = FormSection(header: nil, footer: nil, rows: [
            .button(title: Strings.restoreDefaults, enabled: settings != defaults) { [weak self] in
                guard let self else { return }
                self.update { _ in self.defaults }
            },
        ])
        return exerciseSections(settings) + [focusSection, restore, instrumentSection()]
    }

    func update(_ transform: (S) -> S) { store.update(transform) }

    /// The app-wide instrument choice, shown on every exercise's settings screen.
    private func instrumentSection() -> FormSection {
        let sound = AppContainer.shared.soundSettings
        let selected = sound.current.instrument
        return FormSection(header: Strings.instrumentTitle, footer: Strings.instrumentDescription, rows: Instrument.allCases.map { instrument in
            .option(title: instrument.displayName, selected: instrument == selected) { sound.update { $0.instrument == instrument ? $0 : SoundSettings(instrument: instrument) } }
        })
    }

    /// One checked option per value; [select] makes it the setting.
    func options<V: Equatable>(_ values: [V], selected: (V) -> Bool, title: (V) -> String, select: @escaping (S, V) -> S) -> [FormRow] {
        values.map { value in
            .option(title: title(value), selected: selected(value)) { [weak self] in self?.update { select($0, value) } }
        }
    }
}
