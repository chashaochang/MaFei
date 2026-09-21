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
        let composeController = MainViewControllerKt.MainViewController { [weak orientationController] visible in
            orientationController?.setPlayerScreenVisible(visible.boolValue)
        }
        orientationController.install(composeController)
        window.rootViewController = orientationController
        self.window = window
        window.makeKeyAndVisible()
    }
}

final class OrientationViewController: UIViewController {
    private var playerScreenVisible = false

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        guard UIDevice.current.userInterfaceIdiom == .phone else { return .all }
        return playerScreenVisible ? .allButUpsideDown : .portrait
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
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
            guard !visible,
                  UIDevice.current.userInterfaceIdiom == .phone,
                  let windowScene = view.window?.windowScene else { return }
            windowScene.requestGeometryUpdate(
                UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: .portrait)
            )
        } else {
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}
