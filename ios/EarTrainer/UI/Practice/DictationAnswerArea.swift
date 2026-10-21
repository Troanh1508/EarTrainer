import UIKit

/**
 * The notes written so far in a dictation, before "Check". Slot 0 holds the given tonic and is
 * never edited; the others start empty. One editable slot is selected at a time: keypad input
 * goes there, and tapping a slot selects it so any note can be changed directly.
 */
struct DictationDraft: Equatable {
    let notes: [ScaleDegree?]
    let selected: Int

    init(notes: [ScaleDegree?], selected: Int) {
        precondition(notes.count >= 2 && notes[0] == .tonic, "A draft has the given tonic and at least one note to write")
        precondition((1..<notes.count).contains(selected), "Only written notes can be selected")
        self.notes = notes
        self.selected = selected
    }

    static func start(_ length: Int) -> DictationDraft {
        DictationDraft(notes: [.tonic] + Array(repeating: nil, count: length - 1), selected: 1)
    }

    var isComplete: Bool { notes.allSatisfy { $0 != nil } }

    /// Whether any note has been written, i.e. whether there is anything to clear.
    var hasWrittenNotes: Bool { notes.dropFirst().contains { $0 != nil } }

    /// Selects slot [index] for editing; the given tonic cannot be selected.
    func select(_ index: Int) -> DictationDraft {
        (1..<notes.count).contains(index) ? DictationDraft(notes: notes, selected: index) : self
    }

    /**
     * Writes [degree] into the selected slot, then moves on to the next empty slot (wrapping to
     * earlier gaps), so typing a melody in order needs no extra taps. If every slot is filled,
     * the selection stays put.
     */
    func enter(_ degree: ScaleDegree) -> DictationDraft {
        var updated = notes
        updated[selected] = degree
        let emptySlots = updated.indices.filter { $0 > 0 && updated[$0] == nil }
        let next = emptySlots.first { $0 > selected } ?? emptySlots.first ?? selected
        return DictationDraft(notes: updated, selected: next)
    }

    /// Empties every written slot and goes back to the first one.
    func clearAll() -> DictationDraft { .start(notes.count) }

    /// The finished melody, or nil while any slot is empty.
    func melody() -> Melody? { isComplete ? Melody(notes.map { $0! }) : nil }
}

/**
 * A `DictationDraft` with the earlier versions needed for Undo. Every change to the notes can be
 * undone, including "Clear all"; moving the selection is not a change, so Undo skips over it.
 */
struct DictationEntry {
    /// Far more than a six-note melody needs.
    static let maxUndoSteps = 50

    var draft: DictationDraft
    var history: [DictationDraft] = []

    static func start(_ length: Int) -> DictationEntry { DictationEntry(draft: .start(length)) }

    var canUndo: Bool { !history.isEmpty }

    /// Moves the cursor without adding an undo step.
    func select(_ index: Int) -> DictationEntry { DictationEntry(draft: draft.select(index), history: history) }

    func enter(_ degree: ScaleDegree) -> DictationEntry { edit(draft.enter(degree)) }

    func clearAll() -> DictationEntry { edit(draft.clearAll()) }

    /// Returns to the notes (and selection) before the last change.
    func undo() -> DictationEntry {
        guard let last = history.last else { return self }
        return DictationEntry(draft: last, history: Array(history.dropLast()))
    }

    private func edit(_ next: DictationDraft) -> DictationEntry {
        next == draft ? self : DictationEntry(draft: next, history: Array((history + [draft]).suffix(Self.maxUndoSteps)))
    }
}

/**
 * The note slots, keypad and editing buttons. What has been typed is a `DictationDraft` (with its
 * undo history) that only becomes an answer on "Check"; it lives here because it is pure input.
 */
final class DictationAnswerArea: AnswerArea<DictationQuestion> {
    /// Degrees are short, so four keypad buttons fit in a row.
    private static let keypadColumns = 4

    private let onSubmit: (Melody) -> Void
    private let slots = FlowLayoutView()
    private let keypadContainer = UIView()
    private var keypad: AnswerGridView<ScaleDegree>?
    private var undoButton: UIButton!
    private var clearButton: UIButton!
    private var checkButton: UIButton!
    private var entry = DictationEntry.start(4)
    private var questionKey: String?
    private var lastState: PracticeUiState<DictationQuestion>?

