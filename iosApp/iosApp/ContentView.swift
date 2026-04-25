import UIKit
import SwiftUI
import ObjectiveC.runtime
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let session: JellyfinSession?
    let rememberSession: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        makeComposeRootViewController(session: session, rememberSession: rememberSession)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private func makeComposeRootViewController(
    session: JellyfinSession?,
    rememberSession: Bool
) -> UIViewController {
    guard
        let entryClass = NSClassFromString("ComposeAppMainViewControllerKt")
    else {
        assertionFailure("ComposeAppMainViewControllerKt is not available in ComposeApp.framework")
        return UIViewController()
    }

    if let session,
       let bridgedController = invokeBridgeEntryPoint(
        on: entryClass,
        session: session,
        rememberSession: rememberSession
       ) {
        return bridgedController
    }

    if let legacyController = invokeLegacyEntryPoint(on: entryClass) {
        return legacyController
    }

    assertionFailure("ComposeApp MainViewController entry points are unavailable")
    return UIViewController()
}

private func invokeBridgeEntryPoint(
    on entryClass: AnyClass,
    session: JellyfinSession,
    rememberSession: Bool
) -> UIViewController? {
    let selector = NSSelectorFromString(
        "MainViewControllerWithBridgeBaseUrl:serverId:username:userId:accessToken:rememberSession:savedAtEpochMillis:"
    )

    guard
        let method = class_getClassMethod(entryClass, selector)
    else {
        return nil
    }

    typealias BridgeEntryPoint = @convention(c) (
        AnyClass,
        Selector,
        NSString?,
        NSString?,
        NSString?,
        NSString?,
        NSString?,
        Bool,
        Int64
    ) -> UIViewController

    let implementation = method_getImplementation(method)
    let function = unsafeBitCast(implementation, to: BridgeEntryPoint.self)
    let savedAtMillis = Int64(session.savedAt.timeIntervalSince1970 * 1000)

    return function(
        entryClass,
        selector,
        session.baseURL as NSString,
        session.serverId as NSString?,
        session.username as NSString,
        session.userId as NSString,
        session.accessToken as NSString,
        rememberSession,
        savedAtMillis
    )
}

