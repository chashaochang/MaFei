import SwiftUI

struct LoginRecoveryContext: Equatable {
    let reasonCode: String
    let reasonMessage: String
    let recommendedAction: String
    let previousServer: String?
    let previousUsername: String?
    let previousRememberSession: Bool?
}

struct LoginView: View {
    let baseURL: String
    let authService: JellyfinAuthService
    let recoveryContext: LoginRecoveryContext?
    let runtimeSnapshot: SecureAuthRuntimeSnapshot?
    let onBack: () -> Void
    let onAuthenticated: (JellyfinSession, Bool) -> Void

    @State private var username = ""
    @State private var password = ""
    @State private var rememberSession = true
    @State private var isSigningIn = false
    @State private var authError: JellyfinAuthServiceError?
    @State private var attemptedSignIn = false

    init(
        baseURL: String,
        authService: JellyfinAuthService,
        recoveryContext: LoginRecoveryContext?,
        runtimeSnapshot: SecureAuthRuntimeSnapshot?,
        onBack: @escaping () -> Void,
        onAuthenticated: @escaping (JellyfinSession, Bool) -> Void
    ) {
        self.baseURL = baseURL
        self.authService = authService
        self.recoveryContext = recoveryContext
        self.runtimeSnapshot = runtimeSnapshot
        self.onBack = onBack
        self.onAuthenticated = onAuthenticated
        _username = State(initialValue: recoveryContext?.previousUsername ?? "")
        _rememberSession = State(initialValue: recoveryContext?.previousRememberSession ?? true)
    }

