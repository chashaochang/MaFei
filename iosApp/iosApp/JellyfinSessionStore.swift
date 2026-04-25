import Foundation
import SwiftUI

struct JellyfinSession: Codable, Equatable {
    let baseURL: String
    let accessToken: String
    let userId: String
    let username: String
    let serverId: String?
    let savedAt: Date
}

enum BridgeSyncResult: Equatable {
    case upToDate
    case repaired
    case clearedUnauthenticated
}

enum SecureAccessTokenSource: String, Equatable {
    case inMemorySession
    case keychain
    case unavailable
}

enum SecureAuthRuntimeIssue: String, Equatable {
    case healthy
    case missingSecureToken
    case rememberSemanticsInvalid
    case bridgeContextInconsistent
}

struct SecureAuthRuntimeSnapshot: Equatable {
    let checkedAt: Date
    let rememberSession: Bool
    let hasPersistedSessionMetadata: Bool
    let hasPersistedSecureToken: Bool
    let isRememberSessionSemanticsValid: Bool
    let bridgeAuthState: KMPBridgeAuthState
    let hasSecureAccessToken: Bool
    let tokenSource: SecureAccessTokenSource
    let isBridgeConsistentWithActiveSession: Bool
    let issue: SecureAuthRuntimeIssue
    let issueDetail: String
    let recommendedAction: String
}

final class JellyfinSessionStore: ObservableObject {
    @Published private(set) var session: JellyfinSession?
    @Published private(set) var rememberSession = false

    private static let legacySessionKey = "cn.xiaobai.mafei.ios.jellyfin.session"
    private static let sessionMetadataKey = "cn.xiaobai.mafei.ios.jellyfin.session.metadata"

    private let defaults: UserDefaults
    private let tokenStore: KeychainTokenStore
    private let bridgeStore: KMPBridgeContextStore

    init(
        defaults: UserDefaults = .standard,
        tokenStore: KeychainTokenStore = KeychainTokenStore(),
        bridgeStore: KMPBridgeContextStore = KMPBridgeContextStore()
    ) {
        self.defaults = defaults
        self.tokenStore = tokenStore
        self.bridgeStore = bridgeStore
        load()
    }

    func load() {
        let metadata = readMetadata()
        let token = tokenStore.readToken()

        if let metadata, let token, !token.isEmpty {
            let restored = JellyfinSession(
                baseURL: metadata.baseURL,
                accessToken: token,
                userId: metadata.userId,
                username: metadata.username,
                serverId: metadata.serverId,
                savedAt: metadata.savedAt
            )
            bridgeStore.sync(session: restored)
            session = restored
            rememberSession = true
            return
        }

        if metadata != nil || token != nil {
            clearPersistedSessionData()
            bridgeStore.clear()
        }

        if let legacyData = defaults.data(forKey: Self.legacySessionKey),
           let legacySession = decodeLegacySession(legacyData)
        {
            save(legacySession)
            defaults.removeObject(forKey: Self.legacySessionKey)
            session = legacySession
            rememberSession = true
            return
        }

        session = nil
        rememberSession = false
        bridgeStore.clear()
    }

    func activate(_ value: JellyfinSession, persist: Bool) {
        if persist {
            save(value)
            return
        }

        clearPersistedSessionData()
        bridgeStore.sync(session: value)
        session = value
        rememberSession = false
    }

    func save(_ value: JellyfinSession) {
        guard !value.accessToken.isEmpty else {
            return
        }

        do {
            try tokenStore.save(token: value.accessToken)
        } catch {
            return
        }

        let metadata = SessionMetadata(
            baseURL: value.baseURL,
            userId: value.userId,
            username: value.username,
            serverId: value.serverId,
            savedAt: value.savedAt
        )

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(metadata) {
            defaults.set(data, forKey: Self.sessionMetadataKey)
        }

        defaults.removeObject(forKey: Self.legacySessionKey)
        bridgeStore.sync(session: value)
        session = value
        rememberSession = true
    }

    func clear() {
        clearPersistedSessionData()
        bridgeStore.clear()
        session = nil
        rememberSession = false
    }

    @discardableResult
    func ensureBridgeContext() -> BridgeSyncResult {
        guard let session else {
            bridgeStore.clear()
            return .clearedUnauthenticated
        }

        if bridgeStore.isAuthenticatedContextValid(for: session) {
            return .upToDate
        }

        bridgeStore.sync(session: session)
        return .repaired
    }

    func repairBridgeFromActiveSession(_ value: JellyfinSession) {
        session = value
        bridgeStore.sync(session: value)
    }

    func bridgeContextSnapshot() -> KMPBridgeContext? {
        bridgeStore.load()
    }

