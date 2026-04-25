import Foundation
import JellyfinAPI

enum MaFeiLogLevel: String {
    case debug = "DEBUG"
    case info = "INFO"
    case warning = "WARN"
    case error = "ERROR"
}

func maFeiLog(_ level: MaFeiLogLevel, tag: String, _ message: String) {
    NSLog("[MaFei][iOS][\(level.rawValue)][\(tag)] \(message)")
}

func redactBaseURL(_ value: String) -> String {
    guard var components = URLComponents(string: value.trimmingCharacters(in: .whitespacesAndNewlines)) else {
        return value
    }
    components.user = nil
    components.password = nil
    components.query = nil
    components.fragment = nil
    let scheme = components.scheme.map { "\($0)://" } ?? ""
    let host = components.host ?? value
    let port = components.port.map { ":\($0)" } ?? ""
    let path = components.percentEncodedPath == "/" ? "" : components.percentEncodedPath
    return scheme + host + port + path
}

func redactIdentifier(_ value: String?) -> String {
    let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !trimmed.isEmpty else {
        return "-"
    }
    guard trimmed.count > 8 else {
        return trimmed
    }
    return "\(trimmed.prefix(4))...\(trimmed.suffix(2))"
}

func redactUsername(_ value: String?) -> String {
    let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !trimmed.isEmpty else {
        return "-"
    }
    guard trimmed.count > 2 else {
        return String(trimmed.prefix(1)) + "*"
    }
    return String(trimmed.prefix(1)) + "***" + String(trimmed.suffix(1))
}

enum HomeBridgeSnapshotFreshness: String {
    case unavailable
    case fresh
    case stale
    case sessionMismatch
    case malformed
}

struct HomeBridgeSnapshotStatus {
    let freshness: HomeBridgeSnapshotFreshness
    let schemaVersion: Int?
    let baseURL: String?
    let userID: String?
    let username: String?
    let updatedAt: Date?
    let ageSeconds: Int?
    let continueCount: Int
    let nextUpCount: Int
    let updatesCount: Int
    let latestCount: Int

    var hasSnapshot: Bool {
        freshness != .unavailable
    }
}

final class KMPHomeBridgeStore {
    typealias RefreshCommit = (@escaping () -> Void) -> Bool

    static let metadataKey = "cn.xiaobai.mafei.kmp.bridge.home.meta"
    static let continueWatchingKey = "cn.xiaobai.mafei.kmp.bridge.home.continue"
    static let nextUpKey = "cn.xiaobai.mafei.kmp.bridge.home.nextup"
    static let updatesKey = "cn.xiaobai.mafei.kmp.bridge.home.updates"
    static let latestAddedKey = "cn.xiaobai.mafei.kmp.bridge.home.latest"
    static let detailIndexKey = "cn.xiaobai.mafei.kmp.bridge.detail.index"
    static let detailMetaKeyPrefix = "cn.xiaobai.mafei.kmp.bridge.detail.meta."
    static let detailEpisodesKeyPrefix = "cn.xiaobai.mafei.kmp.bridge.detail.episodes."
    static let detailRequestQueueKey = "cn.xiaobai.mafei.kmp.bridge.detail.requests"
    static let searchRequestKey = "cn.xiaobai.mafei.kmp.bridge.search.request"
    static let searchMetaKey = "cn.xiaobai.mafei.kmp.bridge.search.meta"
    static let searchItemsKey = "cn.xiaobai.mafei.kmp.bridge.search.items"
    static let mediaViewMetaKey = "cn.xiaobai.mafei.kmp.bridge.mediaView.meta"
    static let mediaViewViewsKey = "cn.xiaobai.mafei.kmp.bridge.mediaView.views"
    static let mediaViewItemsKeyPrefix = "cn.xiaobai.mafei.kmp.bridge.mediaView.items."
    static let mediaViewRequestKey = "cn.xiaobai.mafei.kmp.bridge.mediaView.request"
    static let favoritesRequestKey = "cn.xiaobai.mafei.kmp.bridge.favorites.request"
    static let favoritesMetaKey = "cn.xiaobai.mafei.kmp.bridge.favorites.meta"
    static let favoritesItemsKey = "cn.xiaobai.mafei.kmp.bridge.favorites.items"
    static let favoritesMutationRequestKey = "cn.xiaobai.mafei.kmp.bridge.favorites.mutation.request"
    static let favoritesMutationResultKey = "cn.xiaobai.mafei.kmp.bridge.favorites.mutation.result"
    static let playbackRequestKey = "cn.xiaobai.mafei.kmp.bridge.playback.request"
    static let playbackMetaKey = "cn.xiaobai.mafei.kmp.bridge.playback.meta"

    fileprivate static let fieldSeparator = "\u{001F}"
    fileprivate static let recordSeparator = "\u{001E}"
    private static let schemaVersion = 1
    private static let detailPrefetchLimit = 12
    private static let detailRequestTTLMillis: Int64 = 120_000
    private static let searchRequestTTLMillis: Int64 = 30_000
    private static let mediaViewRequestTTLMillis: Int64 = 30_000
    private static let favoritesRequestTTLMillis: Int64 = 30_000
    private static let favoritesMutationRequestTTLMillis: Int64 = 30_000
    private static let playbackRequestTTLMillis: Int64 = 45_000

    private let defaults: UserDefaults
    private let authService: JellyfinAuthService

    init(
        defaults: UserDefaults = .standard,
        authService: JellyfinAuthService = JellyfinAuthService()
    ) {
        self.defaults = defaults
        self.authService = authService
    }

