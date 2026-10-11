import Foundation
import Network

/// Runs the shipping Swift proxy against a local HTTP origin, without opening an application window.
@main
struct AppleMediaProxyTest {
    static func main() throws {
        let queue = DispatchQueue(label: "mafei-proxy-test-origin")
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        let origin = try NWListener(using: parameters, on: .any)
        let ready = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var requests: [String] = []
        origin.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
        origin.newConnectionHandler = { connection in
            connection.start(queue: queue)
            connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { bytes, _, _, _ in
                let request = String(data: bytes ?? Data(), encoding: .utf8) ?? ""
                lock.lock(); requests.append(request); lock.unlock()
                let path = request.split(separator: " ").dropFirst().first ?? ""
                let status: String
                let contentType: String
                let body: String
                var extra = ""
                if path == "/root/index.m3u8" {
                    status = "200 OK"; contentType = "application/vnd.apple.mpegurl"
                    body = "\u{FEFF}#EXTM3U\r\n\r\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\r\nsegment.ts\r\n"
                } else if path == "/root/same-redirect" {
                    status = "307 Temporary Redirect"; contentType = "text/plain"; body = ""
                    extra = "Location: /root/index.m3u8\r\n"
                } else if path == "/root/redirect" {
                    status = "302 Found"; contentType = "text/plain"; body = ""
                    extra = "Location: http://127.0.0.1:1/private\r\n"
                } else {
                    status = "206 Partial Content"; contentType = "video/mp2t"; body = "bc"
                    extra = "Content-Range: bytes 1-2/4\r\n"
                }
                let response = "HTTP/1.1 \(status)\r\nContent-Type: \(contentType)\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\(extra)\r\n\(body)"
                connection.send(content: Data(response.utf8), completion: .contentProcessed { _ in connection.cancel() })
            }
        }
        origin.start(queue: queue)
        precondition(ready.wait(timeout: .now() + 5) == .success)
        defer { origin.cancel() }
        let base = "http://127.0.0.1:\(origin.port!.rawValue)"
        let proxy = AppleAuthorizedMediaProxy(source: URL(string: base + "/root/index.m3u8")!, headers: ["Authorization": "fixture-token"])
        var local: URL?
        let proxyReady = DispatchSemaphore(value: 0)
        proxy.start { local = $0; proxyReady.signal() }
        precondition(proxyReady.wait(timeout: .now() + 5) == .success)
        let (playlist, response) = try fetch(local!)
        precondition(response.statusCode == 200)
        let text = String(data: playlist, encoding: .utf8)!
        precondition(text.hasPrefix("#EXTM3U")); precondition(text.contains("\r\n\r\n"))
        precondition(text.contains("URI=\"http://127.0.0.1:"))
        let segment = text.components(separatedBy: "\n").first { $0.hasPrefix("http://") }!
        let (body, segmentResponse) = try fetch(URL(string: segment)!, range: "bytes=1-2")
        precondition(segmentResponse.statusCode == 206 && String(data: body, encoding: .utf8) == "bc")
        lock.lock(); let recorded = requests; lock.unlock()
        precondition(recorded.count == 2 && recorded.allSatisfy { $0.lowercased().contains("authorization: fixture-token") })
        precondition(recorded.last!.lowercased().contains("range: bytes=1-2"))
        let (_, headResponse) = try fetch(local!, method: "HEAD")
        precondition(headResponse.statusCode == 200)
        let key = try NSRegularExpression(pattern: "URI=\"([^\"]+)\"").firstMatch(in: text, range: NSRange(text.startIndex..., in: text))!
        let keyURL = URL(string: String(text[Range(key.range(at: 1), in: text)!]))!
        let (_, keyResponse) = try fetch(keyURL)
        precondition(keyResponse.statusCode == 206)
        precondition(!AppleAuthorizedMediaProxy.sameOrigin(URL(string: base)!, URL(string: "https://127.0.0.1")!))
        proxy.close()
        let redirectProxy = AppleAuthorizedMediaProxy(source: URL(string: base + "/root/redirect")!, headers: ["Cookie": "fixture-cookie"])
        var redirected: URL?
        let redirectedReady = DispatchSemaphore(value: 0)
        redirectProxy.start { redirected = $0; redirectedReady.signal() }
        precondition(redirectedReady.wait(timeout: .now() + 5) == .success)
        let (_, rejected) = try fetch(redirected!)
        precondition(rejected.statusCode == 302)
        redirectProxy.close()
        let sameProxy = AppleAuthorizedMediaProxy(source: URL(string: base + "/root/same-redirect")!, headers: ["Authorization": "fixture-token"])
        var same: URL?; let sameReady = DispatchSemaphore(value: 0)
        sameProxy.start { same = $0; sameReady.signal() }
        precondition(sameReady.wait(timeout: .now() + 5) == .success)
        let (redirectedBody, sameResponse) = try fetch(same!)
        precondition(sameResponse.statusCode == 200 && String(data: redirectedBody, encoding: .utf8)!.hasPrefix("#EXTM3U"))
        lock.lock(); let redirectedRequests = requests.suffix(2); lock.unlock()
        precondition(redirectedRequests.allSatisfy { $0.lowercased().contains("authorization: fixture-token") })
        sameProxy.close()
        print("PASS: Swift media proxy CRLF/BOM HLS, key fetch, HEAD, auth, Range and same/cross-origin redirects")
    }
    private static func fetch(_ url: URL, range: String? = nil, method: String = "GET") throws -> (Data, HTTPURLResponse) {
        let done = DispatchSemaphore(value: 0)
        var result: Result<(Data, HTTPURLResponse), Error>!
        var request = URLRequest(url: url)
        request.httpMethod = method
        if let range { request.setValue(range, forHTTPHeaderField: "Range") }
        let session = URLSession(configuration: .ephemeral)
        session.dataTask(with: request) { data, response, error in
            if let error { result = .failure(error) }
            else if let response = response as? HTTPURLResponse { result = .success((data ?? Data(), response)) }
            else { result = .failure(URLError(.badServerResponse)) }
            done.signal()
        }.resume()
        guard done.wait(timeout: .now() + 10) == .success else { session.invalidateAndCancel(); throw URLError(.timedOut) }
        session.finishTasksAndInvalidate()
        return try result.get()
    }
}