private func invokeLegacyEntryPoint(on entryClass: AnyClass) -> UIViewController? {
    let selector = NSSelectorFromString("MainViewController")
    guard let method = class_getClassMethod(entryClass, selector) else {
        return nil
    }

    typealias LegacyEntryPoint = @convention(c) (AnyClass, Selector) -> UIViewController
    let implementation = method_getImplementation(method)
    let function = unsafeBitCast(implementation, to: LegacyEntryPoint.self)
    return function(entryClass, selector)
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var sessionStore = JellyfinSessionStore()
    @StateObject private var homeSnapshotRefreshCoordinator = HomeSnapshotRefreshCoordinator()
    private let authService = JellyfinAuthService()
    private let homeBridgeStore = KMPHomeBridgeStore()
    @State private var configuredBaseURL = ""
    @State private var containerState: AppContainerState = .restoring
    @State private var bridgeStatusMessage: String?
    @State private var isDiagnosticsPresented = false
    @State private var bridgeContextSnapshot: KMPBridgeContext?
    @State private var bridgeLastSyncResult: BridgeSyncResult?
    @State private var secureRuntimeSnapshot: SecureAuthRuntimeSnapshot?
    @State private var homeSnapshotStatus: HomeBridgeSnapshotStatus?
    @State private var loginRecoveryContext: LoginRecoveryContext?
    @State private var isRefreshingHomeSnapshot = false

    var body: some View {
        Group {
            switch containerState {
            case .restoring:
                restoringContainerView()
            case let .unauthenticated(step):
                unauthenticatedView(for: step)
            case let .authenticated(session):
                composeShell(session: session)
            }
        }
        .task {
            restoreInitialContainerState()
        }
        .onChange(of: scenePhase) { newPhase in
            guard newPhase == .active else {
                return
            }
            guard case let .authenticated(session) = containerState else {
                return
            }
            refreshBridgeContext(reason: .foreground)
            refreshHomeBridgeSnapshot(for: session, reason: .foreground)
        }
        .overlay(alignment: .top) {
            if let bridgeStatusMessage {
                statusBanner(bridgeStatusMessage)
                    .padding(.top, 10)
            }
        }
        .sheet(isPresented: $isDiagnosticsPresented) {
            diagnosticsSheet()
        }
    }

    private func restoringContainerView() -> some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(.systemBackground),
                    Color(.secondarySystemBackground),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 18) {
                HStack(spacing: 6) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.caption.weight(.semibold))
                    Text("MaFei 媒体客户端")
                        .font(.caption.weight(.semibold))
                }
                .foregroundStyle(.tint)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.accentColor.opacity(0.12), in: Capsule())

                VStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(Color.accentColor.opacity(0.12))
                            .frame(width: 72, height: 72)
                        Image(systemName: "play.tv.fill")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.tint)
                    }
                    Text("MaFei")
                        .font(.title3.weight(.semibold))
                    Text("正在恢复你的观影空间")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 10) {
                    startupStepRow(icon: "checkmark.shield", text: "校验登录状态")
                    startupStepRow(icon: "server.rack", text: "连接最近服务器")
                    startupStepRow(icon: "sparkles.rectangle.stack", text: "准备首页内容")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(
                    Color(.secondarySystemBackground),
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                )

                ProgressView("请稍候…")
                    .font(.footnote)
                    .tint(.accentColor)
            }
            .padding(22)
            .frame(maxWidth: 360)
        }
    }

    private func startupStepRow(icon: String, text: String) -> some View {
        HStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.12))
                    .frame(width: 20, height: 20)
                Image(systemName: icon)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.tint)
            }
            Text(text)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func unauthenticatedView(for step: AuthStep) -> some View {
        VStack(spacing: 0) {
            switch step {
            case .serverConfig:
                ServerConfigView(
                    authService: authService,
                    initialBaseURL: configuredBaseURL,
                    onContinue: { testedBaseURL in
                        configuredBaseURL = testedBaseURL
                        containerState = .unauthenticated(.login)
                    }
                )
            case .login:
                LoginView(
                    baseURL: configuredBaseURL,
                    authService: authService,
                    recoveryContext: loginRecoveryContext,
                    runtimeSnapshot: secureRuntimeSnapshot,
                    onBack: {
                        containerState = .unauthenticated(.serverConfig)
                    },
                    onAuthenticated: { session, shouldRemember in
                        completeAuthentication(with: session, persist: shouldRemember)
                    }
                )
            }

            if let loginRecoveryContext {
                unauthenticatedRecoveryFooter(loginRecoveryContext)
            }
        }
    }

    private func unauthenticatedRecoveryFooter(_ context: LoginRecoveryContext) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: recoverySymbol(for: context))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(recoveryTint(for: context))
                Text("登录小贴士")
                    .font(.caption)
                    .fontWeight(.semibold)
            }
            Text(primaryRecoveryHint(for: context))
                .font(.caption)
                .foregroundColor(.secondary)
            if let runtime = secureRuntimeSnapshot, runtime.issue != .healthy {
                Text("系统已为你保留安全策略，重新登录后会自动修复。")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            if let previousServer = context.previousServer, !previousServer.isEmpty {
                Text("上次连接：\(previousServer)")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            Button("查看诊断详情") {
                presentDiagnostics()
            }
            .font(.caption2)
            .buttonStyle(.borderless)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    private func primaryRecoveryHint(for context: LoginRecoveryContext) -> String {
        switch context.reasonCode {
        case "STARTUP_RESTORE_INCOMPLETE":
            return "检测到你之前登录过，重新登录即可继续。"
        case "STARTUP_NOT_AUTHENTICATED":
            return "首次使用请先连接服务器并登录。"
        case "SECURE_TOKEN_UNAVAILABLE":
            return "出于安全原因，登录已过期，请重新登录。"
        case "REMEMBER_DATA_INCOMPLETE":
            return "自动登录信息不完整，请重新登录。"
        case "BRIDGE_CONTEXT_EXPIRED", "BRIDGE_UNAUTHENTICATED":
            return "登录状态已过期，请重新登录。"
        case "USER_SIGN_OUT":
            return "你已退出当前账号。"
        default:
            return context.reasonMessage
        }
    }

    private func recoverySymbol(for context: LoginRecoveryContext) -> String {
        switch context.reasonCode {
        case "USER_SIGN_OUT":
            return "person.crop.circle.badge.checkmark"
        case "STARTUP_NOT_AUTHENTICATED":
            return "sparkles"
        case "STARTUP_RESTORE_INCOMPLETE":
            return "clock.arrow.trianglehead.counterclockwise.rotate.90"
        default:
            return "exclamationmark.shield"
        }
    }

    private func recoveryTint(for context: LoginRecoveryContext) -> Color {
        switch context.reasonCode {
        case "USER_SIGN_OUT", "STARTUP_NOT_AUTHENTICATED":
            return .accentColor
        case "STARTUP_RESTORE_INCOMPLETE":
            return .orange
        default:
            return .red
        }
    }

    private func composeShell(session: JellyfinSession) -> some View {
        // KMP side can read `KMPBridgeContextStore.bridgeContextKey` from UserDefaults
        // to bootstrap non-sensitive user context before shared auth lands.
        VStack(spacing: 0) {
            authenticatedTopBar(session: session)
            ComposeView(session: session, rememberSession: sessionStore.rememberSession)
                .ignoresSafeArea(edges: .bottom)
        }
        .task(id: detailRequestMonitorID(for: session)) {
            await monitorNativeBridgeRequests(for: session)
        }
    }

    private func authenticatedTopBar(session: JellyfinSession) -> some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                accountAvatarView(session: session)
                VStack(alignment: .leading, spacing: 2) {
                    Text("MaFei")
                        .font(.headline)
                    Label(session.username, systemImage: "person.crop.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if isRefreshingHomeSnapshot {
                ProgressView()
                    .controlSize(.small)
            }
            Menu {
                Section("内容") {
                    Button {
                        refreshHomeBridgeSnapshot(for: session, reason: .manual)
                    } label: {
                        Label(
                            isRefreshingHomeSnapshot ? "正在刷新首页…" : "刷新首页内容",
                            systemImage: "arrow.clockwise"
                        )
                    }
                    .disabled(isRefreshingHomeSnapshot)
                }
                Section("账号") {
                    Button {
                        presentDiagnostics()
                    } label: {
                        Label("账号与服务器信息", systemImage: "person.crop.rectangle.stack")
                    }
                }
                Button {
                    presentDiagnostics()
                } label: {
                    Label("帮助与诊断", systemImage: "stethoscope")
                }
                Button(role: .destructive) {
                    signOut(from: session)
                } label: {
                    Label("退出当前账号", systemImage: "rectangle.portrait.and.arrow.right")
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(Color(.secondarySystemBackground))
                        .frame(width: 30, height: 30)
                    Image(systemName: "ellipsis")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
        .overlay(alignment: .bottom) {
            Divider()
        }
    }

    @ViewBuilder
    private func accountAvatarView(session: JellyfinSession) -> some View {
        if let avatarURL = userAvatarURL(for: session) {
            AsyncImage(url: avatarURL, transaction: Transaction(animation: .easeInOut(duration: 0.2))) { phase in
                switch phase {
                case let .success(image):
                    image
                        .resizable()
                        .scaledToFill()
                case .failure:
                    avatarPlaceholderIcon(systemName: "person.crop.circle.badge.exclamationmark")
                case .empty:
                    avatarPlaceholderIcon(systemName: "person.crop.circle.fill")
                @unknown default:
                    avatarPlaceholderIcon(systemName: "person.crop.circle.fill")
                }
            }
            .frame(width: 28, height: 28)
            .clipShape(Circle())
            .overlay(
                Circle()
                    .stroke(Color.primary.opacity(0.14), lineWidth: 0.5)
            )
        } else {
            avatarPlaceholderIcon(systemName: "person.crop.circle.fill")
                .frame(width: 28, height: 28)
        }
    }

    private func avatarPlaceholderIcon(systemName: String) -> some View {
        ZStack {
            Circle()
                .fill(Color.accentColor.opacity(0.14))
            Image(systemName: systemName)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tint)
        }
    }

    private func userAvatarURL(for session: JellyfinSession) -> URL? {
        guard
            let baseURL = URL(string: session.baseURL.trimmingCharacters(in: .whitespacesAndNewlines)),
            !session.userId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            !session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return nil
        }

        let imageURL = baseURL
            .appendingPathComponent("Users")
            .appendingPathComponent(session.userId)
            .appendingPathComponent("Images")
            .appendingPathComponent("Primary")
        var components = URLComponents(url: imageURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "fillWidth", value: "96"),
            URLQueryItem(name: "fillHeight", value: "96"),
            URLQueryItem(name: "quality", value: "90"),
            URLQueryItem(name: "api_key", value: session.accessToken),
        ]
        return components?.url
    }

    private func restoreInitialContainerState() {
        sessionStore.load()
        let runtimeSnapshot = sessionStore.secureAuthRuntimeSnapshot()
        secureRuntimeSnapshot = runtimeSnapshot
        refreshHomeSnapshotStatus(for: sessionStore.session)
        if let savedSession = sessionStore.session {
            transitionToAuthenticated(with: savedSession, refreshReason: .startup)
            return
        }

        let startupMessage: String
        let startupAction: String
        let startupCode: String
        if runtimeSnapshot.hasPersistedSessionMetadata || runtimeSnapshot.hasPersistedSecureToken {
            startupCode = "STARTUP_RESTORE_INCOMPLETE"
            startupMessage = "检测到历史登录信息，但未能完整恢复会话。"
            startupAction = "请重新登录以恢复会话。"
        } else {
            startupCode = "STARTUP_NOT_AUTHENTICATED"
            startupMessage = "当前设备没有可用登录会话。"
            startupAction = "请登录后继续。"
        }

        transitionToUnauthenticated(
            preferredBaseURL: configuredBaseURL,
            reasonCode: startupCode,
            reasonMessage: startupMessage,
            recommendedAction: startupAction
        )
    }

    private func completeAuthentication(with session: JellyfinSession, persist: Bool) {
        configuredBaseURL = session.baseURL
        sessionStore.activate(session, persist: persist)
        loginRecoveryContext = nil
        transitionToAuthenticated(with: session, refreshReason: .postAuthentication)
    }

    private func transitionToAuthenticated(with session: JellyfinSession, refreshReason: BridgeRefreshReason) {
        invalidateHomeSnapshotRefresh()
        configuredBaseURL = session.baseURL
        containerState = .authenticated(session)
        refreshBridgeContext(reason: refreshReason)
        runRuntimeHealthCheck(showSuccessBanner: false)
        refreshHomeSnapshotStatus(for: session)
        refreshHomeBridgeSnapshot(for: session, reason: .bootstrap)
    }

    private func transitionToUnauthenticated(
        preferredBaseURL: String?,
        reasonCode: String,
        reasonMessage: String,
        recommendedAction: String,
        previousSession: JellyfinSession? = nil,
        previousRememberSession: Bool? = nil
    ) {
        invalidateHomeSnapshotRefresh()
        if let preferredBaseURL {
            configuredBaseURL = preferredBaseURL
        }
        homeBridgeStore.clear()
        containerState = configuredBaseURL.isEmpty ? .unauthenticated(.serverConfig) : .unauthenticated(.login)
        bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
        refreshRuntimeSnapshot()
        refreshHomeSnapshotStatus(for: nil)
        if shouldShowRecoveryContext(for: reasonCode) {
            loginRecoveryContext = LoginRecoveryContext(
                reasonCode: reasonCode,
                reasonMessage: reasonMessage,
                recommendedAction: recommendedAction,
                previousServer: previousSession?.baseURL ?? configuredBaseURL,
                previousUsername: previousSession?.username,
                previousRememberSession: previousRememberSession
            )
        } else {
            loginRecoveryContext = nil
        }
    }

    private func shouldShowRecoveryContext(for reasonCode: String) -> Bool {
        switch reasonCode {
        case "STARTUP_NOT_AUTHENTICATED", "USER_SIGN_OUT":
            return false
        default:
            return true
        }
    }

    private func signOut(
        from session: JellyfinSession,
        reasonCode: String = "USER_SIGN_OUT",
        reasonMessage: String = "你已退出当前账号。",
        recommendedAction: String = "需要时可重新登录。"
    ) {
        let previousRememberSession = sessionStore.rememberSession
        sessionStore.clear()
        transitionToUnauthenticated(
            preferredBaseURL: session.baseURL,
            reasonCode: reasonCode,
            reasonMessage: reasonMessage,
            recommendedAction: recommendedAction,
            previousSession: session,
            previousRememberSession: previousRememberSession
        )
    }

    private func refreshBridgeContext(reason: BridgeRefreshReason) {
        let syncResult = sessionStore.ensureBridgeContext()
        bridgeLastSyncResult = syncResult
        bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
        refreshRuntimeSnapshot()

        switch syncResult {
        case .upToDate:
            if reason == .manual {
                showBridgeStatus("会话状态正常。")
            }
        case .repaired:
            if reason == .manual {
                showBridgeStatus("会话状态已更新。")
            }
        case .clearedUnauthenticated:
            if case let .authenticated(currentSession) = containerState {
                sessionStore.repairBridgeFromActiveSession(currentSession)
                let repairedResult = sessionStore.ensureBridgeContext()
                bridgeLastSyncResult = repairedResult
                bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
                if repairedResult == .upToDate {
                    runRuntimeHealthCheck(showSuccessBanner: false)
                    return
                }
                signOut(
                    from: currentSession,
                    reasonCode: "BRIDGE_CONTEXT_EXPIRED",
                    reasonMessage: "会话已过期且无法自动恢复。",
                    recommendedAction: "请重新登录后继续。"
                )
                showBridgeStatus("会话已失效，请重新登录。")
            }
        }
    }

    private func refreshHomeBridgeSnapshot(
        for session: JellyfinSession,
        reason: HomeSnapshotRefreshReason = .bootstrap
    ) {
        let sessionKey = sessionIdentity(session)
        if homeSnapshotRefreshCoordinator.isRefreshing(for: sessionKey) {
            if reason == .manual {
                showBridgeStatus("首页刷新进行中，请稍候。")
            }
            return
        }

        let refreshToken = homeSnapshotRefreshCoordinator.beginRefresh(for: sessionKey)
        isRefreshingHomeSnapshot = true

        let task = Task {
            do {
                maFeiLog(
                    .info,
                    tag: "ContentView",
                    "bridge refresh requested baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId))"
                )
                try await homeBridgeStore.refresh(
                    for: session,
                    shouldContinue: {
                        !Task.isCancelled &&
                            homeSnapshotRefreshCoordinator.shouldContinue(
                                token: refreshToken,
                                sessionKey: sessionKey
                            )
                    },
                    commit: { operation in
                        homeSnapshotRefreshCoordinator.performIfCurrent(
                            token: refreshToken,
                            sessionKey: sessionKey,
                            operation: operation
                        )
                    }
                )
                guard homeSnapshotRefreshCoordinator.shouldContinue(
                    token: refreshToken,
                    sessionKey: sessionKey
                ) else {
                    return
                }
                await MainActor.run {
                    refreshHomeSnapshotStatus(for: session)
                    if reason == .manual {
                        showBridgeStatus(homeBridgeRefreshSuccessMessage(snapshot: homeSnapshotStatus))
                    }
                }
                maFeiLog(
                    .info,
                    tag: "ContentView",
                    "bridge refresh finished baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId))"
                )
            } catch is CancellationError {
                maFeiLog(
                    .debug,
                    tag: "ContentView",
                    "bridge refresh cancelled baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId))"
                )
            } catch {
                guard homeSnapshotRefreshCoordinator.shouldContinue(
                    token: refreshToken,
                    sessionKey: sessionKey
                ) else {
                    return
                }
                let failureKind = homeBridgeRefreshFailureKind(for: error)
                maFeiLog(
                    .warning,
                    tag: "ContentView",
                    "bridge refresh failed reason=\(failureKind.rawValue) baseUrl=\(redactBaseURL(session.baseURL)) userId=\(redactIdentifier(session.userId)) preservingCachedSnapshot=1 errorType=\(String(describing: type(of: error)))"
                )
                await MainActor.run {
                    refreshHomeSnapshotStatus(for: session)
                    if shouldPresentHomeRefreshFailureBanner(
                        for: reason,
                        snapshot: homeSnapshotStatus
                    ) {
                        showBridgeStatus(
                            homeBridgeRefreshFailureMessage(
                                for: failureKind,
                                snapshot: homeSnapshotStatus
                            )
                        )
                    }
                }
            }
            await MainActor.run {
                if homeSnapshotRefreshCoordinator.finish(
                    token: refreshToken,
                    sessionKey: sessionKey
                ) {
                    isRefreshingHomeSnapshot = false
                }
            }
        }

        homeSnapshotRefreshCoordinator.attach(
            task: task,
            token: refreshToken,
            sessionKey: sessionKey
        )
    }

    private func monitorNativeBridgeRequests(for session: JellyfinSession) async {
        while !Task.isCancelled {
            if let itemId = homeBridgeStore.consumePendingDetailRequest(for: session) {
                do {
                    maFeiLog(
                        .info,
                        tag: "ContentView",
                        "consuming detail bridge request itemId=\(redactIdentifier(itemId))"
                    )
                    let snapshotReady = try await homeBridgeStore.ensureDetailSnapshot(for: session, itemID: itemId)
                    if snapshotReady {
                        maFeiLog(
                            .info,
                            tag: "ContentView",
                            "detail bridge request completed itemId=\(redactIdentifier(itemId))"
                        )
                    } else {
                        maFeiLog(
                            .warning,
                            tag: "ContentView",
                            "detail bridge request completed without snapshot itemId=\(redactIdentifier(itemId))"
                        )
                    }
                } catch {
                    let failureKind = detailBridgeFailureKind(for: error)
                    homeBridgeStore.recordDetailFailureSnapshot(
                        for: session,
                        itemID: itemId,
                        kind: failureKind
                    )
                    maFeiLog(
                        .warning,
                        tag: "ContentView",
                        "detail bridge request failed itemId=\(redactIdentifier(itemId)) reason=\(failureKind.rawValue) errorType=\(String(describing: type(of: error)))"
                    )
                    // Detail bridge now saves an explicit failure snapshot for shared-side retry UX.
                }
                continue
            }

            if let query = homeBridgeStore.consumePendingSearchRequest(for: session) {
                do {
                    maFeiLog(
                        .info,
                        tag: "ContentView",
                        "consuming search bridge request queryLength=\(query.count) userId=\(redactIdentifier(session.userId))"
                    )
                    let snapshotReady = try await homeBridgeStore.ensureSearchSnapshot(for: session, query: query)
                    if snapshotReady {
                        maFeiLog(
                            .info,
                            tag: "ContentView",
                            "search bridge request completed queryLength=\(query.count) userId=\(redactIdentifier(session.userId))"
                        )
                    } else {
                        maFeiLog(
                            .warning,
                            tag: "ContentView",
                            "search bridge request completed without snapshot queryLength=\(query.count) userId=\(redactIdentifier(session.userId))"
                        )
                    }
                } catch {
                    let failureKind = searchBridgeFailureKind(for: error)
                    homeBridgeStore.recordSearchFailureSnapshot(
                        for: session,
                        query: query,
                        kind: failureKind
                    )
                    maFeiLog(
                        .warning,
                        tag: "ContentView",
                        "search bridge request failed queryLength=\(query.count) reason=\(failureKind.rawValue) errorType=\(String(describing: type(of: error)))"
                    )
                    // Search bridge now saves an explicit failure snapshot for shared-side retry UX.
                }
                continue
            }

            if let mediaViewRequest = homeBridgeStore.consumePendingMediaViewRequest(for: session) {
                do {
                    maFeiLog(
                        .info,
                        tag: "ContentView",
                        "consuming mediaView bridge request viewId=\(redactIdentifier(mediaViewRequest.viewID)) startIndex=\(mediaViewRequest.startIndex) limit=\(mediaViewRequest.limit) sort=\(mediaViewRequest.sortMode.rawValue)"
                    )
                    let snapshotReady = try await homeBridgeStore.ensureMediaViewSnapshot(
                        for: session,
                        request: mediaViewRequest
                    )
                    if snapshotReady {
                        maFeiLog(
                            .info,
                            tag: "ContentView",
                            "mediaView bridge request completed viewId=\(redactIdentifier(mediaViewRequest.viewID)) startIndex=\(mediaViewRequest.startIndex) limit=\(mediaViewRequest.limit)"
                        )
                    } else {
                        homeBridgeStore.recordMediaViewFailureSnapshot(
                            for: session,
                            request: mediaViewRequest,
                            kind: .emptySnapshot
                        )
                        maFeiLog(
                            .warning,
                            tag: "ContentView",
                            "mediaView bridge request completed without snapshot viewId=\(redactIdentifier(mediaViewRequest.viewID)) startIndex=\(mediaViewRequest.startIndex) limit=\(mediaViewRequest.limit) reason=empty-snapshot"
                        )
                    }
                } catch {
                    let failureKind = mediaViewBridgeFailureKind(for: error)
                    homeBridgeStore.recordMediaViewFailureSnapshot(
                        for: session,
                        request: mediaViewRequest,
                        kind: failureKind
                    )
                    maFeiLog(
                        .warning,
                        tag: "ContentView",
                        "mediaView bridge request failed viewId=\(redactIdentifier(mediaViewRequest.viewID)) startIndex=\(mediaViewRequest.startIndex) limit=\(mediaViewRequest.limit) reason=\(failureKind.rawValue) errorType=\(String(describing: type(of: error)))"
                    )
                    // MediaView bridge now saves an explicit failure snapshot for shared-side retry UX.
                }
                continue
            }

            if homeBridgeStore.consumePendingFavoritesRequest(for: session) {
                do {
                    maFeiLog(
                        .info,
                        tag: "ContentView",
                        "consuming favorites bridge request userId=\(redactIdentifier(session.userId))"
                    )
                    let snapshotReady = try await homeBridgeStore.ensureFavoritesSnapshot(for: session)
                    if snapshotReady {
                        maFeiLog(
                            .info,
                            tag: "ContentView",
                            "favorites bridge request completed userId=\(redactIdentifier(session.userId))"
                        )
                    } else {
                        maFeiLog(
                            .warning,
                            tag: "ContentView",
                            "favorites bridge request completed without snapshot userId=\(redactIdentifier(session.userId))"
                        )
                    }
                } catch {
                    let failureKind = favoritesBridgeFailureKind(for: error)
                    homeBridgeStore.recordFavoritesFailureSnapshot(
                        for: session,
                        kind: failureKind
                    )
                    maFeiLog(
                        .warning,
                        tag: "ContentView",
                        "favorites bridge request failed reason=\(failureKind.rawValue) errorType=\(String(describing: type(of: error)))"
                    )
                    // Favorites bridge now saves an explicit failure snapshot for shared-side retry UX.
                }
                continue
            }

            if let favoritesMutationRequest = homeBridgeStore.consumePendingFavoritesMutationRequest(for: session) {
                maFeiLog(
                    .info,
                    tag: "ContentView",
                    "consuming favorites mutation request itemId=\(redactIdentifier(favoritesMutationRequest.itemID)) favorite=\(favoritesMutationRequest.favorite ? "1" : "0") userId=\(redactIdentifier(session.userId))"
                )
                let isSuccess = await homeBridgeStore.performFavoritesMutation(
                    for: session,
                    itemID: favoritesMutationRequest.itemID,
                    favorite: favoritesMutationRequest.favorite
                )
                maFeiLog(
                    isSuccess ? .info : .warning,
                    tag: "ContentView",
                    "favorites mutation request completed itemId=\(redactIdentifier(favoritesMutationRequest.itemID)) favorite=\(favoritesMutationRequest.favorite ? "1" : "0") userId=\(redactIdentifier(session.userId)) success=\(isSuccess ? "1" : "0")"
                )
                continue
            }

            if let playbackRequest = homeBridgeStore.consumePendingPlaybackRequest(for: session) {
                do {
                    maFeiLog(
                        .info,
                        tag: "ContentView",
                        "consuming playback bridge request itemId=\(redactIdentifier(playbackRequest.itemID)) episodeId=\(playbackRequest.episodeID) playbackItemId=\(redactIdentifier(playbackRequest.playbackItemID))"
                    )
                    let snapshotReady = try await homeBridgeStore.ensurePlaybackSnapshot(
                        for: session,
                        itemID: playbackRequest.itemID,
                        episodeID: playbackRequest.episodeID,
                        playbackItemID: playbackRequest.playbackItemID
                    )
                    if snapshotReady {
                        maFeiLog(
                            .info,
                            tag: "ContentView",
                            "playback bridge request completed itemId=\(redactIdentifier(playbackRequest.itemID)) episodeId=\(playbackRequest.episodeID) playbackItemId=\(redactIdentifier(playbackRequest.playbackItemID))"
                        )
                    } else {
                        maFeiLog(
                            .warning,
                            tag: "ContentView",
                            "playback bridge request completed without snapshot itemId=\(redactIdentifier(playbackRequest.itemID)) episodeId=\(playbackRequest.episodeID) playbackItemId=\(redactIdentifier(playbackRequest.playbackItemID))"
                        )
                    }
                } catch {
                    let failureKind = playbackBridgeFailureKind(for: error)
                    homeBridgeStore.recordPlaybackFailureSnapshot(
                        for: session,
                        itemID: playbackRequest.itemID,
                        episodeID: playbackRequest.episodeID,
                        playbackItemID: playbackRequest.playbackItemID,
                        kind: failureKind
                    )
                    maFeiLog(
                        .warning,
                        tag: "ContentView",
                        "playback bridge request failed itemId=\(redactIdentifier(playbackRequest.itemID)) playbackItemId=\(redactIdentifier(playbackRequest.playbackItemID)) reason=\(failureKind.rawValue) errorType=\(String(describing: type(of: error)))"
                    )
                    // Playback bridge now saves an explicit failure snapshot for shared-side retry UX.
                }
                continue
            }

            try? await Task.sleep(nanoseconds: 250_000_000)
        }
    }

    private func detailRequestMonitorID(for session: JellyfinSession) -> String {
        "\(session.baseURL)|\(session.userId)|\(session.savedAt.timeIntervalSince1970)"
    }

    private func detailBridgeFailureKind(for error: Error) -> DetailBridgeFailureKind {
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func homeBridgeRefreshFailureKind(for error: Error) -> HomeBridgeRefreshFailureKind {
        if let refreshFailure = error as? HomeBridgeRefreshFailure {
            return refreshFailure.kind
        }
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func homeBridgeRefreshFailureMessage(
        for kind: HomeBridgeRefreshFailureKind,
        snapshot: HomeBridgeSnapshotStatus?
    ) -> String {
        let baseMessage: String
        switch kind {
        case .network:
            baseMessage = "首页同步失败（网络）"
        case .cancelled:
            baseMessage = "首页同步已取消"
        case .auth:
            baseMessage = "首页同步失败（鉴权）"
        case .server:
            baseMessage = "首页同步失败（服务器）"
        case .nativeError:
            baseMessage = "首页同步失败（客户端）"
        }

        guard let snapshot else {
            return "\(baseMessage)，当前没有可用缓存。"
        }

        switch snapshot.freshness {
        case .unavailable:
            return "\(baseMessage)，当前没有可用缓存。"
        case .fresh:
            return "\(baseMessage)，已保留缓存（\(homeSnapshotAgeLabel(snapshot))）。"
        case .stale:
            return "\(baseMessage)，已保留旧缓存（\(homeSnapshotAgeLabel(snapshot))）。"
        case .sessionMismatch:
            return "\(baseMessage)，缓存属于其他账号。"
        case .malformed:
            return "\(baseMessage)，缓存数据异常。"
        }
    }

    private func homeBridgeRefreshSuccessMessage(snapshot: HomeBridgeSnapshotStatus?) -> String {
        guard let snapshot else {
            return "首页缓存同步完成。"
        }
        return "首页缓存同步完成（\(homeSnapshotFreshnessLabel(snapshot.freshness))，\(homeSnapshotAgeLabel(snapshot))）。"
    }

    private func playbackBridgeFailureKind(for error: Error) -> PlaybackBridgeFailureKind {
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func searchBridgeFailureKind(for error: Error) -> SearchBridgeFailureKind {
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func favoritesBridgeFailureKind(for error: Error) -> FavoritesBridgeFailureKind {
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func mediaViewBridgeFailureKind(for error: Error) -> MediaViewBridgeFailureKind {
        if error is CancellationError {
            return .cancelled
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network
        }
        return .nativeError
    }

    private func presentDiagnostics() {
        bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
        refreshRuntimeSnapshot()
        refreshHomeSnapshotStatus(for: activeSession())
        isDiagnosticsPresented = true
    }

    private func refreshRuntimeSnapshot() {
        secureRuntimeSnapshot = sessionStore.secureAuthRuntimeSnapshot()
    }

    private func runRuntimeHealthCheck(showSuccessBanner: Bool) {
        let snapshot = sessionStore.secureAuthRuntimeSnapshot()
        secureRuntimeSnapshot = snapshot

        guard case let .authenticated(currentSession) = containerState else {
            return
        }

        if snapshot.issue != .healthy {
            maFeiLog(
                .warning,
                tag: "ContentView",
                "runtime health issue issue=\(snapshot.issue.rawValue) detail=\(snapshot.issueDetail)"
            )
        }

        guard snapshot.hasSecureAccessToken else {
            if snapshot.rememberSession,
               let recoveredSession = sessionStore.recoverSessionFromSecureStore()
            {
                containerState = .authenticated(recoveredSession)
                bridgeLastSyncResult = sessionStore.ensureBridgeContext()
                bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
                refreshRuntimeSnapshot()
                refreshHomeBridgeSnapshot(for: recoveredSession)
                return
            }
            signOut(
                from: currentSession,
                reasonCode: "SECURE_TOKEN_UNAVAILABLE",
                reasonMessage: "当前会话凭据已失效。",
                recommendedAction: "请重新登录以恢复会话。"
            )
            showBridgeStatus("登录凭据已失效，已安全退出。")
            return
        }

        if !snapshot.isRememberSessionSemanticsValid {
            if snapshot.rememberSession {
                sessionStore.save(currentSession)
                let repairedSnapshot = sessionStore.secureAuthRuntimeSnapshot()
                secureRuntimeSnapshot = repairedSnapshot
                if repairedSnapshot.isRememberSessionSemanticsValid && repairedSnapshot.hasSecureAccessToken {
                    bridgeLastSyncResult = sessionStore.ensureBridgeContext()
                    bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
                    refreshRuntimeSnapshot()
                    return
                }

                if let recoveredSession = sessionStore.recoverSessionFromSecureStore() {
                    containerState = .authenticated(recoveredSession)
                    bridgeLastSyncResult = sessionStore.ensureBridgeContext()
                    bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
                    refreshRuntimeSnapshot()
                    refreshHomeBridgeSnapshot(for: recoveredSession)
                    return
                }

                signOut(
                    from: currentSession,
                    reasonCode: "REMEMBER_DATA_INCOMPLETE",
                    reasonMessage: "记住登录数据不完整且修复失败。",
                    recommendedAction: "请重新登录后继续。"
                )
                showBridgeStatus("记住登录数据异常，请重新登录。")
                return
            }

            sessionStore.activate(currentSession, persist: false)
            bridgeLastSyncResult = sessionStore.ensureBridgeContext()
            bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
            refreshRuntimeSnapshot()
            return
        }

        if !snapshot.isBridgeConsistentWithActiveSession {
            let repairResult = sessionStore.ensureBridgeContext()
            bridgeLastSyncResult = repairResult
            bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
            refreshRuntimeSnapshot()
            switch repairResult {
            case .upToDate, .repaired:
                break
            case .clearedUnauthenticated:
                signOut(
                    from: currentSession,
                    reasonCode: "BRIDGE_UNAUTHENTICATED",
                    reasonMessage: "会话状态检查发现登录已失效。",
                    recommendedAction: "请重新登录后继续。"
                )
                showBridgeStatus("会话已失效，请重新登录。")
            }
            return
        }

        if showSuccessBanner {
            showBridgeStatus("登录状态正常。")
        }
    }

    private func showBridgeStatus(_ message: String) {
        bridgeStatusMessage = message
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if bridgeStatusMessage == message {
                bridgeStatusMessage = nil
            }
        }
    }

    private func statusBanner(_ message: String) -> some View {
        let isWarning = message.contains("失败") || message.contains("失效") || message.contains("异常")

        return HStack(spacing: 8) {
            Image(systemName: statusBannerSymbol(for: message))
                .font(.caption.weight(.semibold))
                .foregroundStyle(isWarning ? .red : .green)
            Text(message)
                .font(.footnote)
                .lineLimit(2)
        }
        .foregroundStyle(.primary)
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(
            Capsule()
                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.08), radius: 8, y: 3)
    }

    private func statusBannerSymbol(for message: String) -> String {
        if message.contains("失败") || message.contains("失效") || message.contains("异常") {
            return "exclamationmark.triangle.fill"
        }
        return "checkmark.circle.fill"
    }

    private func diagnosticsSheet() -> some View {
        NavigationView {
            Form {
                Section("Container") {
                    Text("State: \(containerStateLabel)")
                    if let bridgeLastSyncResult {
                        Text("Last bridge sync: \(syncResultLabel(bridgeLastSyncResult))")
                    }
                }

                if case let .authenticated(session) = containerState {
                    Section("Active Session") {
                        Text("Server: \(session.baseURL)")
                        Text("User: \(session.username)")
                        Text("User ID: \(session.userId)")
                        Text("Server ID: \(session.serverId ?? "-")")
                    }
                }

                Section("Bridge Context Snapshot") {
                    if let snapshot = bridgeContextSnapshot {
                        Text("Schema: \(snapshot.schemaVersion)")
                        Text("Auth state: \(snapshot.authState.rawValue)")
                        Text("Source: \(snapshot.source)")
                        Text("Updated: \(snapshot.updatedAt.ISO8601Format())")
                        if let session = snapshot.session {
                            Text("Context server: \(session.baseURL)")
                            Text("Context user: \(session.username)")
                            Text("Context userId: \(session.userId)")
                        } else {
                            Text("Session payload: nil")
                        }
                    } else {
                        Text("No bridge context found in UserDefaults.")
                            .foregroundColor(.secondary)
                    }
                }

                Section("Secure Auth Boundary") {
                    if let runtime = secureRuntimeSnapshot {
                        Text("Remember session: \(runtime.rememberSession ? "on" : "off")")
                        Text("Runtime issue: \(runtimeIssueLabel(runtime.issue))")
                        Text("Bridge auth state: \(runtime.bridgeAuthState.rawValue)")
                        Text("Secure token available: \(runtime.hasSecureAccessToken ? "yes" : "no")")
                        Text("Secure token source: \(secureTokenSourceLabel(runtime.tokenSource))")
                        Text("Persisted secure token: \(runtime.hasPersistedSecureToken ? "yes" : "no")")
                        Text("Bridge/session consistent: \(runtime.isBridgeConsistentWithActiveSession ? "yes" : "no")")
                        Text("Persisted metadata: \(runtime.hasPersistedSessionMetadata ? "yes" : "no")")
                        Text("rememberSession semantics valid: \(runtime.isRememberSessionSemanticsValid ? "yes" : "no")")
                        Text("Issue detail: \(runtime.issueDetail)")
                            .foregroundColor(.secondary)
                        Text("Suggested action: \(runtime.recommendedAction)")
                            .foregroundColor(.secondary)
                        Text("Checked at: \(runtime.checkedAt.ISO8601Format())")
                    } else {
                        Text("Runtime snapshot unavailable.")
                            .foregroundColor(.secondary)
                    }
                }

                Section("Home Bridge Snapshot") {
                    if let snapshot = homeSnapshotStatus {
                        Text("Freshness: \(homeSnapshotFreshnessLabel(snapshot.freshness))")
                        Text("Schema: \(snapshot.schemaVersion.map(String.init) ?? "-")")
                        Text("Age: \(homeSnapshotAgeLabel(snapshot))")
                        Text("Updated: \(snapshot.updatedAt?.ISO8601Format() ?? "-")")
                        Text("Snapshot server: \(redactBaseURL(snapshot.baseURL ?? "-"))")
                        Text("Snapshot user: \(redactIdentifier(snapshot.userID))")
                        Text("Snapshot username: \(redactUsername(snapshot.username))")
                        Text("Sections: continue=\(snapshot.continueCount), nextUp=\(snapshot.nextUpCount), updates=\(snapshot.updatesCount), latest=\(snapshot.latestCount)")
                    } else {
                        Text("Home snapshot status unavailable.")
                            .foregroundColor(.secondary)
                    }
                }

                Section("Shared Contract Readiness") {
                    let readiness = sharedContractReadiness(for: bridgeContextSnapshot)
                    Text("Status: \(readiness.isReady ? "ready" : "incomplete")")
                    if readiness.missingFields.isEmpty {
                        Text("Missing fields: none")
                    } else {
                        ForEach(readiness.missingFields, id: \.self) { field in
                            Text("Missing: \(field)")
                                .foregroundColor(.secondary)
                        }
                    }
                    Text("Secure-only: accessToken stays in Keychain and is not in bridge payload.")
                        .foregroundColor(.secondary)
                }

                Section {
                    Button("Refresh Snapshot") {
                        bridgeContextSnapshot = sessionStore.bridgeContextSnapshot()
                        refreshRuntimeSnapshot()
                        refreshHomeSnapshotStatus(for: activeSession())
                        showBridgeStatus("诊断信息已更新。")
                    }
                    Button("执行会话健康检查") {
                        runRuntimeHealthCheck(showSuccessBanner: true)
                    }
                    .disabled(activeSession() == nil)
                    if case let .authenticated(session) = containerState {
                        Button(isRefreshingHomeSnapshot ? "Refreshing Home Snapshot..." : "Refresh Home Snapshot") {
                            refreshHomeBridgeSnapshot(for: session, reason: .manual)
                        }
                        .disabled(isRefreshingHomeSnapshot)
                    } else {
                        Text("当前没有已登录会话。")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("会话诊断")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        isDiagnosticsPresented = false
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private var containerStateLabel: String {
        switch containerState {
        case .restoring:
            return "restoring"
        case .unauthenticated:
            return "unauthenticated"
        case .authenticated:
            return "authenticated"
        }
    }

    private func syncResultLabel(_ result: BridgeSyncResult) -> String {
        switch result {
        case .upToDate:
            return "upToDate"
        case .repaired:
            return "repaired"
        case .clearedUnauthenticated:
            return "clearedUnauthenticated"
        }
    }

    private func secureTokenSourceLabel(_ source: SecureAccessTokenSource) -> String {
        switch source {
        case .inMemorySession:
            return "in-memory session"
        case .keychain:
            return "keychain"
        case .unavailable:
            return "unavailable"
        }
    }

    private func runtimeIssueLabel(_ issue: SecureAuthRuntimeIssue) -> String {
        switch issue {
        case .healthy:
            return "healthy"
        case .missingSecureToken:
            return "missing-secure-token"
        case .rememberSemanticsInvalid:
            return "remember-semantics-invalid"
        case .bridgeContextInconsistent:
            return "bridge-context-inconsistent"
        }
    }

    private func refreshHomeSnapshotStatus(for session: JellyfinSession?) {
        homeSnapshotStatus = homeBridgeStore.inspectHomeSnapshotStatus(for: session)
    }

    private func invalidateHomeSnapshotRefresh() {
        homeSnapshotRefreshCoordinator.cancelActiveRefresh()
        isRefreshingHomeSnapshot = false
    }

    private func activeSession() -> JellyfinSession? {
        if case let .authenticated(session) = containerState {
            return session
        }
        return nil
    }

    private func sessionIdentity(_ session: JellyfinSession) -> String {
        "\(session.baseURL)|\(session.userId)|\(session.savedAt.timeIntervalSince1970)"
    }

    private func shouldPresentHomeRefreshFailureBanner(
        for reason: HomeSnapshotRefreshReason,
        snapshot: HomeBridgeSnapshotStatus?
    ) -> Bool {
        switch reason {
        case .manual:
            return true
        case .foreground:
            return false
        case .bootstrap:
            guard let snapshot else {
                return true
            }
            switch snapshot.freshness {
            case .unavailable, .sessionMismatch, .malformed:
                return true
            case .fresh, .stale:
                return false
            }
        }
    }

    private func homeSnapshotMenuLabel(_ snapshot: HomeBridgeSnapshotStatus?) -> String {
        guard let snapshot else {
            return "无缓存"
        }
        return "\(homeSnapshotFreshnessLabel(snapshot.freshness)) · \(homeSnapshotAgeLabel(snapshot))"
    }

    private func homeSnapshotFreshnessLabel(_ freshness: HomeBridgeSnapshotFreshness) -> String {
        switch freshness {
        case .unavailable:
            return "不可用"
        case .fresh:
            return "最新"
        case .stale:
            return "旧缓存"
        case .sessionMismatch:
            return "账号不匹配"
        case .malformed:
            return "数据异常"
        }
    }

    private func homeSnapshotAgeLabel(_ snapshot: HomeBridgeSnapshotStatus) -> String {
        guard let ageSeconds = snapshot.ageSeconds else {
            return "时间未知"
        }
        if ageSeconds < 60 {
            return "\(ageSeconds) 秒前"
        }
        if ageSeconds < 3600 {
            return "\(ageSeconds / 60) 分钟前"
        }
        return "\(ageSeconds / 3600) 小时前"
    }

    private func sharedContractReadiness(for snapshot: KMPBridgeContext?) -> SharedContractReadiness {
        guard let snapshot else {
            return SharedContractReadiness(
                isReady: false,
                missingFields: [
                    "schemaVersion",
                    "authState",
                    "updatedAt",
                    "source",
                ]
            )
        }

        var missing: [String] = []

        if snapshot.schemaVersion < 2 {
            missing.append("schemaVersion>=2")
        }

        switch snapshot.authState {
        case .unauthenticated:
            break
        case .authenticated:
            guard let session = snapshot.session else {
                missing.append("session")
                break
            }
            if session.baseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                missing.append("session.baseURL")
            }
            if session.userId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                missing.append("session.userId")
            }
            if session.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                missing.append("session.username")
            }
        }

        if snapshot.source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            missing.append("source")
        }

        return SharedContractReadiness(
            isReady: missing.isEmpty,
            missingFields: missing
        )
    }
}

private enum AuthStep {
    case serverConfig
    case login
}

private enum AppContainerState {
    case restoring
    case unauthenticated(AuthStep)
    case authenticated(JellyfinSession)
}

private enum BridgeRefreshReason {
    case startup
    case postAuthentication
    case foreground
    case manual
}

private enum HomeSnapshotRefreshReason {
    case bootstrap
    case foreground
    case manual
}

private struct SharedContractReadiness {
    let isReady: Bool
    let missingFields: [String]
}

private final class HomeSnapshotRefreshCoordinator: ObservableObject {
    private let lock = NSLock()
    private var activeRefreshToken = UUID()
    private var activeSessionKey: String?
    private var activeTask: Task<Void, Never>?

    func isRefreshing(for sessionKey: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return activeSessionKey == sessionKey && activeTask != nil
    }

    func beginRefresh(for sessionKey: String) -> UUID {
        lock.lock()
        let taskToCancel = activeTask
        let nextToken = UUID()
        activeRefreshToken = nextToken
        activeSessionKey = sessionKey
        activeTask = nil
        lock.unlock()

        taskToCancel?.cancel()
        return nextToken
    }

    func attach(task: Task<Void, Never>, token: UUID, sessionKey: String) {
        var shouldCancel = false

        lock.lock()
        if activeRefreshToken == token, activeSessionKey == sessionKey {
            activeTask = task
        } else {
            shouldCancel = true
        }
        lock.unlock()

        if shouldCancel {
            task.cancel()
        }
    }

    func shouldContinue(token: UUID, sessionKey: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return activeRefreshToken == token && activeSessionKey == sessionKey
    }

    func performIfCurrent(
        token: UUID,
        sessionKey: String,
        operation: () -> Void
    ) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard activeRefreshToken == token, activeSessionKey == sessionKey else {
            return false
        }

        operation()
        return true
    }

    func finish(token: UUID, sessionKey: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        guard activeRefreshToken == token, activeSessionKey == sessionKey else {
            return false
        }

        activeTask = nil
        return true
    }

    func cancelActiveRefresh() {
        lock.lock()
        let taskToCancel = activeTask
        activeRefreshToken = UUID()
        activeSessionKey = nil
        activeTask = nil
        lock.unlock()

        taskToCancel?.cancel()
    }
}
