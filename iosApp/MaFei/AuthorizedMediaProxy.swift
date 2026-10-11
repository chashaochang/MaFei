import Foundation
import Network

/// AVFoundation reads ordinary HTTP, including HLS playlists, segments and keys.
/// Credentials stay at the original server; only this process uses the loopback URL.
final class AppleAuthorizedMediaProxy {
    private let source: URL
    private let headers: [String: String]
    private let token = UUID().uuidString
    private let queue = DispatchQueue(label: "cn.mafei.authorized-media")
    private var listener: NWListener?
    private var pipes: [UUID: Pipe] = [:]
    private var closed = false

    init(source: URL, headers: [String: String]) { self.source = source; self.headers = headers }

    func start(_ completion: @escaping (URL?) -> Void) {
        queue.async { [self] in
            guard !self.closed else { completion(nil); return }
            do {
                let parameters = NWParameters.tcp
                parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
                let listener = try NWListener(using: parameters, on: .any)
                self.listener = listener
                var delivered = false
                listener.stateUpdateHandler = { [weak self] state in
                    guard let self, !delivered else { return }
                    switch state {
                    case .ready: delivered = true; completion(self.localURL(self.source))
                    case .failed, .cancelled: delivered = true; completion(nil)
                    default: break
                    }
                }
                listener.newConnectionHandler = { [weak self] connection in
                    guard let self, !self.closed else { connection.cancel(); return }
                    let id = UUID()
                    let pipe = Pipe(connection: connection, source: self.source, headers: self.headers,
                                    token: self.token, queue: self.queue, rewrite: { [weak self] url in self?.localURL(url) },
                                    finished: { [weak self] in self?.pipes.removeValue(forKey: id) })
                    self.pipes[id] = pipe
                    pipe.start()
                }
                listener.start(queue: self.queue)
            } catch { completion(nil) }
        }
    }

    func close() {
        queue.async {
            guard !self.closed else { return }
            self.closed = true
            self.listener?.cancel(); self.listener = nil
            let pipes = Array(self.pipes.values)
            self.pipes.removeAll()
            pipes.forEach { $0.close() }
        }
    }

