import Foundation
import UIKit
import CryptoKit
import JellyfinAPI

enum JellyfinAuthErrorCode: String, Codable, Equatable {
    case invalidUrl = "InvalidUrl"
    case networkUnreachable = "NetworkUnreachable"
    case tlsCertificateError = "TlsCertificateError"
    case authFailed = "AuthFailed"
    case forbidden = "Forbidden"
    case serverError = "ServerError"
    case unknown = "Unknown"
}

enum JellyfinAuthSuggestedAction: String, Codable, Equatable {
    case retry = "Retry"
    case editServer = "EditServer"
    case reLogin = "ReLogin"
}

struct JellyfinAuthServiceError: LocalizedError, Equatable {
    let code: JellyfinAuthErrorCode
    let retryable: Bool
    let suggestedAction: JellyfinAuthSuggestedAction
    let detail: String?

    var errorDescription: String? {
        switch code {
        case .invalidUrl:
            return "Please enter a valid Jellyfin server URL."
        case .networkUnreachable:
            return "Cannot reach server. Please check network connectivity."
        case .tlsCertificateError:
            return "TLS certificate validation failed."
        case .authFailed:
            return "Invalid username or password."
        case .forbidden:
            return "Access is forbidden for current account."
        case .serverError:
            return "Server returned an error. Please retry later."
        case .unknown:
            return "Unexpected error occurred during authentication."
        }
    }

    var testSummary: String {
        "code=\(code.rawValue), retryable=\(retryable ? "yes" : "no"), action=\(suggestedAction.rawValue)"
    }

    static func invalidURL(detail: String? = nil) -> JellyfinAuthServiceError {
        JellyfinAuthServiceError(
            code: .invalidUrl,
            retryable: false,
            suggestedAction: .editServer,
            detail: detail
        )
    }

    static func authFailed(detail: String? = nil) -> JellyfinAuthServiceError {
        JellyfinAuthServiceError(
            code: .authFailed,
            retryable: false,
            suggestedAction: .reLogin,
            detail: detail
        )
    }

    static func serverError(detail: String? = nil) -> JellyfinAuthServiceError {
        JellyfinAuthServiceError(
            code: .serverError,
            retryable: true,
            suggestedAction: .retry,
            detail: detail
        )
    }

    static func unknown(detail: String? = nil) -> JellyfinAuthServiceError {
        JellyfinAuthServiceError(
            code: .unknown,
            retryable: true,
            suggestedAction: .retry,
            detail: detail
        )
    }

    static func map(_ error: Error) -> JellyfinAuthServiceError {
        if let mapped = error as? JellyfinAuthServiceError {
            return mapped
        }

        if let urlError = (error as? URLError) ?? ((error as NSError).userInfo[NSUnderlyingErrorKey] as? URLError) {
            return map(urlError)
        }

        if let statusCode = extractStatusCode(from: error) {
            switch statusCode {
            case 401:
                return .authFailed(detail: "HTTP 401")
            case 403:
                return JellyfinAuthServiceError(
                    code: .forbidden,
                    retryable: false,
                    suggestedAction: .reLogin,
                    detail: "HTTP 403"
                )
            case 500 ... 599:
                return .serverError(detail: "HTTP \(statusCode)")
            default:
                return .unknown(detail: "HTTP \(statusCode)")
            }
        }

        return .unknown(detail: String(describing: error))
    }

    private static func map(_ error: URLError) -> JellyfinAuthServiceError {
        switch error.code {
        case .badURL, .unsupportedURL:
            return .invalidURL(detail: error.localizedDescription)
        case .secureConnectionFailed, .serverCertificateHasBadDate, .serverCertificateHasUnknownRoot,
             .serverCertificateNotYetValid, .serverCertificateUntrusted, .clientCertificateRejected,
             .clientCertificateRequired:
            return JellyfinAuthServiceError(
                code: .tlsCertificateError,
                retryable: false,
                suggestedAction: .editServer,
                detail: error.localizedDescription
            )
        case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost,
             .dnsLookupFailed, .timedOut, .internationalRoamingOff, .callIsActive, .dataNotAllowed:
            return JellyfinAuthServiceError(
                code: .networkUnreachable,
                retryable: true,
                suggestedAction: .retry,
                detail: error.localizedDescription
            )
        default:
            return .unknown(detail: error.localizedDescription)
        }
    }