    private var trimmedUsername: String {
        username.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var trimmedPassword: String {
        password.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isServerConfigured: Bool {
        !baseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var signInValidationMessage: String? {
        if !isServerConfigured {
            return "请先选择可用服务器。"
        }
        if trimmedUsername.isEmpty {
            return "请输入用户名。"
        }
        if trimmedPassword.isEmpty {
            return "请输入密码。"
        }
        return nil
    }

    private var canSubmitSignIn: Bool {
        !isSigningIn && signInValidationMessage == nil
    }

    var body: some View {
        NavigationView {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()
                VStack {
                    LinearGradient(
                        colors: [Color.accentColor.opacity(0.14), .clear],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 180)
                    Spacer()
                }
                .ignoresSafeArea(edges: .top)
                .allowsHitTesting(false)

                ScrollView {
                    VStack(spacing: 14) {
                        loginBrandHeader

                        VStack(alignment: .leading, spacing: 10) {
                            Label("当前服务器", systemImage: "server.rack")
                                .font(.headline)
                            Text(isServerConfigured ? baseURL : "未选择服务器")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                .textSelection(.enabled)
                            if let previousServer = recoveryContext?.previousServer,
                               !previousServer.isEmpty,
                               previousServer != baseURL
                            {
                                Text("上次服务器：\(previousServer)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Button {
                                onBack()
                            } label: {
                                Label("切换服务器", systemImage: "arrow.triangle.2.circlepath")
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                        if recoveryContext != nil || (runtimeSnapshot?.issue != .healthy) {
                            VStack(alignment: .leading, spacing: 8) {
                                Label("登录状态提示", systemImage: "shield.checkered")
                                    .font(.headline)
                                if let recoveryContext {
                                    Text(recoveryPrimaryHint(recoveryContext))
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                    Text("建议：\(recoverySuggestedAction(recoveryContext))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    if let previousUsername = recoveryContext.previousUsername,
                                       !previousUsername.isEmpty
                                    {
                                        Text("上次账号：\(previousUsername)")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                if let runtimeSnapshot, runtimeSnapshot.issue != .healthy {
                                    Text("安全状态：\(runtimeIssueSummary(runtimeSnapshot.issue))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }

                        VStack(alignment: .leading, spacing: 10) {
                            Label("账号信息", systemImage: "person.crop.circle")
                                .font(.headline)
                            TextField("用户名", text: $username)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .textFieldStyle(.roundedBorder)
                                .submitLabel(.next)
                            SecureField("密码", text: $password)
                                .textFieldStyle(.roundedBorder)
                                .submitLabel(.go)
                                .onSubmit {
                                    signIn()
                                }
                            Toggle("记住登录状态", isOn: $rememberSession)
                                .font(.subheadline)
                            Text("开启后会使用系统安全存储，下次可自动进入。")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if attemptedSignIn || isSigningIn {
                                if let signInValidationMessage {
                                    Text(signInValidationMessage)
                                        .font(.caption)
                                        .foregroundStyle(.red)
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                        if let authError {
                            VStack(alignment: .leading, spacing: 8) {
                                Label(authError.errorDescription ?? "登录失败，请稍后重试。", systemImage: "exclamationmark.triangle.fill")
                                    .foregroundStyle(.red)
                                Text("请检查账号密码与服务器连接后重试。")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if authError.suggestedAction == .retry {
                                    Button {
                                        signIn()
                                    } label: {
                                        Label("重新登录", systemImage: "arrow.clockwise")
                                    }
                                    .disabled(isSigningIn)
                                }
                                if authError.suggestedAction == .editServer {
                                    Button {
                                        onBack()
                                    } label: {
                                        Label("返回修改服务器", systemImage: "slider.horizontal.3")
                                    }
                                }
                                if !authError.testSummary.isEmpty || ((authError.detail?.isEmpty) == false) {
                                    DisclosureGroup("查看详细信息") {
                                        Text(authError.testSummary)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                        if let detail = authError.detail, !detail.isEmpty {
                                            Text("详细错误：\(detail)")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    .font(.caption)
                                }
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }

                        Button {
                            signIn()
                        } label: {
                            Label(isSigningIn ? "登录中…" : "进入 MaFei", systemImage: "play.fill")
                        }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                        .disabled(!canSubmitSignIn)
                        .padding(.top, 4)
                    }
                    .padding(16)
                }
            }
            .navigationTitle("账号登录")
            .navigationBarTitleDisplayMode(.inline)
        }
        .navigationViewStyle(.stack)
        .onChange(of: recoveryContext) { newValue in
            guard !isSigningIn else {
                return
            }
            if trimmedUsername.isEmpty {
                username = newValue?.previousUsername ?? ""
            }
            if !attemptedSignIn {
                rememberSession = newValue?.previousRememberSession ?? rememberSession
            }
        }
    }

    private var loginBrandHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.accentColor.opacity(0.16))
                    .frame(width: 56, height: 56)
                Image(systemName: "film.stack.fill")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.tint)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("登录 MaFei")
                    .font(.title3.weight(.semibold))
                Text("连接你的媒体库，继续观影与管理内容。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func recoveryPrimaryHint(_ context: LoginRecoveryContext) -> String {
        switch context.reasonCode {
        case "STARTUP_RESTORE_INCOMPLETE":
            return "检测到历史登录记录，请重新登录后继续。"
        case "SECURE_TOKEN_UNAVAILABLE", "BRIDGE_CONTEXT_EXPIRED", "BRIDGE_UNAUTHENTICATED":
            return "登录状态已过期，需要重新登录。"
        case "REMEMBER_DATA_INCOMPLETE":
            return "自动登录信息不完整，需要重新登录。"
        case "USER_SIGN_OUT":
            return "你已退出账号。"
        default:
            return context.reasonMessage
        }
    }

    private func recoverySuggestedAction(_ context: LoginRecoveryContext) -> String {
        switch context.reasonCode {
        case "STARTUP_RESTORE_INCOMPLETE":
            return "使用原账号重新登录。"
        case "SECURE_TOKEN_UNAVAILABLE", "BRIDGE_CONTEXT_EXPIRED", "BRIDGE_UNAUTHENTICATED", "REMEMBER_DATA_INCOMPLETE":
            return "重新登录后可恢复正常使用。"
        case "USER_SIGN_OUT":
            return "需要时可随时重新登录。"
        default:
            return context.recommendedAction
        }
    }

    private func signIn() {
        attemptedSignIn = true
        authError = nil
        guard canSubmitSignIn else {
            return
        }
        isSigningIn = true

        Task {
            do {
                let session = try await authService.signIn(
                    baseURL: baseURL,
                    username: trimmedUsername,
                    password: trimmedPassword
                )
                await MainActor.run {
                    isSigningIn = false
                    onAuthenticated(session, rememberSession)
                }
            } catch {
                await MainActor.run {
                    isSigningIn = false
                    authError = JellyfinAuthServiceError.map(error)
                }
            }
        }
    }

    private func runtimeIssueSummary(_ issue: SecureAuthRuntimeIssue) -> String {
        switch issue {
        case .healthy:
            return "正常"
        case .missingSecureToken:
            return "凭据缺失，需要重新登录"
        case .rememberSemanticsInvalid:
            return "本地登录状态异常，需修复"
        case .bridgeContextInconsistent:
            return "会话状态不一致，需刷新"
        }
    }
}
