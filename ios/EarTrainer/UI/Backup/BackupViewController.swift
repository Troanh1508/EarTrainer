import UIKit
import UniformTypeIdentifiers

/// Export and import of history and settings as one JSON file, compatible with the Android app.
final class BackupViewController: ScrollingViewController, UIDocumentPickerDelegate {
    private enum Action { case export, `import` }

    private let backup = AppContainer.shared.backup
    private var working: Action?
    private var exportButton: UIButton!
    private var importButton: UIButton!
    /// The file being exported and how many answers it holds, until the picker reports back.
    private var pendingExport: (url: URL, answers: Int)?

    override func viewDidLoad() {
        super.viewDidLoad()
        title = Strings.backupTitle
        navigationItem.largeTitleDisplayMode = .never
        exportButton = actionButton(Strings.backupExportButton, filled: true, icon: "square.and.arrow.up") { [weak self] in self?.export() }
        importButton = actionButton(Strings.backupImportButton, filled: false, icon: "square.and.arrow.down") { [weak self] in self?.pickImport() }
        contentStack.addArrangedSubviews([
            section(Strings.backupAutoTitle, Strings.backupAutoDescription, nil),
            section(Strings.backupExportTitle, Strings.backupExportDescription, exportButton),
            section(Strings.backupImportTitle, Strings.backupImportDescription, importButton),
        ])
    }

    private func section(_ title: String, _ description: String, _ button: UIButton?) -> UIView {
        let card = CardView(spacing: 8)
        let heading = UILabel(title, style: .headline)
        heading.accessibilityTraits = .header
        card.stack.addArrangedSubviews([heading, UILabel(description, style: .subheadline, color: Palette.secondaryText)])
        if let button {
            card.stack.setCustomSpacing(14, after: card.stack.arrangedSubviews.last!)
            card.stack.addArrangedSubview(UIStackView.horizontal([button, .spacer()]))
        }
        return card
    }

    private func setWorking(_ action: Action?) {
        working = action
        exportButton.configuration?.showsActivityIndicator = action == .export
        importButton.configuration?.showsActivityIndicator = action == .import
        exportButton.isEnabled = action == nil
        importButton.isEnabled = action == nil
    }

    private func export() {
        guard working == nil else { return }
        setWorking(.export)
        Task {
            do {
                let (data, answers) = try await backup.export()
                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.dateFormat = "yyyy-MM-dd"
                let url = FileManager.default.temporaryDirectory.appendingPathComponent(Strings.backupFileName(formatter.string(from: Date())))
                try data.write(to: url, options: .atomic)
                pendingExport = (url, answers)
                let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
                picker.delegate = self
                present(picker, animated: true)
            } catch {
                setWorking(nil)
                show(Strings.exportFailed)
            }
        }
    }

    private func pickImport() {
        guard working == nil else { return }
        // File providers label JSON files inconsistently, so plain text and raw data are accepted too.
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data], asCopy: true)
        picker.delegate = self
        present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if let export = pendingExport {
            pendingExport = nil
            try? FileManager.default.removeItem(at: export.url)
            setWorking(nil)
            show(Strings.exported(export.answers))
            return
        }
        guard let url = urls.first else { return }
        setWorking(.import)
        Task {
            let message: String
            do {
                let data = try Data(contentsOf: url)
                let result = try await backup.importBackup(data)
                var parts = [Strings.imported(result.added)]
                if result.alreadyPresent > 0 { parts.append(Strings.importedAlreadyPresent(result.alreadyPresent)) }
                if result.unreadable > 0 { parts.append(Strings.importedUnreadable(result.unreadable)) }
                if result.settingsRestored { parts.append(Strings.importedSettings) }
                message = parts.joined(separator: " ")
            } catch BackupError.notABackup {
                message = Strings.notABackup
            } catch BackupError.newerVersion {
                message = Strings.newerVersion
            } catch BackupError.tooLarge {
                message = Strings.tooLarge
            } catch {
                message = Strings.importFailed
            }
            setWorking(nil)
            show(message)
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        if let export = pendingExport {
            try? FileManager.default.removeItem(at: export.url)
            pendingExport = nil
            setWorking(nil)
        }
    }

    private func show(_ message: String) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: Strings.ok, style: .default))
        present(alert, animated: true)
    }
}