    func refresh(
        for session: JellyfinSession,
        shouldContinue: @escaping () -> Bool = { true },
        commit: RefreshCommit? = nil
    ) async throws {
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "refresh start baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId))"
        )
        do {
            let client = try authService.makeAuthenticatedClient(for: session)
            try ensureRefreshCanContinue(shouldContinue)

            async let resumeResponse = client.send(
                Paths.getResumeItems(
                    parameters: .init(
                        userID: session.userId,
                        limit: 20,
                        enableUserData: true
                    )
                )
            )
            async let latestResponse = client.send(
                Paths.getLatestMedia(
                    parameters: .init(
                        userID: session.userId,
                        enableUserData: true,
                        limit: 20
                    )
                )
            )
            async let nextUpResponse = client.send(
                Paths.getNextUp(
                    parameters: .init(
                        userID: session.userId,
                        limit: 20,
                        enableUserData: true,
                        enableResumable: true,
                        enableRewatching: false
                    )
                )
            )
            async let updatesResponse = client.send(
                Paths.getItems(
                    parameters: .init(
                        userID: session.userId,
                        limit: 40,
                        isRecursive: true,
                        sortOrder: [.descending],
                        includeItemTypes: [.episode],
                        filters: [.isUnplayed],
                        sortBy: [.dateCreated],
                        enableUserData: true
                    )
                )
            )

            let continueWatching = try await resumeResponse.value.items?
                .compactMap { $0.toHomeContinueWatchingItem(session: session) } ?? []
            let latestAdded = try await latestResponse.value
                .compactMap { $0.toHomeLatestItem(session: session) }
            let nextUp = try await nextUpResponse.value.items?
                .compactMap { $0.toHomeNextUpItem(session: session) } ?? []
            let updates = try await updatesResponse.value.items?
                .compactMap { $0.toHomeUpdateSeed(session: session) } ?? []
            try ensureRefreshCanContinue(shouldContinue)

            let groupedUpdates = Dictionary(grouping: updates) { seed in
                seed.itemId
            }
                .compactMap { _, episodes -> HomeUpdateItem? in
                    guard let first = episodes.first else {
                        return nil
                    }
                    return HomeUpdateItem(
                        itemId: first.itemId,
                        title: first.title,
                        latestEpisodeTitle: first.latestEpisodeTitle,
                        newEpisodeCount: episodes.count,
                        thumbnailUrl: first.thumbnailUrl
                    )
                }
                .prefix(20)

            let metadata = encodeRecord([
                String(Self.schemaVersion),
                session.baseURL,
                session.userId,
                session.username,
                ISO8601DateFormatter().string(from: Date()),
            ])

            try commitRefreshWrite(commit) { [self] in
                self.defaults.set(metadata, forKey: Self.metadataKey)
                self.defaults.set(self.encodeRecords(continueWatching.map { $0.recordFields }), forKey: Self.continueWatchingKey)
                self.defaults.set(self.encodeRecords(nextUp.map { $0.recordFields }), forKey: Self.nextUpKey)
                self.defaults.set(self.encodeRecords(groupedUpdates.map { $0.recordFields }), forKey: Self.updatesKey)
                self.defaults.set(self.encodeRecords(latestAdded.map { $0.recordFields }), forKey: Self.latestAddedKey)
            }

            let detailPrefetchIDs = orderedUniqueItemIDs(
                from: continueWatching.map { $0.itemId },
                nextUp.map { $0.itemId },
                groupedUpdates.map { $0.itemId },
                latestAdded.map { $0.itemId }
            )
            try await refreshDetailSnapshots(
                client: client,
                session: session,
                itemIDs: detailPrefetchIDs,
                shouldContinue: shouldContinue,
                commit: commit
            )
            try? await refreshMediaViewSnapshots(
                client: client,
                session: session,
                shouldContinue: shouldContinue,
                commit: commit
            )
            maFeiLog(
                .info,
                tag: "KMPHomeBridgeStore",
                "refresh complete continue=\(continueWatching.count) nextUp=\(nextUp.count) latest=\(latestAdded.count) updates=\(groupedUpdates.count)"
            )
        } catch {
            let failureKind = classifyHomeBridgeRefreshFailure(error: error)
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "home bridge refresh failed reason=\(failureKind.rawValue) baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId)) preservingCachedSnapshot=1 errorType=\(String(describing: type(of: error)))"
            )
            throw HomeBridgeRefreshFailure(
                kind: failureKind,
                underlyingError: error
            )
        }
    }

    func clear() {
        maFeiLog(.info, tag: "KMPHomeBridgeStore", "clearing persisted bridge snapshots")
        defaults.removeObject(forKey: Self.metadataKey)
        defaults.removeObject(forKey: Self.continueWatchingKey)
        defaults.removeObject(forKey: Self.nextUpKey)
        defaults.removeObject(forKey: Self.updatesKey)
        defaults.removeObject(forKey: Self.latestAddedKey)
        defaults.removeObject(forKey: Self.searchRequestKey)
        defaults.removeObject(forKey: Self.searchMetaKey)
        defaults.removeObject(forKey: Self.searchItemsKey)
        clearMediaViewSnapshots()
        defaults.removeObject(forKey: Self.favoritesRequestKey)
        defaults.removeObject(forKey: Self.favoritesMetaKey)
        defaults.removeObject(forKey: Self.favoritesItemsKey)
        defaults.removeObject(forKey: Self.favoritesMutationRequestKey)
        defaults.removeObject(forKey: Self.favoritesMutationResultKey)
        defaults.removeObject(forKey: Self.playbackRequestKey)
        defaults.removeObject(forKey: Self.playbackMetaKey)
        clearDetailSnapshots()
    }

    func inspectHomeSnapshotStatus(
        for session: JellyfinSession?,
        staleAfterSeconds: TimeInterval = 5 * 60
    ) -> HomeBridgeSnapshotStatus {
        let continueCount = decodeRecords(defaults.string(forKey: Self.continueWatchingKey)).count
        let nextUpCount = decodeRecords(defaults.string(forKey: Self.nextUpKey)).count
        let updatesCount = decodeRecords(defaults.string(forKey: Self.updatesKey)).count
        let latestCount = decodeRecords(defaults.string(forKey: Self.latestAddedKey)).count

        let metadataFields = decodeRecords(defaults.string(forKey: Self.metadataKey)).first
        guard let metadataFields else {
            return HomeBridgeSnapshotStatus(
                freshness: .unavailable,
                schemaVersion: nil,
                baseURL: nil,
                userID: nil,
                username: nil,
                updatedAt: nil,
                ageSeconds: nil,
                continueCount: continueCount,
                nextUpCount: nextUpCount,
                updatesCount: updatesCount,
                latestCount: latestCount
            )
        }

        guard metadataFields.count >= 5 else {
            return HomeBridgeSnapshotStatus(
                freshness: .malformed,
                schemaVersion: Int(metadataFields[safe: 0] ?? ""),
                baseURL: metadataFields[safe: 1]?.nonEmpty,
                userID: metadataFields[safe: 2]?.nonEmpty,
                username: metadataFields[safe: 3]?.nonEmpty,
                updatedAt: nil,
                ageSeconds: nil,
                continueCount: continueCount,
                nextUpCount: nextUpCount,
                updatesCount: updatesCount,
                latestCount: latestCount
            )
        }

        let schemaVersion = Int(metadataFields[0])
        let baseURL = metadataFields[1].nonEmpty
        let userID = metadataFields[2].nonEmpty
        let username = metadataFields[3].nonEmpty
        let updatedAt = ISO8601DateFormatter().date(from: metadataFields[4])
        let ageSeconds = updatedAt.map { max(0, Int(Date().timeIntervalSince($0))) }
        let isSessionMismatch: Bool
        if let session {
            isSessionMismatch = baseURL != session.baseURL || userID != session.userId
        } else {
            isSessionMismatch = false
        }

        let freshness: HomeBridgeSnapshotFreshness
        if updatedAt == nil {
            freshness = .malformed
        } else if isSessionMismatch {
            freshness = .sessionMismatch
        } else if (ageSeconds ?? 0) > Int(staleAfterSeconds) {
            freshness = .stale
        } else {
            freshness = .fresh
        }

        return HomeBridgeSnapshotStatus(
            freshness: freshness,
            schemaVersion: schemaVersion,
            baseURL: baseURL,
            userID: userID,
            username: username,
            updatedAt: updatedAt,
            ageSeconds: ageSeconds,
            continueCount: continueCount,
            nextUpCount: nextUpCount,
            updatesCount: updatesCount,
            latestCount: latestCount
        )
    }

    func consumePendingDetailRequest(for session: JellyfinSession) -> String? {
        let nowMillis = currentEpochMillis()
        var remainingRequests: [[String]] = []
        var matchedItemID: String?

        for fields in decodeRecords(defaults.string(forKey: Self.detailRequestQueueKey)) {
            guard fields.count >= 4 else {
                continue
            }

            let requestedBaseURL = fields[0]
            let requestedUserID = fields[1]
            let requestedItemID = fields[2]
            let requestedAtMillis = Int64(fields[3]) ?? 0

            if requestedAtMillis > 0 && nowMillis - requestedAtMillis > Self.detailRequestTTLMillis {
                continue
            }

            if matchedItemID == nil,
               requestedBaseURL == session.baseURL,
               requestedUserID == session.userId {
                matchedItemID = requestedItemID
                continue
            }

            remainingRequests.append(fields)
        }

        if remainingRequests.isEmpty {
            defaults.removeObject(forKey: Self.detailRequestQueueKey)
        } else {
            defaults.set(encodeRecords(remainingRequests), forKey: Self.detailRequestQueueKey)
        }

        if let matchedItemID {
            maFeiLog(
                .debug,
                tag: "KMPHomeBridgeStore",
                "consumed pending detail request itemId=\(redactIdentifier(matchedItemID)) userId=\(redactIdentifier(session.userId))"
            )
        }
        return matchedItemID
    }

    func ensureDetailSnapshot(for session: JellyfinSession, itemID: String) async throws -> Bool {
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "ensure detail snapshot itemId=\(redactIdentifier(itemID)) userId=\(redactIdentifier(session.userId))"
        )
        let client = try authService.makeAuthenticatedClient(for: session)
        guard let snapshot = try await fetchDetailSnapshot(
            client: client,
            session: session,
            itemID: itemID
        ) else {
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "detail snapshot fetch returned nil itemId=\(redactIdentifier(itemID)) userId=\(redactIdentifier(session.userId))"
            )
            recordDetailFailureSnapshot(
                for: session,
                itemID: itemID,
                kind: .emptySnapshot
            )
            return false
        }

        saveDetailSnapshot(snapshot, session: session)
        var indexedItemIDs = Set(loadPersistedDetailItemIDs())
        indexedItemIDs.insert(itemID)
        defaults.set(
            encodeRecords(indexedItemIDs.sorted().map { [$0] }),
            forKey: Self.detailIndexKey
        )
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "detail snapshot saved itemId=\(redactIdentifier(snapshot.itemId)) episodes=\(snapshot.episodes.count) updateCount=\(snapshot.updateCount)"
        )
        return true
    }

    func recordDetailFailureSnapshot(
        for session: JellyfinSession,
        itemID: String,
        kind: DetailBridgeFailureKind
    ) {
        let statusMessage: String
        switch kind {
        case .network:
            statusMessage = "iOS native detail prefetch failed because the network request did not complete. Please retry."
        case .cancelled:
            statusMessage = "iOS native detail prefetch was cancelled before snapshot data was ready. Please retry."
        case .emptySnapshot:
            statusMessage = "iOS native detail prefetch returned an empty snapshot. Please retry."
        case .nativeError:
            statusMessage = "iOS native detail prefetch failed with a native error. Please retry."
        }

        let snapshot = DetailSnapshot(
            itemId: itemID,
            title: "详情暂不可用",
            metaLine: "详情加载失败 · 可重试",
            synopsis: statusMessage,
            isFavorite: false,
            continueEpisodeId: 1,
            continueProgressLabel: "等待重试",
            continueProgressPercent: 0,
            updateCount: 0,
            episodes: [
                DetailEpisodeItem(
                    id: 1,
                    title: "正片",
                    durationLabel: "--",
                    isNew: false,
                    playbackItemId: itemID,
                    thumbnailUrl: nil
                )
            ],
            bridgeState: "bridge-failure:\(kind.rawValue)"
        )

        saveDetailSnapshot(snapshot, session: session)
        var indexedItemIDs = Set(loadPersistedDetailItemIDs())
        indexedItemIDs.insert(itemID)
        defaults.set(
            encodeRecords(indexedItemIDs.sorted().map { [$0] }),
            forKey: Self.detailIndexKey
        )
        maFeiLog(
            .warning,
            tag: "KMPHomeBridgeStore",
            "detail failure snapshot saved itemId=\(redactIdentifier(itemID)) userId=\(redactIdentifier(session.userId)) reason=\(kind.rawValue)"
        )
    }

    func consumePendingSearchRequest(for session: JellyfinSession) -> String? {
        let request = decodeRecords(defaults.string(forKey: Self.searchRequestKey)).first
        guard let fields = request, fields.count >= 4 else {
            defaults.removeObject(forKey: Self.searchRequestKey)
            return nil
        }

        let requestedBaseURL = fields[0]
        let requestedUserID = fields[1]
        let requestedQuery = fields[2]
        let requestedAtMillis = Int64(fields[3]) ?? 0
        let isExpired = requestedAtMillis > 0 && currentEpochMillis() - requestedAtMillis > Self.searchRequestTTLMillis

        guard !isExpired else {
            defaults.removeObject(forKey: Self.searchRequestKey)
            maFeiLog(
                .debug,
                tag: "KMPHomeBridgeStore",
                "drop expired search request queryLength=\(requestedQuery.count)"
            )
            return nil
        }

        guard requestedBaseURL == session.baseURL, requestedUserID == session.userId else {
            return nil
        }

        defaults.removeObject(forKey: Self.searchRequestKey)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "consumed pending search request queryLength=\(requestedQuery.count) userId=\(redactIdentifier(session.userId))"
        )
        return requestedQuery
    }

    func ensureSearchSnapshot(for session: JellyfinSession, query: String) async throws -> Bool {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "ensure search snapshot queryLength=\(trimmedQuery.count) baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId))"
        )
        guard !trimmedQuery.isEmpty else {
            defaults.removeObject(forKey: Self.searchMetaKey)
            defaults.removeObject(forKey: Self.searchItemsKey)
            maFeiLog(
                .info,
                tag: "KMPHomeBridgeStore",
                "search snapshot cleared for empty query userId=\(redactIdentifier(session.userId))"
            )
            return true
        }

        let client = try authService.makeAuthenticatedClient(for: session)
        let response = try await client.send(
            Paths.getSearchHints(
                parameters: .init(
                    limit: 20,
                    userID: session.userId,
                    searchTerm: trimmedQuery,
                    includeItemTypes: [.series, .movie, .episode],
                    isIncludePeople: false,
                    isIncludeMedia: true,
                    isIncludeGenres: false,
                    isIncludeStudios: false,
                    isIncludeArtists: false
                )
            )
        ).value

        var seenItemIDs: Set<String> = []
        let items = (response.searchHints ?? []).compactMap { hint -> SearchLibraryItem? in
            guard let item = hint.toSearchLibraryItem(session: session) else {
                return nil
            }
            guard !seenItemIDs.contains(item.itemId) else {
                return nil
            }
            seenItemIDs.insert(item.itemId)
            return item
        }

        let metadata = encodeRecord([
            String(Self.schemaVersion),
            session.baseURL,
            session.userId,
            trimmedQuery,
            String(response.totalRecordCount ?? items.count),
            ISO8601DateFormatter().string(from: Date()),
        ])
        defaults.set(metadata, forKey: Self.searchMetaKey)
        defaults.set(
            encodeRecords(items.map { $0.recordFields }),
            forKey: Self.searchItemsKey
        )
        let totalRecordCount = response.totalRecordCount ?? items.count
        if items.isEmpty {
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "search snapshot saved with empty items queryLength=\(trimmedQuery.count) totalRecordCount=\(totalRecordCount) userId=\(redactIdentifier(session.userId))"
            )
        } else {
            maFeiLog(
                .info,
                tag: "KMPHomeBridgeStore",
                "search snapshot saved queryLength=\(trimmedQuery.count) itemCount=\(items.count) totalRecordCount=\(totalRecordCount) userId=\(redactIdentifier(session.userId))"
            )
        }
        return true
    }

    func recordSearchFailureSnapshot(
        for session: JellyfinSession,
        query: String,
        kind: SearchBridgeFailureKind
    ) {
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedQuery.isEmpty else {
            maFeiLog(
                .debug,
                tag: "KMPHomeBridgeStore",
                "skip search failure snapshot for empty query userId=\(redactIdentifier(session.userId))"
            )
            return
        }

        let metadata = encodeRecord([
            String(Self.schemaVersion),
            session.baseURL,
            session.userId,
            trimmedQuery,
            "0",
            ISO8601DateFormatter().string(from: Date()),
            "bridge-failure:\(kind.rawValue)",
        ])
        defaults.set(metadata, forKey: Self.searchMetaKey)
        // Clear stale items so shared-side can render an explicit failure state for this query.
        defaults.set(encodeRecords([]), forKey: Self.searchItemsKey)
        maFeiLog(
            .warning,
            tag: "KMPHomeBridgeStore",
            "search failure snapshot saved queryLength=\(trimmedQuery.count) userId=\(redactIdentifier(session.userId)) reason=\(kind.rawValue)"
        )
    }

    func consumePendingMediaViewRequest(for session: JellyfinSession) -> PendingMediaViewRequest? {
        let request = decodeRecords(defaults.string(forKey: Self.mediaViewRequestKey)).first
        guard let fields = request, fields.count >= 4 else {
            defaults.removeObject(forKey: Self.mediaViewRequestKey)
            return nil
        }

        let requestedBaseURL = fields[0]
        let requestedUserID = fields[1]
        let requestedViewID = fields[2]
        let requestedStartIndex: Int
        let requestedLimit: Int
        let requestedSortMode: MediaViewSortBridgeMode
        let requestedAtMillis: Int64

        if fields.count >= 7 {
            requestedStartIndex = Int(fields[3]) ?? 0
            requestedLimit = Int(fields[4]) ?? 24
            requestedSortMode = MediaViewSortBridgeMode(rawValue: fields[5]) ?? .recent
            requestedAtMillis = Int64(fields[6]) ?? 0
        } else {
            requestedStartIndex = 0
            requestedLimit = 60
            requestedSortMode = .recent
            requestedAtMillis = Int64(fields[3]) ?? 0
        }
        let isExpired = requestedAtMillis > 0 &&
            currentEpochMillis() - requestedAtMillis > Self.mediaViewRequestTTLMillis

        guard !isExpired else {
            defaults.removeObject(forKey: Self.mediaViewRequestKey)
            return nil
        }

        guard requestedBaseURL == session.baseURL, requestedUserID == session.userId else {
            return nil
        }

        defaults.removeObject(forKey: Self.mediaViewRequestKey)
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "consumed pending mediaView request viewId=\(redactIdentifier(requestedViewID)) startIndex=\(max(0, requestedStartIndex)) limit=\(max(1, requestedLimit)) sort=\(requestedSortMode.rawValue)"
        )
        return PendingMediaViewRequest(
            viewID: requestedViewID,
            startIndex: max(0, requestedStartIndex),
            limit: max(1, requestedLimit),
            sortMode: requestedSortMode
        )
    }

    func ensureMediaViewSnapshot(
        for session: JellyfinSession,
        request: PendingMediaViewRequest
    ) async throws -> Bool {
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "ensure mediaView snapshot viewId=\(redactIdentifier(request.viewID)) startIndex=\(request.startIndex) limit=\(request.limit) sort=\(request.sortMode.rawValue)"
        )
        let client = try authService.makeAuthenticatedClient(for: session)
        guard let requestedView = try await fetchMediaViewDefinition(
            client: client,
            session: session,
            viewID: request.viewID
        ) else {
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "missing mediaView definition viewId=\(redactIdentifier(request.viewID))"
            )
            recordMediaViewFailureSnapshot(
                for: session,
                request: request,
                kind: .emptySnapshot
            )
            return false
        }
        guard let snapshot = try await fetchSingleMediaViewSnapshot(
            client: client,
            session: session,
            view: requestedView,
            startIndex: request.startIndex,
            limit: max(request.limit, 24),
            sortMode: request.sortMode
        ) else {
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "mediaView snapshot fetch returned nil viewId=\(redactIdentifier(request.viewID))"
            )
            recordMediaViewFailureSnapshot(
                for: session,
                request: request,
                kind: .emptySnapshot
            )
            return false
        }
        saveMediaViewSnapshotPage(
            snapshot,
            session: session,
            sortMode: request.sortMode,
            startIndex: request.startIndex
        )
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "mediaView snapshot saved viewId=\(redactIdentifier(request.viewID)) items=\(snapshot.items.count) totalCount=\(snapshot.totalCount) startIndex=\(request.startIndex)"
        )
        return true
    }

    func recordMediaViewFailureSnapshot(
        for session: JellyfinSession,
        request: PendingMediaViewRequest,
        kind: MediaViewBridgeFailureKind
    ) {
        let existingSummaryRecords = loadPersistedMediaViewSummaryRecords()
        let existingSummary = existingSummaryRecords.first { $0.viewId == request.viewID }
        let existingItems = loadPersistedMediaViewItems(
            viewID: request.viewID,
            sortMode: request.sortMode
        )
        let fallbackRecentItems: [MediaViewLibraryItem]
        if request.sortMode == .title && existingItems.isEmpty {
            fallbackRecentItems = loadPersistedMediaViewItems(
                viewID: request.viewID,
                sortMode: .recent
            )
        } else {
            fallbackRecentItems = []
        }
        let preservedItemCount = max(existingItems.count, fallbackRecentItems.count)
        let resolvedTotalCount = max(existingSummary?.totalCount ?? 0, preservedItemCount)
        let failureSummary = MediaViewSummaryRecord(
            viewId: request.viewID,
            title: existingSummary?.title ?? "内容分区",
            subtitle: existingSummary?.subtitle ?? "iOS 分区桥接失败，可重试",
            totalCount: resolvedTotalCount,
            bridgeState: "bridge-failure:\(kind.rawValue)"
        )

        let updatedRecords = existingSummaryRecords
            .filter { $0.viewId != request.viewID } + [failureSummary]
        saveMediaViewMetadata(summaryCount: updatedRecords.count, session: session)
        defaults.set(
            encodeRecords(updatedRecords.map(\.recordFields)),
            forKey: Self.mediaViewViewsKey
        )

        let storageKey = mediaViewItemsKey(for: request.viewID, sortMode: request.sortMode)
        if !existingItems.isEmpty {
            defaults.set(
                encodeRecords(existingItems.map(\.recordFields)),
                forKey: storageKey
            )
        } else if !fallbackRecentItems.isEmpty {
            defaults.set(
                encodeRecords(fallbackRecentItems.map(\.recordFields)),
                forKey: storageKey
            )
        }

        maFeiLog(
            .warning,
            tag: "KMPHomeBridgeStore",
            "mediaView failure snapshot saved viewId=\(redactIdentifier(request.viewID)) startIndex=\(request.startIndex) limit=\(request.limit) sort=\(request.sortMode.rawValue) preservedItems=\(preservedItemCount) reason=\(kind.rawValue)"
        )
    }

    func consumePendingFavoritesRequest(for session: JellyfinSession) -> Bool {
        let request = decodeRecords(defaults.string(forKey: Self.favoritesRequestKey)).first
        guard let fields = request, fields.count >= 3 else {
            defaults.removeObject(forKey: Self.favoritesRequestKey)
            return false
        }

        let requestedBaseURL = fields[0]
        let requestedUserID = fields[1]
        let requestedAtMillis = Int64(fields[2]) ?? 0
        let isExpired = requestedAtMillis > 0 && currentEpochMillis() - requestedAtMillis > Self.favoritesRequestTTLMillis

        guard !isExpired else {
            defaults.removeObject(forKey: Self.favoritesRequestKey)
            return false
        }

        guard requestedBaseURL == session.baseURL, requestedUserID == session.userId else {
            return false
        }

        defaults.removeObject(forKey: Self.favoritesRequestKey)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "consumed pending favorites request userId=\(redactIdentifier(session.userId))"
        )
        return true
    }

    func ensureFavoritesSnapshot(for session: JellyfinSession) async throws -> Bool {
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "ensure favorites snapshot userId=\(redactIdentifier(session.userId))"
        )
        let client = try authService.makeAuthenticatedClient(for: session)
        let items = try await fetchFavoritesSnapshot(
            client: client,
            session: session
        )
        saveFavoritesSnapshot(items, session: session)
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "favorites snapshot saved itemCount=\(items.count) userId=\(redactIdentifier(session.userId))"
        )
        return true
    }

    func recordFavoritesFailureSnapshot(
        for session: JellyfinSession,
        kind: FavoritesBridgeFailureKind
    ) {
        let existingItems = loadPersistedFavoriteItems()
        saveFavoritesSnapshot(
            existingItems,
            session: session,
            bridgeState: "bridge-failure:\(kind.rawValue)"
        )
        maFeiLog(
            .warning,
            tag: "KMPHomeBridgeStore",
            "favorites failure snapshot saved itemCount=\(existingItems.count) userId=\(redactIdentifier(session.userId)) reason=\(kind.rawValue)"
        )
    }

    func consumePendingFavoritesMutationRequest(for session: JellyfinSession) -> PendingFavoriteMutationRequest? {
        let request = decodeRecords(defaults.string(forKey: Self.favoritesMutationRequestKey)).first
        guard let fields = request, fields.count >= 5 else {
            defaults.removeObject(forKey: Self.favoritesMutationRequestKey)
            return nil
        }

        let requestedBaseURL = fields[0]
        let requestedUserID = fields[1]
        let requestedItemID = fields[2]
        let requestedFavorite = fields[3] == "1"
        let requestedAtMillis = Int64(fields[4]) ?? 0
        let isExpired = requestedAtMillis > 0 &&
            currentEpochMillis() - requestedAtMillis > Self.favoritesMutationRequestTTLMillis

        guard !isExpired else {
            defaults.removeObject(forKey: Self.favoritesMutationRequestKey)
            return nil
        }

        guard requestedBaseURL == session.baseURL, requestedUserID == session.userId else {
            return nil
        }

        defaults.removeObject(forKey: Self.favoritesMutationRequestKey)
        return PendingFavoriteMutationRequest(
            itemID: requestedItemID,
            favorite: requestedFavorite
        )
    }

    func performFavoritesMutation(
        for session: JellyfinSession,
        itemID: String,
        favorite: Bool
    ) async -> Bool {
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "favorites mutation start itemId=\(redactIdentifier(itemID)) favorite=\(favorite ? "1" : "0") userId=\(redactIdentifier(session.userId))"
        )
        do {
            let client = try authService.makeAuthenticatedClient(for: session)
            if favorite {
                _ = try await client.send(
                    Paths.markFavoriteItem(itemID: itemID, userID: session.userId)
                ).value
            } else {
                _ = try await client.send(
                    Paths.unmarkFavoriteItem(itemID: itemID, userID: session.userId)
                ).value
            }

            let favorites = try await fetchFavoritesSnapshot(
                client: client,
                session: session
            )
            saveFavoritesSnapshot(favorites, session: session)
            _ = try? await ensureDetailSnapshot(for: session, itemID: itemID)
            saveFavoritesMutationResult(
                FavoriteMutationBridgeResult(
                    itemID: itemID,
                    favorite: favorite,
                    success: true,
                    errorCode: nil,
                    retryable: false,
                    message: favorite ? "已加入收藏" : "已取消收藏"
                ),
                session: session
            )
            maFeiLog(
                .info,
                tag: "KMPHomeBridgeStore",
                "favorites mutation success itemId=\(redactIdentifier(itemID)) favorite=\(favorite ? "1" : "0") userId=\(redactIdentifier(session.userId))"
            )
            return true
        } catch {
            let failure = mapFavoritesMutationFailure(
                error: error,
                itemID: itemID,
                favorite: favorite
            )
            saveFavoritesMutationResult(
                failure.result,
                session: session
            )
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "favorites mutation failed itemId=\(redactIdentifier(itemID)) favorite=\(favorite ? "1" : "0") userId=\(redactIdentifier(session.userId)) reason=\(failure.kind.rawValue) code=\(failure.result.errorCode ?? "UNKNOWN") retryable=\(failure.result.retryable ? "1" : "0") errorType=\(String(describing: type(of: error)))"
            )
            return false
        }
    }

    private func mapFavoritesMutationFailure(
        error: Error,
        itemID: String,
        favorite: Bool
    ) -> (kind: FavoritesMutationFailureKind, result: FavoriteMutationBridgeResult) {
        let kind = classifyFavoritesMutationFailure(error: error)
        let result = FavoriteMutationBridgeResult(
            itemID: itemID,
            favorite: favorite,
            success: false,
            errorCode: kind.errorCode,
            retryable: kind.retryable,
            message: kind.userMessage(favorite: favorite)
        )
        return (kind, result)
    }

    private func classifyFavoritesMutationFailure(error: Error) -> FavoritesMutationFailureKind {
        if error is CancellationError {
            return .cancelled
        }

        if let urlError = (error as? URLError) ??
            ((error as NSError).userInfo[NSUnderlyingErrorKey] as? URLError) {
            switch urlError.code {
            case .badURL, .unsupportedURL:
                return .invalidUrl
            case .secureConnectionFailed, .serverCertificateHasBadDate, .serverCertificateHasUnknownRoot,
                 .serverCertificateNotYetValid, .serverCertificateUntrusted, .clientCertificateRejected,
                 .clientCertificateRequired:
                return .tlsCertificateError
            case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost,
                 .dnsLookupFailed, .timedOut, .internationalRoamingOff, .callIsActive, .dataNotAllowed:
                return .network
            case .cancelled:
                return .cancelled
            default:
                return .nativeError
            }
        }

        if let statusCode = extractStatusCode(from: error) {
            switch statusCode {
            case 401:
                return .authFailed
            case 403:
                return .forbidden
            case 500 ... 599:
                return .server
            default:
                return .nativeError
            }
        }

        return .nativeError
    }

    private func classifyHomeBridgeRefreshFailure(error: Error) -> HomeBridgeRefreshFailureKind {
        if error is CancellationError {
            return .cancelled
        }

        if let urlError = (error as? URLError) ??
            ((error as NSError).userInfo[NSUnderlyingErrorKey] as? URLError) {
            switch urlError.code {
            case .cancelled:
                return .cancelled
            case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost,
                 .dnsLookupFailed, .timedOut, .internationalRoamingOff, .callIsActive, .dataNotAllowed:
                return .network
            default:
                break
            }
        }

        if let statusCode = extractStatusCode(from: error) {
            switch statusCode {
            case 401, 403:
                return .auth
            case 500 ... 599:
                return .server
            default:
                break
            }
        }

        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func extractStatusCode(from error: Error) -> Int? {
        let localized = error.localizedDescription
        if let status = matchStatusCode(in: localized) {
            return status
        }

        let described = String(describing: error)
        if let status = matchStatusCode(in: described) {
            return status
        }

        let nsError = error as NSError
        if let status = matchStatusCode(in: nsError.domain) {
            return status
        }

        return nil
    }

    private func matchStatusCode(in text: String) -> Int? {
        let patterns = [
            #"unacceptableStatusCode\((\d{3})\)"#,
            #"status code was unacceptable:\s*(\d{3})"#,
            #"\bHTTP\s*(\d{3})\b"#,
            #"\b(\d{3})\b"#,
        ]

        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else {
                continue
            }
            let range = NSRange(text.startIndex..<text.endIndex, in: text)
            guard let match = regex.firstMatch(in: text, options: [], range: range),
                  match.numberOfRanges >= 2,
                  let codeRange = Range(match.range(at: 1), in: text),
                  let code = Int(text[codeRange]),
                  (100 ... 599).contains(code)
            else {
                continue
            }
            return code
        }

        return nil
    }

    private func fetchFavoritesSnapshot(
        client: JellyfinClient,
        session: JellyfinSession
    ) async throws -> [FavoriteLibraryItem] {
        let response = try await client.send(
            Paths.getItems(
                parameters: .init(
                    userID: session.userId,
                    limit: 60,
                    isRecursive: true,
                    sortOrder: [.ascending],
                    includeItemTypes: [.series, .movie],
                    isFavorite: true,
                    sortBy: [.sortName],
                    enableUserData: true
                )
            )
        ).value

        var seenItemIDs: Set<String> = []
        return (response.items ?? []).compactMap { item -> FavoriteLibraryItem? in
            guard let favorite = item.toFavoriteLibraryItem(session: session) else {
                return nil
            }
            guard !seenItemIDs.contains(favorite.itemId) else {
                return nil
            }
            seenItemIDs.insert(favorite.itemId)
            return favorite
        }
    }

    private func saveFavoritesSnapshot(
        _ items: [FavoriteLibraryItem],
        session: JellyfinSession,
        bridgeState: String? = nil
    ) {
        let metadata = encodeRecord([
            String(Self.schemaVersion),
            session.baseURL,
            session.userId,
            String(items.count),
            ISO8601DateFormatter().string(from: Date()),
            bridgeState ?? "",
        ])
        defaults.set(metadata, forKey: Self.favoritesMetaKey)
        defaults.set(
            encodeRecords(items.map { $0.recordFields }),
            forKey: Self.favoritesItemsKey
        )
    }

    private func loadPersistedFavoriteItems() -> [FavoriteLibraryItem] {
        decodeRecords(defaults.string(forKey: Self.favoritesItemsKey))
            .compactMap { fields in
                guard fields.count >= 3 else {
                    return nil
                }
                return FavoriteLibraryItem(
                    itemId: fields[0],
                    title: fields[1],
                    subtitle: fields[2],
                    posterUrl: fields[safe: 3]?.nonEmpty
                )
            }
    }

    private func saveMediaViewSnapshots(_ snapshots: [MediaViewBridgeSnapshot], session: JellyfinSession) {
        let previousViewIDs = Set(loadPersistedMediaViewIDs())
        let currentViewIDs = Set(snapshots.map(\.viewId))

        for staleViewID in previousViewIDs.subtracting(currentViewIDs) {
            defaults.removeObject(forKey: mediaViewItemsKey(for: staleViewID, sortMode: .recent))
            defaults.removeObject(forKey: mediaViewItemsKey(for: staleViewID, sortMode: .title))
        }

        saveMediaViewMetadata(summaryCount: snapshots.count, session: session)
        defaults.set(
            encodeRecords(snapshots.map { $0.summaryRecord.recordFields }),
            forKey: Self.mediaViewViewsKey
        )

        for snapshot in snapshots {
            defaults.set(
                encodeRecords(snapshot.items.map { $0.recordFields }),
                forKey: mediaViewItemsKey(for: snapshot.viewId, sortMode: .recent)
            )
            defaults.removeObject(forKey: mediaViewItemsKey(for: snapshot.viewId, sortMode: .title))
        }

        defaults.removeObject(forKey: Self.mediaViewRequestKey)
    }

    private func saveMediaViewSnapshotPage(
        _ snapshot: MediaViewBridgeSnapshot,
        session: JellyfinSession,
        sortMode: MediaViewSortBridgeMode,
        startIndex: Int
    ) {
        var summaryRecords = loadPersistedMediaViewSummaryRecords()
            .filter { $0.viewId != snapshot.viewId }
        summaryRecords.append(snapshot.summaryRecord)
        saveMediaViewMetadata(summaryCount: summaryRecords.count, session: session)
        defaults.set(
            encodeRecords(summaryRecords.map(\.recordFields)),
            forKey: Self.mediaViewViewsKey
        )

        let storageKey = mediaViewItemsKey(for: snapshot.viewId, sortMode: sortMode)
        let mergedItems = mergeMediaViewItems(
            existing: loadPersistedMediaViewItems(viewID: snapshot.viewId, sortMode: sortMode),
            incoming: snapshot.items,
            startIndex: startIndex
        )
        defaults.set(
            encodeRecords(mergedItems.map(\.recordFields)),
            forKey: storageKey
        )
    }

    private func saveMediaViewMetadata(summaryCount: Int, session: JellyfinSession) {
        let metadata = encodeRecord([
            String(Self.schemaVersion),
            session.baseURL,
            session.userId,
            String(summaryCount),
            ISO8601DateFormatter().string(from: Date()),
        ])
        defaults.set(metadata, forKey: Self.mediaViewMetaKey)
    }

    private func saveFavoritesMutationResult(
        _ result: FavoriteMutationBridgeResult,
        session: JellyfinSession
    ) {
        let metadata = encodeRecord(result.metadataFields(
            schemaVersion: Self.schemaVersion,
            baseURL: session.baseURL,
            userID: session.userId
        ))
        defaults.set(metadata, forKey: Self.favoritesMutationResultKey)
    }

    func consumePendingPlaybackRequest(for session: JellyfinSession) -> PendingPlaybackRequest? {
        let request = decodeRecords(defaults.string(forKey: Self.playbackRequestKey)).first
        guard let fields = request, fields.count >= 6 else {
            defaults.removeObject(forKey: Self.playbackRequestKey)
            return nil
        }

        let requestedBaseURL = fields[0]
        let requestedUserID = fields[1]
        let requestedItemID = fields[2]
        let requestedEpisodeID = Int(fields[3]) ?? 1
        let requestedPlaybackItemID = fields[4].nonEmpty
        let requestedAtMillis = Int64(fields[5]) ?? 0
        let isExpired = requestedAtMillis > 0 && currentEpochMillis() - requestedAtMillis > Self.playbackRequestTTLMillis

        guard !isExpired else {
            defaults.removeObject(forKey: Self.playbackRequestKey)
            return nil
        }

        guard requestedBaseURL == session.baseURL, requestedUserID == session.userId else {
            return nil
        }

        defaults.removeObject(forKey: Self.playbackRequestKey)
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "consumed pending playback request itemId=\(redactIdentifier(requestedItemID)) episodeId=\(max(1, requestedEpisodeID)) playbackItemId=\(redactIdentifier(requestedPlaybackItemID)) userId=\(redactIdentifier(session.userId))"
        )
        return PendingPlaybackRequest(
            itemID: requestedItemID,
            episodeID: max(1, requestedEpisodeID),
            playbackItemID: requestedPlaybackItemID
        )
    }

    func ensurePlaybackSnapshot(
        for session: JellyfinSession,
        itemID: String,
        episodeID: Int,
        playbackItemID: String?
    ) async throws -> Bool {
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "ensure playback snapshot itemId=\(redactIdentifier(itemID)) episodeId=\(max(1, episodeID)) playbackItemId=\(redactIdentifier(playbackItemID))"
        )
        let client = try authService.makeAuthenticatedClient(for: session)
        guard let snapshot = try await fetchPlaybackSnapshot(
            client: client,
            session: session,
            itemID: itemID,
            episodeID: max(1, episodeID),
            playbackItemID: playbackItemID
        ) else {
            maFeiLog(
                .warning,
                tag: "KMPHomeBridgeStore",
                "playback snapshot fetch returned nil itemId=\(redactIdentifier(itemID)) episodeId=\(max(1, episodeID))"
            )
            recordPlaybackFailureSnapshot(
                for: session,
                itemID: itemID,
                episodeID: episodeID,
                playbackItemID: playbackItemID,
                kind: .emptySnapshot
            )
            return false
        }

        savePlaybackSnapshot(snapshot, session: session)
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "playback snapshot saved itemId=\(redactIdentifier(snapshot.itemId)) playbackItemId=\(redactIdentifier(snapshot.playbackItemId)) streamType=\(snapshot.streamTypeLabel)"
        )
        return true
    }

    func recordPlaybackFailureSnapshot(
        for session: JellyfinSession,
        itemID: String,
        episodeID: Int,
        playbackItemID: String?,
        kind: PlaybackBridgeFailureKind
    ) {
        let normalizedEpisodeID = max(1, episodeID)
        let resolvedPlaybackItemID = playbackItemID?.nonEmpty ?? itemID
        let statusMessage: String
        switch kind {
        case .network:
            statusMessage = "iOS native playback preflight failed because the network request did not complete. Please retry."
        case .cancelled:
            statusMessage = "iOS native playback preflight was cancelled before stream metadata was ready. Please retry."
        case .emptySnapshot:
            statusMessage = "iOS native playback preflight returned an empty snapshot. Please retry."
        case .nativeError:
            statusMessage = "iOS native playback preflight failed with a native error. Please retry."
        }

        let snapshot = PlaybackSnapshot(
            itemId: itemID,
            episodeId: normalizedEpisodeID,
            requestedPlaybackItemId: playbackItemID?.nonEmpty,
            playbackItemId: resolvedPlaybackItemID,
            showTitle: "Playback preflight",
            episodeTitle: "Episode \(normalizedEpisodeID)",
            streamUrl: "N/A",
            streamTypeLabel: "bridge-failure:\(kind.rawValue)",
            playSessionId: nil,
            mediaSourceId: nil,
            mediaContainer: nil,
            runtimeLabel: "--",
            startPositionLabel: "00:00",
            statusMessage: statusMessage
        )

        savePlaybackSnapshot(snapshot, session: session)
        maFeiLog(
            .warning,
            tag: "KMPHomeBridgeStore",
            "playback failure snapshot saved itemId=\(redactIdentifier(itemID)) episodeId=\(normalizedEpisodeID) playbackItemId=\(redactIdentifier(resolvedPlaybackItemID)) reason=\(kind.rawValue)"
        )
    }

    private func refreshDetailSnapshots(
        client: JellyfinClient,
        session: JellyfinSession,
        itemIDs: [String],
        shouldContinue: @escaping () -> Bool,
        commit: RefreshCommit?
    ) async throws {
        guard !itemIDs.isEmpty else {
            return
        }
        try ensureRefreshCanContinue(shouldContinue)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "refresh detail snapshots start requested=\(itemIDs.count) userId=\(redactIdentifier(session.userId))"
        )

        var indexedItemIDs = Set(loadPersistedDetailItemIDs())
        var savedCount = 0
        for itemId in itemIDs.prefix(Self.detailPrefetchLimit) {
            try ensureRefreshCanContinue(shouldContinue)
            guard let snapshot = try await fetchDetailSnapshot(
                client: client,
                session: session,
                itemID: itemId
            ) else {
                continue
            }
            try commitRefreshWrite(commit) { [self] in
                self.saveDetailSnapshot(snapshot, session: session)
            }
            indexedItemIDs.insert(itemId)
            savedCount += 1
        }

        try commitRefreshWrite(commit) { [self] in
            self.defaults.set(
                self.encodeRecords(indexedItemIDs.sorted().map { [$0] }),
                forKey: Self.detailIndexKey
            )
        }
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "refresh detail snapshots complete saved=\(savedCount) requested=\(min(itemIDs.count, Self.detailPrefetchLimit)) userId=\(redactIdentifier(session.userId))"
        )
    }

    private func refreshMediaViewSnapshots(
        client: JellyfinClient,
        session: JellyfinSession,
        shouldContinue: @escaping () -> Bool,
        commit: RefreshCommit?
    ) async throws {
        try ensureRefreshCanContinue(shouldContinue)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "refresh mediaView snapshots start userId=\(redactIdentifier(session.userId))"
        )
        let snapshots = try await fetchMediaViewSnapshots(
            client: client,
            session: session,
            shouldContinue: shouldContinue
        )
        try commitRefreshWrite(commit) { [self] in
            self.saveMediaViewSnapshots(snapshots, session: session)
        }
        maFeiLog(
            .info,
            tag: "KMPHomeBridgeStore",
            "refresh mediaView snapshots complete viewCount=\(snapshots.count) userId=\(redactIdentifier(session.userId))"
        )
    }

    private func fetchMediaViewSnapshots(
        client: JellyfinClient,
        session: JellyfinSession,
        shouldContinue: @escaping () -> Bool
    ) async throws -> [MediaViewBridgeSnapshot] {
        var snapshots: [MediaViewBridgeSnapshot] = []
        try ensureRefreshCanContinue(shouldContinue)
        let definitions = try await fetchMediaViewDefinitions(client: client, session: session)
        maFeiLog(
            .debug,
            tag: "KMPHomeBridgeStore",
            "fetched mediaView definitions count=\(definitions.count) userId=\(redactIdentifier(session.userId))"
        )
        for view in definitions.prefix(6) {
            try ensureRefreshCanContinue(shouldContinue)
            guard let snapshot = try await fetchSingleMediaViewSnapshot(
                client: client,
                session: session,
                view: view,
                startIndex: 0,
                limit: 60,
                sortMode: .recent
            ) else {
                continue
            }
            snapshots.append(snapshot)
        }
        return snapshots
    }

    private func ensureRefreshCanContinue(_ shouldContinue: () -> Bool) throws {
        if Task.isCancelled || !shouldContinue() {
            throw CancellationError()
        }
    }

    private func commitRefreshWrite(
        _ commit: RefreshCommit?,
        operation: @escaping () -> Void
    ) throws {
        if let commit {
            guard commit(operation) else {
                throw CancellationError()
            }
            return
        }

        operation()
    }

    private func fetchMediaViewDefinitions(
        client: JellyfinClient,
        session: JellyfinSession
    ) async throws -> [BaseItemDto] {
        let viewsResponse = try await client.send(
            Paths.getUserViews(
                parameters: .init(
                    userID: session.userId,
                    isIncludeExternalContent: false,
                    isIncludeHidden: false
                )
            )
        ).value

        return viewsResponse.items ?? []
    }

    private func fetchMediaViewDefinition(
        client: JellyfinClient,
        session: JellyfinSession,
        viewID: String
    ) async throws -> BaseItemDto? {
        try await fetchMediaViewDefinitions(client: client, session: session)
            .first { $0.id?.nonEmpty == viewID }
    }

    private func fetchSingleMediaViewSnapshot(
        client: JellyfinClient,
        session: JellyfinSession,
        view: BaseItemDto,
        startIndex: Int,
        limit: Int,
        sortMode: MediaViewSortBridgeMode
    ) async throws -> MediaViewBridgeSnapshot? {
        guard let viewID = view.id?.nonEmpty else {
            return nil
        }

        let itemsResponse = try await client.send(
            Paths.getItemsByUserID(
                userID: session.userId,
                parameters: .init(
                    startIndex: max(0, startIndex),
                    limit: max(1, limit),
                    isRecursive: true,
                    sortOrder: [sortMode.sortOrder],
                    parentID: viewID,
                    includeItemTypes: [.series, .movie, .episode],
                    sortBy: [sortMode.sortBy],
                    enableUserData: true
                )
            )
        ).value

        let mediaItems: [BaseItemDto] = itemsResponse.items ?? []
        var seenItemIDs: Set<String> = []
        let items = mediaItems.compactMap { item -> MediaViewLibraryItem? in
            guard let mapped = item.toMediaViewLibraryItem(session: session) else {
                return nil
            }
            guard !seenItemIDs.contains(mapped.itemId) else {
                return nil
            }
            seenItemIDs.insert(mapped.itemId)
            return mapped
        }

        let title = view.name?.nonEmpty ?? "未命名分区"
        let totalCount = itemsResponse.totalRecordCount ?? items.count
        let subtitle = buildMediaViewSubtitle(view: view, itemCount: totalCount)
        return MediaViewBridgeSnapshot(
            viewId: viewID,
            title: title,
            subtitle: subtitle,
            totalCount: totalCount,
            items: items
        )
    }

    private func mergeMediaViewItems(
        existing: [MediaViewLibraryItem],
        incoming: [MediaViewLibraryItem],
        startIndex: Int
    ) -> [MediaViewLibraryItem] {
        guard startIndex > 0, !existing.isEmpty else {
            return incoming
        }

        let prefixCount = min(existing.count, startIndex)
        var merged = Array(existing.prefix(prefixCount))
        var seenItemIDs = Set(merged.map(\.itemId))

        for item in incoming {
            guard !seenItemIDs.contains(item.itemId) else {
                continue
            }
            seenItemIDs.insert(item.itemId)
            merged.append(item)
        }

        return merged
    }

    private func fetchDetailSnapshot(
        client: JellyfinClient,
        session: JellyfinSession,
        itemID: String
    ) async throws -> DetailSnapshot? {
        let targetItem = try await client.send(
            Paths.getItem(itemID: itemID, userID: session.userId)
        ).value
        let detailRoot = try await resolveDetailRootItem(
            client: client,
            userID: session.userId,
            item: targetItem
        )
        let episodeDtos = try await loadEpisodesIfSeries(
            client: client,
            userID: session.userId,
            detailRoot: detailRoot
        )

        let episodes: [DetailEpisodeItem]
        if episodeDtos.isEmpty {
            episodes = [
                DetailEpisodeItem(
                    id: 1,
                    title: "正片",
                    durationLabel: formatRuntime(detailRoot.runTimeTicks),
                    isNew: false,
                    playbackItemId: detailRoot.id ?? itemID,
                    thumbnailUrl: detailRoot.buildThumbArtworkURL(session: session)
                )
            ]
        } else {
            episodes = episodeDtos.enumerated().map { index, episode in
                episode.toDetailEpisodeItem(displayIndex: index + 1, session: session)
            }
        }

        let continueEpisodeIndex = episodeDtos.firstIndex { episode in
            let percent = episode.userData?.playedPercentage ?? 0
            return percent > 0 && percent < 100
        } ?? 0
        let continueSource = episodeDtos[safe: continueEpisodeIndex] ?? detailRoot
        let continuePercent = Int((continueSource.userData?.playedPercentage ?? 0).rounded())
            .clamped(to: 0 ... 100)

        return DetailSnapshot(
            itemId: itemID,
            title: detailRoot.name?.nonEmpty
                ?? detailRoot.seriesName?.nonEmpty
                ?? targetItem.name?.nonEmpty
                ?? "Untitled",
            metaLine: detailRoot.toDetailMetaLine(episodeDtos),
            synopsis: detailRoot.overview?.nonEmpty ?? "暂无简介。",
            isFavorite: detailRoot.userData?.isFavorite ?? false,
            continueEpisodeId: episodes[safe: continueEpisodeIndex]?.id ?? 1,
            continueProgressLabel: buildContinueLabel(
                source: continueSource,
                fallback: "未开始播放"
            ),
            continueProgressPercent: continuePercent,
            updateCount: episodes.filter { $0.isNew }.count,
            episodes: episodes,
            posterUrl: detailRoot.buildPrimaryArtworkURL(session: session)
                ?? targetItem.buildPrimaryArtworkURL(session: session),
            backdropUrl: detailRoot.buildBackdropArtworkURL(session: session)
                ?? targetItem.buildBackdropArtworkURL(session: session)
        )
    }

    private func fetchPlaybackSnapshot(
        client: JellyfinClient,
        session: JellyfinSession,
        itemID: String,
        episodeID: Int,
        playbackItemID: String?
    ) async throws -> PlaybackSnapshot? {
        let playbackSeedItem: BaseItemDto
        if let preferredPlaybackItemID = playbackItemID?.nonEmpty, preferredPlaybackItemID != itemID {
            playbackSeedItem = try await client.send(
                Paths.getItem(itemID: preferredPlaybackItemID, userID: session.userId)
            ).value
        } else {
            playbackSeedItem = try await client.send(
                Paths.getItem(itemID: itemID, userID: session.userId)
            ).value
        }

        let playableItem = try await resolvePlaybackItem(
            client: client,
            userID: session.userId,
            routeItemID: itemID,
            episodeID: episodeID,
            seedItem: playbackSeedItem
        )
        let resolvedPlaybackItemID = playableItem.id ?? playbackSeedItem.id ?? itemID
        let playbackInfo = try await client.send(
            Paths.getPostedPlaybackInfo(
                itemID: resolvedPlaybackItemID,
                parameters: .init(
                    userID: session.userId,
                    enableDirectPlay: true,
                    enableDirectStream: true,
                    enableTranscoding: true,
                    allowVideoStreamCopy: true,
                    allowAudioStreamCopy: true
                )
            )
        ).value

        let mediaSource = selectPlaybackMediaSource(from: playbackInfo.mediaSources)
        let showTitle = playableItem.seriesName?.nonEmpty
            ?? playbackSeedItem.seriesName?.nonEmpty
            ?? playbackSeedItem.name?.nonEmpty
            ?? playableItem.name?.nonEmpty
            ?? "Untitled"
        let episodeTitle = buildPlaybackEpisodeTitle(
            playableItem,
            fallbackEpisodeID: episodeID
        )
        let streamHandle = mediaSource == nil
            ? "N/A"
            : buildPlaybackBridgeHandle(
                playbackItemID: resolvedPlaybackItemID,
                playSessionID: playbackInfo.playSessionID,
                mediaSourceID: mediaSource?.id
            )
        let runtimeLabel = formatRuntime(mediaSource?.runTimeTicks ?? playableItem.runTimeTicks)
        let startPositionLabel = formatTicks(playableItem.userData?.playbackPositionTicks ?? 0)
        let mediaContainer = mediaSource?.transcodingContainer?.nonEmpty ?? mediaSource?.container?.nonEmpty
        let statusMessage: String
        if mediaSource == nil {
            statusMessage = "iOS native playback preflight resolved the target item, but Jellyfin has not returned a media source yet."
        } else {
            statusMessage = "iOS native playback preflight is ready. Shared side gets a safe launch handle; the secure stream URL stays in the native auth path."
        }

        return PlaybackSnapshot(
            itemId: itemID,
            episodeId: episodeID,
            requestedPlaybackItemId: playbackItemID?.nonEmpty,
            playbackItemId: resolvedPlaybackItemID,
            showTitle: showTitle,
            episodeTitle: episodeTitle,
            streamUrl: streamHandle,
            streamTypeLabel: buildPlaybackStreamTypeLabel(mediaSource),
            playSessionId: playbackInfo.playSessionID?.nonEmpty,
            mediaSourceId: mediaSource?.id?.nonEmpty,
            mediaContainer: mediaContainer,
            runtimeLabel: runtimeLabel,
            startPositionLabel: startPositionLabel,
            statusMessage: statusMessage
        )
    }

    private func resolveDetailRootItem(
        client: JellyfinClient,
        userID: String,
        item: BaseItemDto
    ) async throws -> BaseItemDto {
        switch item.type {
        case .episode:
            guard let seriesID = item.seriesID else {
                return item
            }
            return try await client.send(
                Paths.getItem(itemID: seriesID, userID: userID)
            ).value
        case .season:
            guard let parentID = item.parentID else {
                return item
            }
            return try await client.send(
                Paths.getItem(itemID: parentID, userID: userID)
            ).value
        default:
            return item
        }
    }

    private func resolvePlaybackItem(
        client: JellyfinClient,
        userID: String,
        routeItemID: String,
        episodeID: Int,
        seedItem: BaseItemDto
    ) async throws -> BaseItemDto {
        switch seedItem.type {
        case .series:
            let episodes = try await loadEpisodesIfSeries(
                client: client,
                userID: userID,
                detailRoot: seedItem
            )
            return selectPlaybackEpisode(from: episodes, episodeID: episodeID) ?? seedItem
        case .season:
            guard let seriesID = seedItem.parentID else {
                return seedItem
            }
            let seriesRoot = try await client.send(
                Paths.getItem(itemID: seriesID, userID: userID)
            ).value
            let seasonEpisodes = try await loadEpisodesIfSeries(
                client: client,
                userID: userID,
                detailRoot: seriesRoot
            ).filter { episode in
                episode.parentIndexNumber == seedItem.indexNumber
            }
            return selectPlaybackEpisode(from: seasonEpisodes, episodeID: episodeID) ?? seasonEpisodes.first ?? seedItem
        case .episode:
            return seedItem
        default:
            if seedItem.id == nil {
                return try await client.send(
                    Paths.getItem(itemID: routeItemID, userID: userID)
                ).value
            }
            return seedItem
        }
    }

    private func loadEpisodesIfSeries(
        client: JellyfinClient,
        userID: String,
        detailRoot: BaseItemDto
    ) async throws -> [BaseItemDto] {
        guard detailRoot.type == .series, let seriesID = detailRoot.id else {
            return []
        }

        let response = try await client.send(
            Paths.getEpisodes(
                seriesID: seriesID,
                parameters: .init(
                    userID: userID,
                    enableUserData: true,
                    sortBy: .indexNumber
                )
            )
        ).value

        return (response.items ?? []).sorted {
            let lhsSeason = $0.parentIndexNumber ?? Int.max
            let rhsSeason = $1.parentIndexNumber ?? Int.max
            if lhsSeason != rhsSeason {
                return lhsSeason < rhsSeason
            }

            let lhsEpisode = $0.indexNumber ?? Int.max
            let rhsEpisode = $1.indexNumber ?? Int.max
            if lhsEpisode != rhsEpisode {
                return lhsEpisode < rhsEpisode
            }

            return ($0.name ?? "") < ($1.name ?? "")
        }
    }

    private func saveDetailSnapshot(_ snapshot: DetailSnapshot, session: JellyfinSession) {
        let metadata = encodeRecord(snapshot.metadataFields(
            schemaVersion: Self.schemaVersion,
            baseURL: session.baseURL,
            userID: session.userId
        ))
        defaults.set(metadata, forKey: detailMetaKey(for: snapshot.itemId))
        defaults.set(
            encodeRecords(snapshot.episodes.map { $0.recordFields }),
            forKey: detailEpisodesKey(for: snapshot.itemId)
        )
    }

    private func savePlaybackSnapshot(_ snapshot: PlaybackSnapshot, session: JellyfinSession) {
        let metadata = encodeRecord(snapshot.metadataFields(
            schemaVersion: Self.schemaVersion,
            baseURL: session.baseURL,
            userID: session.userId
        ))
        defaults.set(metadata, forKey: Self.playbackMetaKey)
    }

    private func clearMediaViewSnapshots() {
        for viewID in loadPersistedMediaViewIDs() {
            defaults.removeObject(forKey: mediaViewItemsKey(for: viewID, sortMode: .recent))
            defaults.removeObject(forKey: mediaViewItemsKey(for: viewID, sortMode: .title))
        }
        defaults.removeObject(forKey: Self.mediaViewMetaKey)
        defaults.removeObject(forKey: Self.mediaViewViewsKey)
        defaults.removeObject(forKey: Self.mediaViewRequestKey)
    }

    private func clearDetailSnapshots() {
        for itemId in loadPersistedDetailItemIDs() {
            defaults.removeObject(forKey: detailMetaKey(for: itemId))
            defaults.removeObject(forKey: detailEpisodesKey(for: itemId))
        }
        defaults.removeObject(forKey: Self.detailIndexKey)
        defaults.removeObject(forKey: Self.detailRequestQueueKey)
    }

    private func loadPersistedDetailItemIDs() -> [String] {
        decodeRecords(defaults.string(forKey: Self.detailIndexKey))
            .compactMap { $0.first?.nonEmpty }
    }

    private func loadPersistedMediaViewIDs() -> [String] {
        decodeRecords(defaults.string(forKey: Self.mediaViewViewsKey))
            .compactMap { $0.first?.nonEmpty }
    }

    private func loadPersistedMediaViewSummaryRecords() -> [MediaViewSummaryRecord] {
        decodeRecords(defaults.string(forKey: Self.mediaViewViewsKey))
            .compactMap { fields in
                guard fields.count >= 3 else {
                    return nil
                }
                return MediaViewSummaryRecord(
                    viewId: fields[0],
                    title: fields[1],
                    subtitle: fields[2],
                    totalCount: Int(fields[safe: 3] ?? "") ?? 0,
                    bridgeState: fields[safe: 4]?.nonEmpty
                )
            }
    }

    private func loadPersistedMediaViewItems(
        viewID: String,
        sortMode: MediaViewSortBridgeMode
    ) -> [MediaViewLibraryItem] {
        decodeRecords(defaults.string(forKey: mediaViewItemsKey(for: viewID, sortMode: sortMode)))
            .compactMap { fields in
                guard fields.count >= 3 else {
                    return nil
                }
                return MediaViewLibraryItem(
                    itemId: fields[0],
                    title: fields[1],
                    subtitle: fields[2],
                    posterUrl: fields[safe: 3]?.nonEmpty
                )
            }
    }

    private func detailMetaKey(for itemId: String) -> String {
        Self.detailMetaKeyPrefix + itemId
    }

    private func detailEpisodesKey(for itemId: String) -> String {
        Self.detailEpisodesKeyPrefix + itemId
    }

    private func mediaViewItemsKey(for viewId: String, sortMode: MediaViewSortBridgeMode) -> String {
        Self.mediaViewItemsKeyPrefix + sortMode.rawValue + "." + viewId
    }

    private func orderedUniqueItemIDs(from groups: [String]...) -> [String] {
        var seen: Set<String> = []
        var ordered: [String] = []

        for itemId in groups.joined() {
            guard !itemId.isEmpty, !seen.contains(itemId) else {
                continue
            }
            seen.insert(itemId)
            ordered.append(itemId)
        }

        return ordered
    }

    private func encodeRecords(_ records: [[String]]) -> String {
        records
            .map(encodeRecord)
            .joined(separator: Self.recordSeparator)
    }

    private func encodeRecord(_ fields: [String]) -> String {
        fields
            .map(Self.escape)
            .joined(separator: Self.fieldSeparator)
    }

    private static func escape(_ input: String) -> String {
        input
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: fieldSeparator, with: "\\u001F")
            .replacingOccurrences(of: recordSeparator, with: "\\u001E")
    }
}

