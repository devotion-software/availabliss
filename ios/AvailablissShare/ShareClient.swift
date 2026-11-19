import Foundation

/// The two calls the share extension makes, as you: upload each attachment, then send the message.
/// (The app's own API client isn't compiled into the extension.)
struct ShareClient: Sendable {
    let token: String
    private var base: URL {
        let string = Bundle.main.object(forInfoDictionaryKey: "AVAPIBaseURL") as? String ?? ""
        return URL(string: string)!.appending(path: "api/v1")
    }

    private struct Uploaded: Decodable { let id: String }
    private struct Failure: Decodable { let message: String? }

    func upload(groupId: String, file: SharedFile) async throws -> String {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        let safeName = file.name.replacingOccurrences(of: "\"", with: "'")
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(safeName)\"\r\nContent-Type: \(file.mime)\r\n\r\n".utf8))
        body.append(file.data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        var request = URLRequest(url: base.appending(path: "groups/\(groupId)/attachments"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        return try await run(request, body: body, as: Uploaded.self).id
    }

    func send(groupId: String, attachmentIds: [String], text: String) async throws {
        var payload: [String: Any] = [:]
        if attachmentIds.isEmpty {
            payload["body"] = text
        } else {
            payload["attachmentIds"] = attachmentIds
            if !text.isEmpty { payload["body"] = text }
        }
        var request = URLRequest(url: base.appending(path: "groups/\(groupId)/messages"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        _ = try await run(request, body: try JSONSerialization.data(withJSONObject: payload), as: Uploaded.self)
    }

    private func run<T: Decodable>(_ request: URLRequest, body: Data, as: T.Type) async throws -> T {
        var request = request
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 120
        let (data, response) = try await URLSession.shared.upload(for: request, from: body)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if status == 401 { throw ShareError(message: "Open Availabliss and sign in again, then share again.") }
            throw ShareError(message: (try? JSONDecoder().decode(Failure.self, from: data))?.message ?? "Something went wrong (\(status)).")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}
