import SwiftUI

struct ServerConfigView: View {
    let authService: JellyfinAuthService
    let initialBaseURL: String
    let onContinue: (String) -> Void

    @State private var baseURL: String
    @State private var isTesting = false
    @State private var statusMessage: String?
    @State private var canContinue = false
    @State private var probeError: JellyfinAuthServiceError?
    @State private var lastSuccessfulURL: String?

    init(
        authService: JellyfinAuthService,
        initialBaseURL: String,
        onContinue: @escaping (String) -> Void
    ) {
        self.authService = authService
        self.initialBaseURL = initialBaseURL
        self.onContinue = onContinue
        _baseURL = State(initialValue: initialBaseURL)
    }

    private var trimmedBaseURL: String {
        baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var hasInput: Bool {
        !trimmedBaseURL.isEmpty
    }

    private var normalizedInputPreview: String? {
        try? authService.normalizeBaseURL(trimmedBaseURL).absoluteString
    }

    private var validationMessage: String? {
        guard hasInput else {
            return "请输入服务器地址，例如 http://192.168.1.10:8096。"
        }
        do {
            _ = try authService.normalizeBaseURL(trimmedBaseURL)
            return nil
        } catch {
            return JellyfinAuthServiceError.map(error).errorDescription ?? "服务器地址格式不正确。"
        }
    }

    private var canTestConnection: Bool {
        !isTesting && validationMessage == nil
    }

    private var continueHint: String {
        if canContinue {
            return "连接已通过验证，可以继续登录。"
        }
        if isTesting {
            return "正在检测连接状态…"
        }
        return "先完成检测，再登录会更顺畅。"
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
                        serverBrandHeader

                        VStack(alignment: .leading, spacing: 10) {
                            Label("服务器地址", systemImage: "server.rack")
                                .font(.headline)
                            TextField("https://example.com 或 http://192.168.1.10:8096", text: $baseURL)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .keyboardType(.URL)
                                .textFieldStyle(.roundedBorder)
                                .onChange(of: baseURL) { _ in
                                    canContinue = false
                                    lastSuccessfulURL = nil
                                    statusMessage = nil
                                    probeError = nil
                                }

                            if let normalizedInputPreview {
                                Text("标准地址：\(normalizedInputPreview)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            } else if let validationMessage {
                                Text(validationMessage)
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }

                            if !initialBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text("上次服务器：\(initialBaseURL)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }

                            Button {
                                probeServer()
                            } label: {
                                Label(isTesting ? "检测中…" : "检测并连接", systemImage: "bolt.horizontal.circle")
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!canTestConnection)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                        VStack(alignment: .leading, spacing: 6) {
                            Label("连接提示", systemImage: "lightbulb")
                                .font(.headline)
                            tipRow(icon: "wifi", text: "确认当前设备与服务器在同一网络或可互通。")
                            tipRow(icon: "link", text: "地址需带协议（http/https），必要时附带端口。")
                            tipRow(icon: "lock.shield", text: "若使用 https，请确保证书可被设备信任。")
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                        if isTesting {
                            HStack(spacing: 10) {
                                ProgressView()
                                Label("正在验证服务器可用性…", systemImage: "dot.radiowaves.left.and.right")
                            }
                            .font(.subheadline)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }

                        if let statusMessage, probeError == nil {
                            VStack(alignment: .leading, spacing: 6) {
                                Label(statusMessage, systemImage: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                                if let lastSuccessfulURL {
                                    Text("已验证地址：\(lastSuccessfulURL)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .textSelection(.enabled)
                                }
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }

                        if let probeError {
                            VStack(alignment: .leading, spacing: 8) {
                                Label(probeError.errorDescription ?? "连接检测失败。", systemImage: "exclamationmark.triangle.fill")
                                    .foregroundStyle(.red)
                                Text("请检查服务器地址、网络与证书后重试。")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text("建议：\(suggestedActionDescription(for: probeError.suggestedAction))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if probeError.suggestedAction == .retry {
                                    Button {
                                        probeServer()
                                    } label: {
                                        Label("重新检测", systemImage: "arrow.clockwise")
                                    }
                                    .disabled(isTesting)
                                }
                                if !probeError.testSummary.isEmpty || ((probeError.detail?.isEmpty) == false) {
                                    DisclosureGroup("查看详细信息") {
                                        Text(probeError.testSummary)
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                        if let detail = probeError.detail, !detail.isEmpty {
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

                        VStack(alignment: .leading, spacing: 8) {
                            Text(continueHint)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Button {
                                if let normalized = try? authService.normalizeBaseURL(baseURL) {
                                    onContinue(normalized.absoluteString)
                                } else {
                                    onContinue(baseURL)
                                }
                            } label: {
                                Label("继续登录", systemImage: "arrow.right.circle.fill")
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!canContinue)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .padding(16)
                }
            }
            .navigationTitle("连接服务器")
            .navigationBarTitleDisplayMode(.inline)
        }
        .navigationViewStyle(.stack)
    }

    private var serverBrandHeader: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Color.accentColor.opacity(0.16))
                    .frame(width: 56, height: 56)
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.tint)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("连接你的媒体服务器")
                    .font(.title3.weight(.semibold))
                Text("先确认服务器可用，再进入账号登录。")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func tipRow(icon: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(text)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func suggestedActionDescription(for action: JellyfinAuthSuggestedAction) -> String {
        switch action {
        case .retry:
            return "请检查网络与服务器状态后再试。"
        case .editServer:
            return "请核对地址、协议或证书设置。"
        case .reLogin:
            return "服务器可达，请继续登录并确认账号密码。"
        }
    }

    private func probeServer() {
        statusMessage = nil
        probeError = nil
        canContinue = false
        lastSuccessfulURL = nil
        isTesting = true

        Task {
            do {
                let normalizedURL = try await authService.probe(baseURL: baseURL)
                await MainActor.run {
                    baseURL = normalizedURL.absoluteString
                    canContinue = true
                    lastSuccessfulURL = normalizedURL.absoluteString
                    statusMessage = "连接检测通过。"
                    isTesting = false
                }
            } catch {
                await MainActor.run {
                    canContinue = false
                    let mapped = JellyfinAuthServiceError.map(error)
                    probeError = mapped
                    statusMessage = nil
                    isTesting = false
                }
            }
        }
    }
}