private struct HomeContinueWatchingItem {
    let itemId: String
    let title: String
    let episodeTitle: String
    let progressLabel: String
    let progressPercent: Int
    let backdropUrl: String?

    var recordFields: [String] {
        [itemId, title, episodeTitle, progressLabel, String(progressPercent), backdropUrl ?? ""]
    }
}

private struct HomeNextUpItem {
    let itemId: String
    let title: String
    let episodeTitle: String
    let subtitle: String
    let thumbnailUrl: String?

    var recordFields: [String] {
        [itemId, title, episodeTitle, subtitle, thumbnailUrl ?? ""]
    }
}

private struct HomeUpdateSeed {
    let itemId: String
    let title: String
    let latestEpisodeTitle: String
    let thumbnailUrl: String?
}

private struct HomeUpdateItem {
    let itemId: String
    let title: String
    let latestEpisodeTitle: String
    let newEpisodeCount: Int
    let thumbnailUrl: String?

    var recordFields: [String] {
        [itemId, title, latestEpisodeTitle, String(newEpisodeCount), thumbnailUrl ?? ""]
    }
}

private struct HomeLatestItem {
    let itemId: String
    let title: String
    let subtitle: String
    let posterUrl: String?

    var recordFields: [String] {
        [itemId, title, subtitle, posterUrl ?? ""]
    }
}