    @discardableResult
    func recoverSessionFromSecureStore() -> JellyfinSession? {
        guard
            let metadata = readMetadata(),
            let token = tokenStore.readToken()?.trimmingCharacters(in: .whitespacesAndNewlines),
            !token.isEmpty
        else {
            return nil
        }

        let restored = JellyfinSession(
            baseURL: metadata.baseURL,
            accessToken: token,
            userId: metadata.userId,
            username: metadata.username,
            serverId: metadata.serverId,
            savedAt: metadata.savedAt
        )
        session = restored
        rememberSession = true
        bridgeStore.sync(session: restored)
        return restored
    }

    func withSecureAccessToken<T>(_ operation: (String) throws -> T) rethrows -> T? {
        guard let resolvedToken = resolveSecureAccessToken() else {
            return nil
        }
        return try operation(resolvedToken.value)
    }

    func secureAuthRuntimeSnapshot() -> SecureAuthRuntimeSnapshot {
        let bridgeContext = bridgeStore.load()
        let currentBridgeAuthState = bridgeContext?.authState ?? .unauthenticated
        let persistedMetadata = readMetadata()
        let activeSession = session
        let persistedToken = tokenStore.readToken()?.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasPersistedToken = persistedToken?.isEmpty == false
        let isRememberValid = rememberSession
            ? (persistedMetadata != nil && hasPersistedToken)
            : (persistedMetadata == nil && !hasPersistedToken)
        let tokenState = resolveSecureAccessToken()

        let isBridgeConsistent: Bool
        if let activeSession {
            isBridgeConsistent = bridgeStore.isAuthenticatedContextValid(for: activeSession)
        } else {
            isBridgeConsistent = currentBridgeAuthState == .unauthenticated
        }

        let issue: SecureAuthRuntimeIssue
        let issueDetail: String
        let recommendedAction: String

        if !isRememberValid {
            issue = .rememberSemanticsInvalid
            if rememberSession {
                issueDetail = "Remember session is enabled but persisted metadata/token is incomplete."
                recommendedAction = "Repair remembered session data or sign in again."
            } else {
                issueDetail = "Remember session is disabled but persisted auth remnants still exist."
                recommendedAction = "Clear persisted remnants and keep transient session only."
            }
        } else if tokenState == nil {
            issue = .missingSecureToken
            if rememberSession {
                issueDetail = "No secure token is available for a remembered session."
                recommendedAction = "Try secure-store recovery, otherwise sign in again."
            } else {
                issueDetail = "Transient session is missing its in-memory secure token."
                recommendedAction = "Sign in again."
            }
        } else if !isBridgeConsistent {
            issue = .bridgeContextInconsistent
            if activeSession == nil {
                issueDetail = "Bridge context is authenticated while no active session exists."
            } else {
                issueDetail = "Bridge context does not match the active session."
            }
            recommendedAction = "Refresh or repair bridge context."
        } else {
            issue = .healthy
            issueDetail = "Secure token and bridge context are consistent."
            recommendedAction = "No action needed."
        }

        return SecureAuthRuntimeSnapshot(
            checkedAt: Date(),
            rememberSession: rememberSession,
            hasPersistedSessionMetadata: persistedMetadata != nil,
            hasPersistedSecureToken: hasPersistedToken,
            isRememberSessionSemanticsValid: isRememberValid,
            bridgeAuthState: currentBridgeAuthState,
            hasSecureAccessToken: tokenState != nil,
            tokenSource: tokenState?.source ?? .unavailable,
            isBridgeConsistentWithActiveSession: isBridgeConsistent,
            issue: issue,
            issueDetail: issueDetail,
            recommendedAction: recommendedAction
        )
    }

    private func readMetadata() -> SessionMetadata? {
        guard let data = defaults.data(forKey: Self.sessionMetadataKey) else {
            return nil
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(SessionMetadata.self, from: data)
    }

    private func decodeLegacySession(_ data: Data) -> JellyfinSession? {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try? decoder.decode(JellyfinSession.self, from: data)
    }

    private func clearPersistedSessionData() {
        defaults.removeObject(forKey: Self.legacySessionKey)
        defaults.removeObject(forKey: Self.sessionMetadataKey)
        try? tokenStore.clear()
    }

    private func resolveSecureAccessToken() -> (value: String, source: SecureAccessTokenSource)? {
        guard let activeSession = session else {
            return nil
        }

        let activeToken = activeSession.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        if !activeToken.isEmpty {
            return (activeToken, .inMemorySession)
        }

        if rememberSession,
           let keychainToken = tokenStore.readToken()?.trimmingCharacters(in: .whitespacesAndNewlines),
           !keychainToken.isEmpty
        {
            return (keychainToken, .keychain)
        }

        return nil
    }
}

private struct SessionMetadata: Codable, Equatable {
    let baseURL: String
    let userId: String
    let username: String
    let serverId: String?
    let savedAt: Date
}