    private func localURL(_ target: URL) -> URL? {
        guard let port = listener?.port, Self.sameOrigin(source, target) else { return nil }
        let encoded = Data(target.absoluteString.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let extensionHint = target.pathExtension.isEmpty ? "bin" : target.pathExtension
        return URL(string: "http://127.0.0.1:\(port.rawValue)/\(token)/\(encoded)/media.\(extensionHint)")
    }

    static func sameOrigin(_ first: URL, _ second: URL) -> Bool {
        first.scheme == second.scheme && first.host?.lowercased() == second.host?.lowercased()
            && (first.port ?? (first.scheme == "https" ? 443 : 80)) == (second.port ?? (second.scheme == "https" ? 443 : 80))
    }

    private final class Pipe: NSObject, URLSessionDataDelegate {
        private let connection: NWConnection
        private let source: URL
        private let headers: [String: String]
        private let token: String
        private let queue: DispatchQueue
        private let rewrite: (URL) -> URL?
        private let finished: () -> Void
        private var session: URLSession?
        private var task: URLSessionDataTask?
        private var incoming = Data()
        private var playlist = Data()
        private var playlistResponse: HTTPURLResponse?
        private var requestHeaders: [String: String] = [:]
        private var target: URL?
        private var method = "GET"
        private var redirects = 0
        private var writes = 0
        private var responseSent = false
        private var complete = false
        private var closed = false

        init(connection: NWConnection, source: URL, headers: [String: String], token: String,
             queue: DispatchQueue, rewrite: @escaping (URL) -> URL?, finished: @escaping () -> Void) {
            self.connection = connection; self.source = source; self.headers = headers; self.token = token
            self.queue = queue; self.rewrite = rewrite; self.finished = finished
        }
        func start() {
            connection.stateUpdateHandler = { [weak self] state in
                if case .failed = state { self?.close() }
            }
            connection.start(queue: queue)
            readHeader()
        }
        private func readHeader() {
            connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] bytes, _, ended, error in
                guard let self, !self.closed else { return }
                if let bytes { self.incoming.append(bytes) }
                guard self.incoming.count <= 16384, error == nil else { self.close(); return }
                if let end = self.incoming.range(of: Data("\r\n\r\n".utf8)) {
                    guard let text = String(data: self.incoming[..<end.lowerBound], encoding: .utf8) else { self.close(); return }
                    self.open(text)
                } else if ended { self.close() } else { self.readHeader() }
            }
        }
        private func open(_ text: String) {
            let lines = text.components(separatedBy: "\r\n")
            let start = (lines.first ?? "").split(separator: " ")
            guard start.count == 3, ["GET", "HEAD"].contains(String(start[0])) else { close(); return }
            method = String(start[0])
            let path = start[1].split(separator: "/")
            guard path.count == 3, path[0] == Substring(token) else { close(); return }
            var encoded = String(path[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
            guard let bytes = Data(base64Encoded: encoded), let value = String(data: bytes, encoding: .utf8),
                  let url = URL(string: value), AppleAuthorizedMediaProxy.sameOrigin(source, url) else { close(); return }
            target = url
            for line in lines.dropFirst() {
                let parts = line.split(separator: ":", maxSplits: 1)
                if parts.count == 2 {
                    let name = String(parts[0])
                    if ["range", "if-range", "if-none-match", "if-modified-since"].contains(name.lowercased()) {
                        requestHeaders[name] = parts[1].trimmingCharacters(in: .whitespaces)
                    }
                }
            }
            let config = URLSessionConfiguration.ephemeral
            config.httpCookieStorage = nil; config.urlCache = nil
            config.timeoutIntervalForRequest = 30; config.timeoutIntervalForResource = 7 * 24 * 60 * 60
            let operations = OperationQueue(); operations.maxConcurrentOperationCount = 1; operations.underlyingQueue = queue
            session = URLSession(configuration: config, delegate: self, delegateQueue: operations)
            task = session?.dataTask(with: outgoing(url)); task?.resume()
        }
        private func outgoing(_ url: URL) -> URLRequest {
            var request = URLRequest(url: url); request.httpMethod = method
            headers.filter { !["host", "connection", "content-length"].contains($0.key.lowercased()) }
                .forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
            requestHeaders.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
            return request
        }
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
            redirects += 1
            guard redirects <= 4, let next = request.url, AppleAuthorizedMediaProxy.sameOrigin(source, next), !closed else { completionHandler(nil); return }
            target = next
            completionHandler(outgoing(next))
        }
        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
            guard !closed, let http = response as? HTTPURLResponse else { completionHandler(.cancel); return }
            let isPlaylist = (response.mimeType ?? "").lowercased().contains("mpegurl") || target?.pathExtension.lowercased() == "m3u8"
            if isPlaylist && (200..<300).contains(http.statusCode) && method == "GET" { playlistResponse = http }
            else { sendHeader(http, length: http.value(forHTTPHeaderField: "Content-Length")) }
            completionHandler(.allow)
        }
        func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
            guard !closed else { return }
            if playlistResponse != nil {
                guard playlist.count + data.count <= 512 * 1024 else { close(); return }
                playlist.append(data)
            } else {
                dataTask.suspend()
                send(data) { [weak dataTask] in dataTask?.resume() }
            }
        }
        func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
            guard !closed else { return }
            if let response = playlistResponse, error == nil {
                guard let text = String(data: playlist, encoding: .utf8), let base = target else { close(); return }
                do {
                    let body = try Self.rewritePlaylist(text) { value in
                        guard let url = URL(string: value, relativeTo: base)?.absoluteURL, let local = self.rewrite(url) else { throw URLError(.redirectToNonExistentLocation) }
                        return local.absoluteString
                    }
                    let bytes = Data(body.utf8)
                    sendHeader(response, length: String(bytes.count)); send(bytes)
                } catch { close(); return }
            } else if error != nil && !responseSent { send(Data("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".utf8)) }
            complete = true
            if writes == 0 { close() }
        }
        private func sendHeader(_ response: HTTPURLResponse, length: String?) {
            var header = "HTTP/1.1 \(response.statusCode) OK\r\nConnection: close\r\n"
            for key in ["Content-Type", "Content-Range", "Accept-Ranges", "ETag", "Last-Modified"] {
                if let value = response.value(forHTTPHeaderField: key) { header += "\(key): \(value)\r\n" }
            }
            if let length { header += "Content-Length: \(length)\r\n" }
            send(Data((header + "\r\n").utf8)); responseSent = true
        }
        private func send(_ bytes: Data, completion: @escaping () -> Void = {}) {
            writes += 1
            connection.send(content: bytes, completion: .contentProcessed { [weak self] error in
                guard let self, !self.closed else { return }
                self.writes -= 1
                if error != nil { self.close(); return }
                completion()
                if self.complete && self.writes == 0 { self.close() }
            })
        }
        func close() {
            guard !closed else { return }
            closed = true
            task?.cancel(); session?.invalidateAndCancel(); session = nil
            connection.cancel(); finished()
        }
        private static func rewritePlaylist(_ text: String, resolve: (String) throws -> String) throws -> String {
            let pattern = try NSRegularExpression(pattern: "URI=\"([^\"]+)\"")
            let document = text.hasPrefix("\u{FEFF}") ? String(text.dropFirst()) : text
            return try document.components(separatedBy: "\n").map { line in
                let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.isEmpty { return line }
                if !trimmed.hasPrefix("#") { return try resolve(trimmed) }
                let matches = pattern.matches(in: line, range: NSRange(line.startIndex..., in: line))
                var result = line
                for match in matches.reversed() {
                    guard let range = Range(match.range(at: 1), in: result) else { continue }
                    let value = String(result[range]); result.replaceSubrange(range, with: try resolve(value))
                }
                return result
            }.joined(separator: "\n")
        }
    }
}