private struct DetailSnapshot {
    let itemId: String
    let title: String
    let metaLine: String
    let synopsis: String
    let isFavorite: Bool
    let continueEpisodeId: Int
    let continueProgressLabel: String
    let continueProgressPercent: Int
    let updateCount: Int
    let episodes: [DetailEpisodeItem]
    let posterUrl: String?
    let backdropUrl: String?
    let bridgeState: String?

    init(
        itemId: String,
        title: String,
        metaLine: String,
        synopsis: String,
        isFavorite: Bool,
        continueEpisodeId: Int,
        continueProgressLabel: String,
        continueProgressPercent: Int,
        updateCount: Int,
        episodes: [DetailEpisodeItem],
        posterUrl: String? = nil,
        backdropUrl: String? = nil,
        bridgeState: String? = nil
    ) {
        self.itemId = itemId
        self.title = title
        self.metaLine = metaLine
        self.synopsis = synopsis
        self.isFavorite = isFavorite
        self.continueEpisodeId = continueEpisodeId
        self.continueProgressLabel = continueProgressLabel
        self.continueProgressPercent = continueProgressPercent
        self.updateCount = updateCount
        self.episodes = episodes
        self.posterUrl = posterUrl
        self.backdropUrl = backdropUrl
        self.bridgeState = bridgeState
    }

