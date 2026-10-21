import XCTest

/**
 * Walks through the app the way a listener would: a full interval session to its summary, then
 * rhythm, dictation, settings, progress, mistakes, a comparison drill, the log and backups.
 * Screenshots are attached to the results, and also saved to SCREENSHOT_DIR when it is set
 * (pass TEST_RUNNER_SCREENSHOT_DIR to xcodebuild).
 */
final class WalkthroughUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    private func shot(_ name: String) {
        let screenshot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let directory = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"] {
            try? screenshot.pngRepresentation.write(to: URL(fileURLWithPath: directory).appendingPathComponent("\(name).png"))
        }
    }

    private func open(_ exercise: String) {
        let card = app.descendants(matching: .any)["exercise.\(exercise)"]
        if !card.isHittable { app.swipeUp() }
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        card.tap()
    }

    private func enabledAnswers() -> [XCUIElement] {
        app.descendants(matching: .any).matching(identifier: "answer").allElementsBoundByIndex.filter { $0.isEnabled }
    }

    private func waitForEnabledAnswers() -> [XCUIElement] {
        let deadline = Date().addingTimeInterval(10)
        while Date() < deadline {
            let answers = enabledAnswers()
            if !answers.isEmpty { return answers }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTFail("No answer became available")
        return []
    }

    private func back() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    func testWalkthrough() {
        shot("01-home")

        // A full interval session, answering some right and some wrong.
        open("INTERVALS")
        shot("02-intervals-unheard")
        app.buttons["play"].tap()
        for question in 1...20 {
            let answers = waitForEnabledAnswers()
            if question == 1 { shot("03-intervals-playing") }
            answers[question % answers.count].tap()
            if question == 1 || question == 2 { shot("04-intervals-answered-\(question)") }
            let next = app.buttons["next"]
            XCTAssertTrue(next.waitForExistence(timeout: 5))
            next.tap()
        }
        XCTAssertTrue(app.staticTexts["Session complete"].waitForExistence(timeout: 5))
        sleep(1)
        shot("05-summary")
        app.swipeUp()
        shot("06-summary-scrolled")
        app.buttons["Done"].tap()
        sleep(1)
        shot("07-home-after-session")

        // Settings, from the practice screen.
        open("INTERVALS")
        app.buttons["Practice settings"].tap()
        sleep(1)
        shot("08-interval-settings")
        back()
        back()

        // Rhythm, with drawn notation.
        open("RHYTHM")
        app.buttons["play"].tap()
        let rhythmAnswers = waitForEnabledAnswers()
        shot("09-rhythm")
        rhythmAnswers[0].tap()
        sleep(1)
        shot("10-rhythm-answered")
        back()

        // Dictation: type the notes, then check.
        open("DICTATION")
        app.buttons["play"].tap()
        let keypad = waitForEnabledAnswers()
        keypad[1].tap()
        keypad[2].tap()
        shot("11-dictation-entering")
        keypad[3].tap()
        app.buttons["check"].tap()
        sleep(1)
        shot("12-dictation-checked")
        back()

        // Chords and progressions, answered once each.
        for exercise in ["CHORDS", "PROGRESSIONS", "SCALE_DEGREES"] {
            open(exercise)
            app.buttons["play"].tap()
            waitForEnabledAnswers()[0].tap()
            sleep(1)
            shot("13-\(exercise.lowercased())")
            back()
        }

        // Progress, then the mistakes behind the weakest item, then a drill.
        app.buttons["See progress"].tap()
        sleep(2)
        shot("14-progress")
        app.swipeUp()
        shot("15-progress-scrolled")
        let row = app.descendants(matching: .any)["accuracyRow"].firstMatch
        if !row.isHittable { app.swipeDown() }
        row.tap()
        sleep(2)
        shot("16-mistakes")
        let compare = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Compare'")).firstMatch
        if compare.waitForExistence(timeout: 3) {
            compare.tap()
            sleep(1)
            shot("17-compare")
            back()
        }
        back()

        // The practice log, from Progress.
        app.navigationBars.buttons["Practice Log"].tap()
        sleep(2)
        shot("18-log")
        back()
        back()

        // Backup and restore.
        app.navigationBars.buttons["Backup and restore"].tap()
        sleep(1)
        shot("19-backup")
    }
}