    init(onSubmit: @escaping (Melody) -> Void) {
        self.onSubmit = onSubmit
        super.init(frame: .zero)
        slots.centered = true
        undoButton = actionButton(Strings.undo, filled: false) { [weak self] in self?.change { $0.undo() } }
        clearButton = actionButton(Strings.clearAll, filled: false) { [weak self] in self?.change { $0.clearAll() } }
        checkButton = actionButton(Strings.check, filled: true) { [weak self] in
            guard let self, let melody = self.entry.draft.melody() else { return }
            self.onSubmit(melody)
        }
        checkButton.accessibilityIdentifier = "check"
        let buttons = UIStackView.horizontal([undoButton, clearButton, checkButton], spacing: 8, alignment: .fill, distribution: .fillEqually)
        let stack = UIStackView.vertical([slots, keypadContainer, buttons], spacing: 20)
        addSubview(stack)
        stack.pinEdges(to: self)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func update(_ state: PracticeUiState<DictationQuestion>) {
        lastState = state
        let key = "\(state.sessionId)#\(state.exercise.questionNumber)"
        if key != questionKey {
            // A fresh draft and keypad for every new question.
            questionKey = key
            entry = .start(state.question.melody.degrees.count)
            keypad?.removeFromSuperview()
            let keypad = AnswerGridView(
                choices: state.question.keypad, columns: Self.keypadColumns,
                label: { ChoiceLabel(title: $0.label, subtitle: $0.solfege) },
                onSelect: { [weak self] degree in
                    guard self?.lastState?.canAnswer == true else { return }
                    self?.change { $0.enter(degree) }
                }
            )
            keypadContainer.addSubview(keypad)
            keypad.pinEdges(to: keypadContainer)
            self.keypad = keypad
        }
        render()
    }

    private func change(_ transform: (DictationEntry) -> DictationEntry) {
        entry = transform(entry)
        render()
    }

    private func render() {
        guard let state = lastState else { return }
        let editable = state.canAnswer
        let answered = state.answer?.selectedAnswer
        keypad?.update { _ in editable ? .available : .disabled }
        undoButton.isEnabled = editable && entry.canUndo
        clearButton.isEnabled = editable && entry.draft.hasWrittenNotes
        checkButton.isEnabled = editable && entry.draft.isComplete

        let written = answered?.degrees.map { Optional($0) } ?? entry.draft.notes
        let correct = answered != nil ? state.question.melody.degrees : nil
        let selected = editable ? entry.draft.selected : nil
        slots.setViews(written.indices.map { index in
            let note = written[index]
            let expected = correct.flatMap { index < $0.count ? $0[index] : nil }
            let slot = NoteSlotView(note: note, index: index, expected: expected, isSelected: index == selected)
            if index > 0 && selected != nil {
                slot.addAction(UIAction { [weak self] _ in self?.change { $0.select(index) } }, for: .touchUpInside)
            } else {
                slot.isUserInteractionEnabled = false
            }
            return slot
        })
    }
}

/**
 * One box per note. The first is the given tonic. While answering, the selected box is outlined.
 * After checking, each box is marked right or wrong (by color and wording, never color alone),
 * with the right note under a wrong one.
 */
private final class NoteSlotView: UIControl {
    init(note: ScaleDegree?, index: Int, expected: ScaleDegree?, isSelected: Bool) {
        super.init(frame: .zero)
        let isRight = expected != nil && note == expected
        let background: UIColor
        let content: UIColor
        let border: UIColor
        if index == 0 {
            // Muted, so the given tonic never looks like the selected slot.
            (background, content, border) = (.tertiarySystemFill, Palette.secondaryText, Palette.outline)
        } else if isSelected {
            (background, content, border) = (Palette.primaryContainer, Palette.onPrimaryContainer, Palette.primary)
        } else if expected == nil {
            (background, content, border) = (Palette.card, Palette.text, note != nil ? Palette.secondaryText : Palette.outline)
        } else if isRight {
            (background, content, border) = (Palette.tertiaryContainer, Palette.onTertiaryContainer, Palette.tertiary)
        } else {
            (background, content, border) = (Palette.errorContainer, Palette.onErrorContainer, Palette.error)
        }
        let correction = (!isRight && index > 0) ? expected : nil
        backgroundColor = background
        layer.cornerRadius = 12
        layer.cornerCurve = .continuous
        layer.borderWidth = isSelected || note != nil ? 2 : 1
        layer.borderColor = border.resolvedColor(with: traitCollection).cgColor
        registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: NoteSlotView, _: UITraitCollection) in
            view.layer.borderColor = border.resolvedColor(with: view.traitCollection).cgColor
        }

        let title = UILabel(note?.label ?? "–", style: .title2, weight: .semibold, color: content, alignment: .center)
        if correction != nil {
            title.attributedText = NSAttributedString(string: note?.label ?? "–", attributes: [.strikethroughStyle: NSUnderlineStyle.single.rawValue])
        }
        let subtitle = UILabel(correction?.label ?? note?.solfege ?? " ", style: .caption1, color: content, alignment: .center)
        let stack = UIStackView.vertical([title, subtitle], spacing: 0, alignment: .center)
        stack.isUserInteractionEnabled = false
        addSubview(stack)
        stack.pinEdges(to: self, insets: UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12))
        widthAnchor.constraint(greaterThanOrEqualToConstant: 56).isActive = true
        heightAnchor.constraint(greaterThanOrEqualToConstant: 64).isActive = true

        isAccessibilityElement = true
        accessibilityTraits = index > 0 && expected == nil ? .button : .staticText
        if isSelected { accessibilityTraits.insert(.selected) }
        let label = note?.label ?? Strings.slotEmpty
        accessibilityLabel = if index == 0 {
            "\(label), \(Strings.slotGiven)"
        } else if expected == nil {
            isSelected ? "\(label), \(Strings.slotSelected)" : label
        } else if isRight {
            "\(label), \(Strings.choiceCorrect)"
        } else {
            Strings.slotWrong(label, expected!.label)
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
}