    func metadataFields(
        schemaVersion: Int,
        baseURL: String,
        userID: String
    ) -> [String] {
        [
            String(schemaVersion),
            baseURL,
            userID,
            itemId,
            title,
            metaLine,
            synopsis,
            isFavorite ? "1" : "0",
            String(continueEpisodeId),
            continueProgressLabel,
            String(continueProgressPercent),
            String(updateCount),
            ISO8601DateFormatter().string(from: Date()),
            bridgeState ?? "",
            posterUrl ?? "",
            backdropUrl ?? "",
        ]
    }
}

private struct DetailEpisodeItem {
    let id: Int
    let title: String
    let durationLabel: String
    let isNew: Bool
    let playbackItemId: String
    let thumbnailUrl: String?

    var recordFields: [String] {
        [String(id), title, durationLabel, isNew ? "1" : "0", playbackItemId, thumbnailUrl ?? ""]
    }
}

private struct SearchLibraryItem {
    let itemId: String
    let title: String
    let subtitle: String
    let posterUrl: String?

    var recordFields: [String] {
        [itemId, title, subtitle, posterUrl ?? ""]
    }
}

private struct MediaViewBridgeSnapshot {
    let viewId: String
    let title: String
    let subtitle: String
    let totalCount: Int
    let items: [MediaViewLibraryItem]

