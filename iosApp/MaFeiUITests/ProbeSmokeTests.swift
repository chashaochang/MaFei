import XCTest

final class ProbeSmokeTests: XCTestCase {
    private func target(_ label: String, in app: XCUIApplication) -> XCUIElement {
        let exact = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        if exact.waitForExistence(timeout: 3) { return exact }
        // Compose may merge text into the containing accessible button.
        return app.buttons.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
    }

    private func tap(_ label: String, in app: XCUIApplication) {
        let button = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
        let element = button.exists && button.isHittable ? button : target(label, in: app)
        XCTAssertTrue(element.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(element.isHittable, "Target is not hittable: \(label)\n\(app.debugDescription)")
        let bounds = element.frame // Actual accessibility frame; no screenshot coordinates.
        XCTAssertGreaterThan(bounds.width, 0)
        XCTAssertGreaterThan(bounds.height, 0)
        print("PROBE TAP \(label) bounds=\(bounds)")
        element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    func testResourcesMaterialsAndWindowLifecycle() {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        defer { XCUIDevice.shared.orientation = .portrait }
        let app = XCUIApplication()
        app.launch()
        tap("实验室", in: app)
        XCTAssertTrue(target("Compose 资源已加载", in: app).waitForExistence(timeout: 15), app.debugDescription)
        tap("液态玻璃 · 收起说明", in: app)
        XCTAssertTrue(target("液态玻璃 · 展开说明", in: app).exists)
        tap("液态玻璃 · 展开说明", in: app)
        for (index, material) in ["完整玻璃", "简化玻璃", "实体背景"].enumerated() {
            tap(material, in: app)
            tap("命中 · \(index)", in: app)
            XCTAssertTrue(target("命中 · \(index + 1)", in: app).exists)
        }
        tap("检查弹层", in: app)
        XCTAssertTrue(target("弹层可读性检查", in: app).exists)
        tap("关闭", in: app)
        XCTAssertTrue(target("命中 · 3", in: app).exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        let landscapeReady = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.frame.width > app.frame.height
        }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [landscapeReady], timeout: 10), .completed)
        XCTAssertTrue(target("命中 · 3", in: app).waitForExistence(timeout: 10))
        // Reading semantics alone can see the old tree during UIKit rotation.
        // Wait for the rotation animation, then prove the new hit region works.
        Thread.sleep(forTimeInterval: 1)
        tap("命中 · 3", in: app)
        XCTAssertTrue(target("命中 · 4", in: app).exists)
        let landscape = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        landscape.name = "Probe landscape"
        landscape.lifetime = .keepAlways
        add(landscape)
        XCUIDevice.shared.orientation = .portrait
        let portraitReady = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.frame.height > app.frame.width
        }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [portraitReady], timeout: 10), .completed)
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(target("命中 · 4", in: app).waitForExistence(timeout: 10))
        let portrait = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        portrait.name = "Probe foreground portrait"
        portrait.lifetime = .keepAlways
        add(portrait)
    }
}