    private static func extractStatusCode(from error: Error) -> Int? {
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

    private static func matchStatusCode(in text: String) -> Int? {
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
}

final class JellyfinAuthService {
    func normalizeBaseURL(_ raw: String) throws -> URL {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw JellyfinAuthServiceError.invalidURL(detail: "Empty input")
        }

        let withScheme: String
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            withScheme = trimmed
        } else {
            withScheme = "https://\(trimmed)"
        }

        guard var components = URLComponents(string: withScheme) else {
            throw JellyfinAuthServiceError.invalidURL(detail: "Cannot parse URL components")
        }

        guard let scheme = components.scheme?.lowercased(),
              scheme == "http" || scheme == "https"
        else {
            throw JellyfinAuthServiceError.invalidURL(detail: "Unsupported URL scheme")
        }

        guard let host = components.host?.trimmingCharacters(in: .whitespacesAndNewlines),
              !host.isEmpty
        else {
            throw JellyfinAuthServiceError.invalidURL(detail: "Missing host")
        }

        components.scheme = scheme
        components.host = host.lowercased()
        components.user = nil
        components.password = nil
        components.query = nil
        components.fragment = nil

        var normalizedPath = components.percentEncodedPath
        if normalizedPath == "/" {
            normalizedPath = ""
        } else if !normalizedPath.isEmpty {
            while normalizedPath.hasSuffix("/") {
                normalizedPath.removeLast()
            }
            if !normalizedPath.hasPrefix("/") {
                normalizedPath = "/" + normalizedPath
            }
        }
        components.percentEncodedPath = normalizedPath

        guard let normalizedURL = components.url else {
            throw JellyfinAuthServiceError.invalidURL(detail: "Failed to build normalized URL")
        }

        return normalizedURL
    }

    func probe(baseURL raw: String) async throws -> URL {
        let url = try normalizeBaseURL(raw)
        maFeiLog(.info, tag: "JellyfinAuthService", "probe start baseUrl=\(redactBaseURL(url.absoluteString))")
        let client = makeClient(for: url, accessToken: nil)

        do {
            _ = try await client.send(Paths.getPublicSystemInfo)
            maFeiLog(.info, tag: "JellyfinAuthService", "probe success baseUrl=\(redactBaseURL(url.absoluteString))")
            return url
        } catch {
            let mapped = JellyfinAuthServiceError.map(error)
            maFeiLog(
                .warning,
                tag: "JellyfinAuthService",
                "probe failed baseUrl=\(redactBaseURL(url.absoluteString)) code=\(mapped.code.rawValue) retryable=\(mapped.retryable)"
            )
            throw mapped
        }
    }

    func signIn(baseURL raw: String, username: String, password: String) async throws -> JellyfinSession {
        let url = try normalizeBaseURL(raw)
        let trimmedUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPassword = password.trimmingCharacters(in: .whitespacesAndNewlines)
        maFeiLog(
            .info,
            tag: "JellyfinAuthService",
            "signIn start baseUrl=\(redactBaseURL(url.absoluteString)) username=\(redactUsername(trimmedUser))"
        )

        guard !trimmedUser.isEmpty, !trimmedPassword.isEmpty else {
            throw JellyfinAuthServiceError.authFailed(detail: "Empty username or password")
        }

        let client = makeClient(for: url, accessToken: nil)

        do {
            let result = try await client.signIn(username: trimmedUser, password: trimmedPassword)
            guard let accessToken = result.accessToken else {
                throw JellyfinAuthServiceError.serverError(detail: "Missing access token in response")
            }

            let normalizedBaseURL = url.absoluteString
            let userId = result.user?.id ?? ""
            let session = JellyfinSession(
                baseURL: normalizedBaseURL,
                accessToken: accessToken,
                userId: userId,
                username: result.user?.name ?? trimmedUser,
                serverId: resolveServerID(from: result.serverID, normalizedBaseURL: normalizedBaseURL),
                savedAt: Date()
            )
            maFeiLog(
                .info,
                tag: "JellyfinAuthService",
                "signIn success baseUrl=\(redactBaseURL(normalizedBaseURL)) userId=\(redactIdentifier(userId)) serverId=\(redactIdentifier(session.serverId))"
            )
            return session
        } catch {
            let mapped = JellyfinAuthServiceError.map(error)
            maFeiLog(
                .warning,
                tag: "JellyfinAuthService",
                "signIn failed baseUrl=\(redactBaseURL(url.absoluteString)) username=\(redactUsername(trimmedUser)) code=\(mapped.code.rawValue) retryable=\(mapped.retryable)"
            )
            throw mapped
        }
    }

    func makeAuthenticatedClient(for session: JellyfinSession) throws -> JellyfinClient {
        let normalizedURL = try normalizeBaseURL(session.baseURL)
        maFeiLog(
            .debug,
            tag: "JellyfinAuthService",
            "makeAuthenticatedClient baseUrl=\(redactBaseURL(normalizedURL.absoluteString)) userId=\(redactIdentifier(session.userId)) tokenAvailable=\(!session.accessToken.isEmpty)"
        )
        return makeClient(for: normalizedURL, accessToken: session.accessToken)
    }

    private func makeClient(for url: URL, accessToken: String?) -> JellyfinClient {
        let appVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0"

        let configuration = JellyfinClient.Configuration(
            url: url,
            accessToken: accessToken,
            client: "MaFei iOS",
            deviceName: UIDevice.current.name,
            deviceID: UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString,
            version: appVersion
        )
        return JellyfinClient(configuration: configuration)
    }

    private func resolveServerID(from sdkServerID: String?, normalizedBaseURL: String) -> String {
        let trimmed = sdkServerID?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmed.isEmpty {
            return trimmed
        }

        let digest = SHA256.hash(data: Data(normalizedBaseURL.lowercased().utf8))
        let shortHex = digest.prefix(12).map { String(format: "%02x", $0) }.joined()
        return "derived-\(shortHex)"
    }
}