    var summaryRecord: MediaViewSummaryRecord {
        MediaViewSummaryRecord(
            viewId: viewId,
            title: title,
            subtitle: subtitle,
            totalCount: totalCount,
            bridgeState: nil
        )
    }
}

private struct MediaViewSummaryRecord {
    let viewId: String
    let title: String
    let subtitle: String
    let totalCount: Int
    let bridgeState: String?

    var recordFields: [String] {
        [viewId, title, subtitle, String(totalCount), bridgeState ?? ""]
    }
}

private struct MediaViewLibraryItem {
    let itemId: String
    let title: String
    let subtitle: String
    let posterUrl: String?

    var recordFields: [String] {
        [itemId, title, subtitle, posterUrl ?? ""]
    }
}

private struct FavoriteLibraryItem {
    let itemId: String
    let title: String
    let subtitle: String
    let posterUrl: String?

    var recordFields: [String] {
        [itemId, title, subtitle, posterUrl ?? ""]
    }
}

enum MediaViewSortBridgeMode: String {
    case recent
    case title

    var sortBy: String {
        switch self {
        case .recent:
            return "DateCreated"
        case .title:
            return "SortName"
        }
    }

    var sortOrder: JellyfinAPI.SortOrder {
        switch self {
        case .recent:
            return .descending
        case .title:
            return .ascending
        }
    }
}

