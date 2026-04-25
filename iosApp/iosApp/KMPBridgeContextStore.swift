import Foundation

enum KMPBridgeAuthState: String, Codable, Equatable {
    case unauthenticated
    case authenticated
}

struct KMPBridgeContext: Codable, Equatable {
    struct Session: Codable, Equatable {
        let baseURL: String
        let userId: String
        let username: String
        let serverId: String?
    }

    let schemaVersion: Int
    let authState: KMPBridgeAuthState
    let session: Session?
    let updatedAt: Date
    let source: String

    static let currentSchemaVersion = 2
    static let sourceName = "ios-swift-shell"

    static func sessionPayload(from session: JellyfinSession) -> Session {
        Session(
            baseURL: session.baseURL,
            userId: session.userId,
            username: session.username,
            serverId: session.serverId
        )
    }

    static func loggedIn(from session: JellyfinSession) -> KMPBridgeContext {
        KMPBridgeContext(
            schemaVersion: currentSchemaVersion,
            authState: .authenticated,
            session: sessionPayload(from: session),
            updatedAt: Date(),
            source: sourceName
        )
    }

    static func loggedOut() -> KMPBridgeContext {
        KMPBridgeContext(
            schemaVersion: currentSchemaVersion,
            authState: .unauthenticated,
            session: nil,
            updatedAt: Date(),
            source: sourceName
        )
    }
}

final class KMPBridgeContextStore {
    // Future KMP side should read this key from UserDefaults.
    static let bridgeContextKey = "cn.xiaobai.mafei.kmp.bridge.session"

    private let defaults: UserDefaults
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601
    }

    func sync(session: JellyfinSession) {
        write(KMPBridgeContext.loggedIn(from: session))
    }

    func load() -> KMPBridgeContext? {
        guard let data = defaults.data(forKey: Self.bridgeContextKey) else {
            return nil
        }

        if let context = try? decoder.decode(KMPBridgeContext.self, from: data) {
            return context
        }

        // Backward compatibility for phase-3 format: `isLoggedIn` boolean.
        if let legacy = try? decoder.decode(LegacyBridgeContextV1.self, from: data) {
            let migrated = KMPBridgeContext(
                schemaVersion: KMPBridgeContext.currentSchemaVersion,
                authState: legacy.isLoggedIn ? .authenticated : .unauthenticated,
                session: legacy.session,
                updatedAt: legacy.updatedAt,
                source: KMPBridgeContext.sourceName
            )
            write(migrated)
            return migrated
        }

        return nil
    }

    func clear() {
        write(.loggedOut())
    }

    func isAuthenticatedContextValid(for session: JellyfinSession) -> Bool {
        guard let context = load() else {
            return false
        }
        guard context.authState == .authenticated else {
            return false
        }
        return context.session == KMPBridgeContext.sessionPayload(from: session)
    }

    private func write(_ context: KMPBridgeContext) {
        guard let data = try? encoder.encode(context) else {
            return
        }
        defaults.set(data, forKey: Self.bridgeContextKey)
    }
}

private struct LegacyBridgeContextV1: Codable {
    let schemaVersion: Int
    let isLoggedIn: Bool
    let session: KMPBridgeContext.Session?
    let updatedAt: Date
}
