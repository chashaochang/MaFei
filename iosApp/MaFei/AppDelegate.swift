import UIKit
import MaFeiShared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "MaFei", sessionRole: session.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

// One Compose controller per scene. Swift owns only the platform window;
// all page layout and safe-area consumption remain in shared Compose.
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        let orientationController = OrientationViewController()
        let composeController = MainViewControllerKt.MainViewController(
            onPlayerScreenChanged: { [weak orientationController] visible in
                orientationController?.setPlayerScreenVisible(visible.boolValue)
            },
            onPlayerFullscreenChanged: { [weak orientationController] fullscreen in
                orientationController?.setPlayerFullscreenRequested(fullscreen.boolValue)
            }
        )
        orientationController.install(composeController)
        window.rootViewController = orientationController
        self.window = window
        window.makeKeyAndVisible()
    }
}

final class OrientationViewController: UIViewController {
    private var playerScreenVisible = false
    private var playerFullscreenRequested = false

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        switch UIDevice.current.userInterfaceIdiom {
        case .phone:
            return playerScreenVisible ? .allButUpsideDown : .portrait
        case .pad:
            // iPad keeps all orientations outside the player. Inside the
            // player, the shared tablet control explicitly selects portrait
            // or landscape; do not leave the host unconstrained after the
            // Compose state has changed.
            return playerScreenVisible
                ? (playerFullscreenRequested ? .landscape : .portrait)
                : .all
        default:
            return .all
        }
    }

    override var shouldAutorotate: Bool { true }

    func install(_ controller: UIViewController) {
        addChild(controller)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(controller.view)
        controller.didMove(toParent: self)
    }

    func setPlayerScreenVisible(_ visible: Bool) {
        guard playerScreenVisible != visible else { return }
        playerScreenVisible = visible
        if !visible {
            playerFullscreenRequested = false
        } else if UIDevice.current.userInterfaceIdiom == .pad,
                  let orientation = view.window?.windowScene?.interfaceOrientation {
            // Keep the orientation that was already active when playback
            // opened. The tablet button changes it; opening the player does
            // not implicitly rotate the window.
            playerFullscreenRequested = orientation.isLandscape
        }
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            guard let windowScene = view.window?.windowScene else { return }
            if !visible && UIDevice.current.userInterfaceIdiom == .phone {
                windowScene.requestGeometryUpdate(
                    UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: .portrait)
                )
            } else if visible && UIDevice.current.userInterfaceIdiom == .pad {
                windowScene.requestGeometryUpdate(
                    UIWindowScene.GeometryPreferences.iOS(
                        interfaceOrientations: playerFullscreenRequested ? .landscape : .portrait
                    )
                )
            }
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }

    func setPlayerFullscreenRequested(_ fullscreen: Bool) {
        playerFullscreenRequested = fullscreen
        guard playerScreenVisible,
              UIDevice.current.userInterfaceIdiom == .phone ||
                  UIDevice.current.userInterfaceIdiom == .pad,
              let windowScene = view.window?.windowScene else { return }
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            windowScene.requestGeometryUpdate(
                UIWindowScene.GeometryPreferences.iOS(
                    interfaceOrientations: fullscreen ? .landscape : .portrait
                )
            )
        } else {
            UIDevice.current.setValue(
                (fullscreen ? UIInterfaceOrientation.landscapeRight : .portrait).rawValue,
                forKey: "orientation"
            )
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}