struct PendingMediaViewRequest {
    let viewID: String
    let startIndex: Int
    let limit: Int
    let sortMode: MediaViewSortBridgeMode
}

struct PendingFavoriteMutationRequest {
    let itemID: String
    let favorite: Bool
}

private struct FavoriteMutationBridgeResult {
    let itemID: String
    let favorite: Bool
    let success: Bool
    let errorCode: String?
    let retryable: Bool
    let message: String

    func metadataFields(
        schemaVersion: Int,
        baseURL: String,
        userID: String
    ) -> [String] {
        [
            String(schemaVersion),
            baseURL,
            userID,
            itemID,
            favorite ? "1" : "0",
            success ? "success" : "failure",
            errorCode ?? "",
            retryable ? "1" : "0",
            message,
            ISO8601DateFormatter().string(from: Date()),
        ]
    }
}

struct PendingPlaybackRequest {
    let itemID: String
    let episodeID: Int
    let playbackItemID: String?
}

enum DetailBridgeFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case emptySnapshot = "empty-snapshot"
    case nativeError = "native-error"
}

enum SearchBridgeFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case emptySnapshot = "empty-snapshot"
    case nativeError = "native-error"
}

enum FavoritesBridgeFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case emptySnapshot = "empty-snapshot"
    case nativeError = "native-error"
}

enum FavoritesMutationFailureKind: String {
    case cancelled = "cancelled"
    case invalidUrl = "invalid-url"
    case tlsCertificateError = "tls-certificate-error"
    case network = "network"
    case authFailed = "auth-failed"
    case forbidden = "forbidden"
    case server = "server-error"
    case nativeError = "native-error"

    var errorCode: String {
        switch self {
        case .invalidUrl:
            return "INVALID_URL"
        case .tlsCertificateError:
            return "TLS_CERTIFICATE_ERROR"
        case .network:
            return "NETWORK_UNREACHABLE"
        case .authFailed:
            return "AUTH_FAILED"
        case .forbidden:
            return "FORBIDDEN"
        case .server:
            return "SERVER_ERROR"
        case .cancelled, .nativeError:
            return "UNKNOWN"
        }
    }

    var retryable: Bool {
        switch self {
        case .invalidUrl, .tlsCertificateError, .authFailed, .forbidden:
            return false
        case .cancelled, .network, .server, .nativeError:
            return true
        }
    }

    func userMessage(favorite: Bool) -> String {
        switch self {
        case .cancelled:
            return favorite ? "收藏请求已取消，请重试。" : "取消收藏请求已取消，请重试。"
        case .invalidUrl:
            return "服务器地址无效，请检查后重试。"
        case .tlsCertificateError:
            return "证书校验失败，请检查服务器证书。"
        case .network:
            return "网络不可用，请检查连接后重试。"
        case .authFailed:
            return "登录已失效，请重新登录后再试。"
        case .forbidden:
            return "当前账号没有收藏权限。"
        case .server:
            return "服务器暂时不可用，请稍后重试。"
        case .nativeError:
            return favorite ? "收藏失败，请稍后重试。" : "取消收藏失败，请稍后重试。"
        }
    }
}

enum MediaViewBridgeFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case emptySnapshot = "empty-snapshot"
    case nativeError = "native-error"
}

enum PlaybackBridgeFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case emptySnapshot = "empty-snapshot"
    case nativeError = "native-error"
}

enum HomeBridgeRefreshFailureKind: String {
    case network = "network"
    case cancelled = "cancelled"
    case auth = "auth"
    case server = "server"
    case nativeError = "native-error"
}

struct HomeBridgeRefreshFailure: Error {
    let kind: HomeBridgeRefreshFailureKind
    let underlyingError: Error
}

private struct PlaybackSnapshot {
    let itemId: String
    let episodeId: Int
    let requestedPlaybackItemId: String?
    let playbackItemId: String
    let showTitle: String
    let episodeTitle: String
    let streamUrl: String
    let streamTypeLabel: String
    let playSessionId: String?
    let mediaSourceId: String?
    let mediaContainer: String?
    let runtimeLabel: String
    let startPositionLabel: String
    let statusMessage: String

    func metadataFields(
        schemaVersion: Int,
        baseURL: String,
        userID: String
    ) -> [String] {
        [
            String(schemaVersion),
            baseURL,
            userID,
            itemId,
            String(episodeId),
            requestedPlaybackItemId ?? "",
            playbackItemId,
            showTitle,
            episodeTitle,
            streamUrl,
            streamTypeLabel,
            playSessionId ?? "",
            mediaSourceId ?? "",
            mediaContainer ?? "",
            runtimeLabel,
            startPositionLabel,
            statusMessage,
            ISO8601DateFormatter().string(from: Date()),
        ]
    }
}

private extension BaseItemDto {
    func toHomeContinueWatchingItem(session: JellyfinSession) -> HomeContinueWatchingItem? {
        let resolvedId = seriesID ?? id
        let resolvedTitle = seriesName ?? name
        guard let itemId = resolvedId, let title = resolvedTitle else {
            return nil
        }

        let percent = Int((userData?.playedPercentage ?? 0).rounded())
        let playbackTicks = userData?.playbackPositionTicks ?? 0
        let episodeTitle: String
        if seriesName?.isEmpty == false, let name, !name.isEmpty {
            episodeTitle = name
        } else {
            episodeTitle = "Continue playback"
        }

        return HomeContinueWatchingItem(
            itemId: itemId,
            title: title,
            episodeTitle: episodeTitle,
            progressLabel: "续播至 \(formatTicks(playbackTicks)) · \(max(0, min(percent, 100)))%",
            progressPercent: max(0, min(percent, 100)),
            backdropUrl: buildBackdropArtworkURL(session: session)
        )
    }

    func toHomeNextUpItem(session: JellyfinSession) -> HomeNextUpItem? {
        let resolvedId = seriesID ?? id
        let resolvedTitle = seriesName ?? name
        guard let itemId = resolvedId, let title = resolvedTitle else {
            return nil
        }

        let episodeTitle = buildEpisodeLabel()
        let runtime = runTimeTicks.map(formatTicks) ?? "00:00"
        return HomeNextUpItem(
            itemId: itemId,
            title: title,
            episodeTitle: episodeTitle,
            subtitle: "Next Up · \(runtime)",
            thumbnailUrl: buildThumbArtworkURL(session: session)
        )
    }

    func toHomeUpdateSeed(session: JellyfinSession) -> HomeUpdateSeed? {
        let resolvedId = seriesID ?? id
        let resolvedTitle = seriesName ?? name
        guard let itemId = resolvedId, let title = resolvedTitle else {
            return nil
        }

        return HomeUpdateSeed(
            itemId: itemId,
            title: title,
            latestEpisodeTitle: buildEpisodeLabel(),
            thumbnailUrl: buildThumbArtworkURL(session: session)
        )
    }

    func toHomeLatestItem(session: JellyfinSession) -> HomeLatestItem? {
        guard let itemId = id else {
            return nil
        }
        let title = name ?? seriesName ?? "Untitled"
        let typeLabel: String
        switch type {
        case .episode:
            typeLabel = "剧集"
        case .movie:
            typeLabel = "电影"
        case .series:
            typeLabel = "剧集合集"
        default:
            typeLabel = "媒体"
        }
        let subtitle = ["最新入库", typeLabel, productionYear.map(String.init)]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")

        return HomeLatestItem(
            itemId: itemId,
            title: title,
            subtitle: subtitle,
            posterUrl: buildPrimaryArtworkURL(session: session)
        )
    }

    func toFavoriteLibraryItem(session: JellyfinSession) -> FavoriteLibraryItem? {
        guard let itemId = id?.nonEmpty else {
            return nil
        }
        let title = name?.nonEmpty ?? seriesName?.nonEmpty ?? "Untitled"
        let typeLabel: String
        switch type {
        case .series:
            typeLabel = "剧集"
        case .movie:
            typeLabel = "电影"
        case .episode:
            typeLabel = "单集"
        default:
            typeLabel = "媒体"
        }
        let subtitle = ["收藏", typeLabel, productionYear.map(String.init)]
            .compactMap { $0 }
            .joined(separator: " · ")

        return FavoriteLibraryItem(
            itemId: itemId,
            title: title,
            subtitle: subtitle.isEmpty ? "Jellyfin 收藏" : subtitle,
            posterUrl: buildPrimaryArtworkURL(session: session)
        )
    }

    func toMediaViewLibraryItem(session: JellyfinSession) -> MediaViewLibraryItem? {
        guard let itemId = id?.nonEmpty else {
            return nil
        }

        let title = name?.nonEmpty ?? seriesName?.nonEmpty ?? "Untitled"
        let typeLabel: String
        switch type {
        case .series:
            typeLabel = "剧集"
        case .movie:
            typeLabel = "电影"
        case .episode:
            typeLabel = "单集"
        default:
            typeLabel = "媒体"
        }

        let runtime = formatRuntime(runTimeTicks).nonEmptyNonPlaceholderRuntime
        let subtitle = [
            typeLabel,
            seriesName?.nonEmpty,
            type == .episode ? buildEpisodeLabel().nonEmpty : nil,
            productionYear.map(String.init),
            runtime,
        ]
            .compactMap { $0 }
            .joined(separator: " · ")

        return MediaViewLibraryItem(
            itemId: itemId,
            title: title,
            subtitle: subtitle.isEmpty ? "Jellyfin 媒体" : subtitle,
            posterUrl: buildPrimaryArtworkURL(session: session)
        )
    }

    func toDetailEpisodeItem(displayIndex: Int, session: JellyfinSession) -> DetailEpisodeItem {
        let title = buildEpisodeDisplayTitle()
        return DetailEpisodeItem(
            id: displayIndex,
            title: title,
            durationLabel: formatRuntime(runTimeTicks),
            isNew: !(userData?.isPlayed ?? false),
            playbackItemId: id ?? UUID().uuidString,
            thumbnailUrl: buildThumbArtworkURL(session: session)
        )
    }

    func toDetailMetaLine(_ episodes: [BaseItemDto]) -> String {
        let typeLabel: String
        switch type {
        case .series:
            typeLabel = "剧集"
        case .season:
            typeLabel = "季"
        case .episode:
            typeLabel = "单集"
        case .movie:
            typeLabel = "电影"
        default:
            typeLabel = "媒体"
        }

        let runtime = formatRuntime(runTimeTicks).nonEmptyNonPlaceholderRuntime
        let seasonCount = Set(episodes.compactMap { $0.parentIndexNumber }).count
        let seriesInfo: String?
        if !episodes.isEmpty {
            if seasonCount > 0 {
                seriesInfo = "\(seasonCount)季 · \(episodes.count)集"
            } else {
                seriesInfo = "\(episodes.count)集"
            }
        } else {
            seriesInfo = nil
        }

        return [typeLabel, productionYear.map(String.init), runtime, seriesInfo, "Jellyfin"]
            .compactMap { $0?.nonEmpty }
            .joined(separator: " · ")
    }

    private func buildEpisodeLabel() -> String {
        var parts: [String] = []
        if let parentIndexNumber {
            parts.append("S\(parentIndexNumber)")
        }
        if let indexNumber {
            parts.append("E\(indexNumber)")
        }
        if let name, !name.isEmpty {
            parts.append(name)
        }
        return parts.joined(separator: " ")
    }

    private func buildEpisodeDisplayTitle() -> String {
        let title = name?.nonEmpty ?? "未命名剧集"
        var prefixParts: [String] = []
        if let parentIndexNumber {
            prefixParts.append("S\(parentIndexNumber)")
        }
        if let indexNumber {
            prefixParts.append("E\(indexNumber)")
        }
        let prefix = prefixParts.joined(separator: " ")
        return prefix.isEmpty ? title : "\(prefix) · \(title)"
    }

    func buildPrimaryArtworkURL(session: JellyfinSession) -> String? {
        buildArtworkURL(
            session: session,
            itemID: id,
            imageType: .primary,
            tag: imageTags?[ImageType.primary.rawValue],
            fillWidth: 540,
            fillHeight: 810
        )
            ?? buildArtworkURL(
                session: session,
                itemID: parentPrimaryImageItemID,
                imageType: .primary,
                tag: parentPrimaryImageTag,
                fillWidth: 540,
                fillHeight: 810
            )
            ?? buildArtworkURL(
                session: session,
                itemID: seriesID,
                imageType: .primary,
                tag: seriesPrimaryImageTag,
                fillWidth: 540,
                fillHeight: 810
            )
    }

    func buildThumbArtworkURL(session: JellyfinSession) -> String? {
        buildArtworkURL(
            session: session,
            itemID: id,
            imageType: .thumb,
            tag: imageTags?[ImageType.thumb.rawValue],
            fillWidth: 640,
            fillHeight: 360
        )
            ?? buildArtworkURL(
                session: session,
                itemID: parentThumbItemID,
                imageType: .thumb,
                tag: parentThumbImageTag,
                fillWidth: 640,
                fillHeight: 360
            )
            ?? buildArtworkURL(
                session: session,
                itemID: seriesID,
                imageType: .thumb,
                tag: seriesThumbImageTag,
                fillWidth: 640,
                fillHeight: 360
            )
            ?? buildPrimaryArtworkURL(session: session)
    }

    func buildBackdropArtworkURL(session: JellyfinSession) -> String? {
        buildArtworkURL(
            session: session,
            itemID: id,
            imageType: .backdrop,
            tag: backdropImageTags?.first,
            index: 0,
            fillWidth: 960,
            fillHeight: 540
        )
            ?? buildArtworkURL(
                session: session,
                itemID: parentBackdropItemID,
                imageType: .backdrop,
                tag: parentBackdropImageTags?.first,
                index: 0,
                fillWidth: 960,
                fillHeight: 540
            )
            ?? buildThumbArtworkURL(session: session)
    }
}

private extension SearchHint {
    func toSearchLibraryItem(session: JellyfinSession) -> SearchLibraryItem? {
        let resolvedItemID = id ?? itemID
        guard let itemId = resolvedItemID, let title = (name ?? series ?? album)?.nonEmpty else {
            return nil
        }

        let typeLabel: String?
        switch type {
        case .series:
            typeLabel = "剧集"
        case .movie:
            typeLabel = "电影"
        case .episode:
            typeLabel = "单集"
        case .audio:
            typeLabel = "音频"
        default:
            typeLabel = type?.rawValue
        }

        let runtime = runTimeTicks.map(formatRuntime)
        let subtitle = [
            typeLabel,
            productionYear.map(String.init),
            series?.nonEmpty,
            runtime?.nonEmptyNonPlaceholderRuntime,
            matchedTerm?.nonEmpty.map { "匹配 \($0)" },
        ]
            .compactMap { $0 }
            .joined(separator: " · ")

        return SearchLibraryItem(
            itemId: itemId,
            title: title,
            subtitle: subtitle.isEmpty ? "Jellyfin 搜索结果" : subtitle,
            posterUrl: buildArtworkURL(
                session: session,
                itemID: resolvedItemID,
                imageType: .primary,
                tag: primaryImageTag,
                fillWidth: 540,
                fillHeight: 810
            )
                ?? buildArtworkURL(
                    session: session,
                    itemID: thumbImageItemID ?? resolvedItemID,
                    imageType: .thumb,
                    tag: thumbImageTag,
                    fillWidth: 640,
                    fillHeight: 360
                )
                ?? buildArtworkURL(
                    session: session,
                    itemID: backdropImageItemID ?? resolvedItemID,
                    imageType: .backdrop,
                    tag: backdropImageTag,
                    index: 0,
                    fillWidth: 960,
                    fillHeight: 540
                )
        )
    }
}

private func buildArtworkURL(
    session: JellyfinSession,
    itemID: String?,
    imageType: ImageType,
    tag: String? = nil,
    index: Int? = nil,
    fillWidth: Int? = nil,
    fillHeight: Int? = nil
) -> String? {
    let normalizedItemID = itemID?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    let normalizedToken = session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !normalizedItemID.isEmpty, !normalizedToken.isEmpty else {
        return nil
    }
    guard let baseURL = URL(string: session.baseURL.trimmingCharacters(in: .whitespacesAndNewlines)) else {
        return nil
    }

    var imageURL = baseURL
        .appendingPathComponent("Items")
        .appendingPathComponent(normalizedItemID)
        .appendingPathComponent("Images")
        .appendingPathComponent(imageType.rawValue)
    if let index {
        imageURL = imageURL.appendingPathComponent(String(index))
    }

    var queryItems = [URLQueryItem(name: "api_key", value: normalizedToken)]
    if let normalizedTag = tag?.trimmingCharacters(in: .whitespacesAndNewlines), !normalizedTag.isEmpty {
        queryItems.append(URLQueryItem(name: "tag", value: normalizedTag))
    }
    if let fillWidth {
        queryItems.append(URLQueryItem(name: "fillWidth", value: String(max(1, fillWidth))))
    }
    if let fillHeight {
        queryItems.append(URLQueryItem(name: "fillHeight", value: String(max(1, fillHeight))))
    }
    queryItems.append(URLQueryItem(name: "quality", value: "90"))

    var components = URLComponents(url: imageURL, resolvingAgainstBaseURL: false)
    components?.queryItems = queryItems
    return components?.url?.absoluteString
}

private func selectPlaybackEpisode(from episodes: [BaseItemDto], episodeID: Int) -> BaseItemDto? {
    guard !episodes.isEmpty else {
        return nil
    }

    let normalizedEpisodeID = max(1, episodeID)
    let indexedMatch = episodes[safe: normalizedEpisodeID - 1]
    if let indexedMatch {
        return indexedMatch
    }

    return episodes.first(where: { episode in
        let percent = episode.userData?.playedPercentage ?? 0
        return percent > 0 && percent < 100
    }) ?? episodes.first
}

private func selectPlaybackMediaSource(from sources: [MediaSourceInfo]?) -> MediaSourceInfo? {
    let candidates = sources ?? []
    return candidates.first(where: { $0.isSupportsDirectPlay == true })
        ?? candidates.first(where: { $0.isSupportsDirectStream == true })
        ?? candidates.first
}

private func buildPlaybackEpisodeTitle(_ item: BaseItemDto, fallbackEpisodeID: Int) -> String {
    if item.type == .episode {
        return buildEpisodeDisplayTitle(for: item)
    }
    return item.name?.nonEmpty ?? "第 \(max(1, fallbackEpisodeID)) 集"
}

private func buildEpisodeDisplayTitle(for item: BaseItemDto) -> String {
    let title = item.name?.nonEmpty ?? "未命名剧集"
    var prefixParts: [String] = []
    if let parentIndexNumber = item.parentIndexNumber {
        prefixParts.append("S\(parentIndexNumber)")
    }
    if let indexNumber = item.indexNumber {
        prefixParts.append("E\(indexNumber)")
    }
    let prefix = prefixParts.joined(separator: " ")
    return prefix.isEmpty ? title : "\(prefix) · \(title)"
}

private func buildPlaybackStreamTypeLabel(_ mediaSource: MediaSourceInfo?) -> String {
    guard let mediaSource else {
        return "原生预备中"
    }

    if mediaSource.transcodingURL?.isEmpty == false || mediaSource.isSupportsTranscoding == true {
        return "转码预备"
    }
    if mediaSource.isSupportsDirectPlay == true {
        return "直连预备"
    }
    if mediaSource.isSupportsDirectStream == true {
        return "直出预备"
    }
    return "原生预备"
}

private func buildMediaViewSubtitle(view: BaseItemDto, itemCount: Int) -> String {
    let typeLabel: String
    switch view.type {
    case .series:
        typeLabel = "剧集分区"
    case .movie:
        typeLabel = "电影分区"
    default:
        typeLabel = "媒体分区"
    }

    let countLabel = itemCount > 0 ? "\(itemCount) 项" : "空分区"
    return [typeLabel, countLabel]
        .joined(separator: " · ")
}

private func buildPlaybackBridgeHandle(
    playbackItemID: String,
    playSessionID: String?,
    mediaSourceID: String?
) -> String {
    var components = URLComponents()
    components.scheme = "mafei-ios-native"
    components.host = "playback"
    components.path = "/\(playbackItemID)"
    components.queryItems = [
        URLQueryItem(name: "playSessionId", value: playSessionID?.nonEmpty),
        URLQueryItem(name: "mediaSourceId", value: mediaSourceID?.nonEmpty),
    ].filter { $0.value != nil }
    return components.string ?? "mafei-ios-native://playback/\(playbackItemID)"
}

private func formatTicks(_ ticks: Int) -> String {
    guard ticks > 0 else {
        return "00:00"
    }

    let totalSeconds = ticks / 10_000_000
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60

    if hours > 0 {
        return "\(hours):\(pad2(minutes)):\(pad2(seconds))"
    } else {
        return "\(pad2(minutes)):\(pad2(seconds))"
    }
}

private func pad2(_ value: Int) -> String {
    value < 10 ? "0\(value)" : "\(value)"
}

private func formatRuntime(_ ticks: Int?) -> String {
    guard let ticks, ticks > 0 else {
        return "--"
    }

    let totalSeconds = ticks / 10_000_000
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    return hours > 0 ? "\(hours)h\(minutes)m" : "\(minutes)m"
}

private func buildContinueLabel(source: BaseItemDto, fallback: String) -> String {
    let ticks = source.userData?.playbackPositionTicks ?? 0
    let percent = Int((source.userData?.playedPercentage ?? 0).rounded()).clamped(to: 0 ... 100)
    if ticks <= 0 && percent <= 0 {
        return (source.name?.nonEmpty).map { "\($0) · 未开始" } ?? fallback
    }

    let title = source.name?.nonEmpty ?? "当前内容"
    return "\(title) · \(formatTicks(ticks)) · \(percent)%"
}

private func decodeRecords(_ raw: String?) -> [[String]] {
    let payload = raw ?? ""
    guard !payload.isEmpty else {
        return []
    }

    return payload
        .split(separator: Character(KMPHomeBridgeStore.recordSeparator), omittingEmptySubsequences: true)
        .map { record in
            record
                .split(separator: Character(KMPHomeBridgeStore.fieldSeparator), omittingEmptySubsequences: false)
                .map { field in
                    String(field)
                        .replacingOccurrences(of: "\\u001F", with: KMPHomeBridgeStore.fieldSeparator)
                        .replacingOccurrences(of: "\\u001E", with: KMPHomeBridgeStore.recordSeparator)
                        .replacingOccurrences(of: "\\\\", with: "\\")
                }
        }
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }

    var nonEmptyNonPlaceholderRuntime: String? {
        (isEmpty || self == "--") ? nil : self
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int {
        Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private func currentEpochMillis() -> Int64 {
    Int64(Date().timeIntervalSince1970 * 1000)
}
