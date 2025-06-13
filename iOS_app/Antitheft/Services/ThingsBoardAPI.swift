// ThingsBoardAPI.swift
// Antitheft
//
// Handles communication with ThingsBoard telemetry and RPC APIs

import Foundation
import CoreLocation

struct DataPoint: Decodable {
    let ts: Int
    let value: String
}

struct TelemetryResponse: Decodable {
    let latitude: [DataPoint]?
    let longitude: [DataPoint]?
    let armed: [DataPoint]?
    let motion_detected: [DataPoint]?
}

enum TBError: Error {
    case invalidURL, noData, decoding, rpcFailed
}

class ThingsBoardAPI {
    static let shared = ThingsBoardAPI()
    private init() {}

    // MARK: – Configuration
    private let baseURL = URL(string: "<TB_IP:port>")!
    private let deviceId = "<device ID>"
    private let jwt = "<JWT Token>"

    // MARK: – Fetch Latest Telemetry
    func fetchLatest() async throws -> (location: CLLocationCoordinate2D, armed: Bool, moving: Bool) {
        var comps = URLComponents(url: baseURL.appendingPathComponent(
            "/api/plugins/telemetry/DEVICE/\(deviceId)/values/timeseries"
        ), resolvingAgainstBaseURL: false)!
        comps.queryItems = [ URLQueryItem(name: "keys", value: "latitude,longitude,armed,motion_detected") ]

        guard let url = comps.url else { throw TBError.invalidURL }
        var req = URLRequest(url: url)
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")

        // Logging
        print("[TB] Fetching telemetry from URL: \(url.absoluteString)")
        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse {
            print("[TB] HTTP Status: \(http.statusCode)")
        }
        if let body = String(data: data, encoding: .utf8) {
            print("[TB] Response body: \(body)")
        }

        let resp = try JSONDecoder().decode(TelemetryResponse.self, from: data)
        guard
          let latStr   = resp.latitude?.last?.value,
          let lastLat  = Double(latStr),
          let lonStr   = resp.longitude?.last?.value,
          let lastLon  = Double(lonStr),
          let armedStr = resp.armed?.last?.value,
          let motionStr = resp.motion_detected?.last?.value
        else {
          print("[TB] Decoding error")
          throw TBError.decoding
        }

        let isArmed = (armedStr.lowercased() == "true")
        let isMoving = (motionStr.lowercased() == "true")
        print("[TB] Parsed latitude=\(lastLat), longitude=\(lastLon), armed=\(isArmed), moving=\(isMoving)")
        return (CLLocationCoordinate2D(latitude: lastLat, longitude: lastLon), isArmed, isMoving)
    }

    // MARK: – Send One-Way RPC
    func sendRPC(method: String) async throws {
        let url = baseURL.appendingPathComponent("/api/plugins/rpc/oneway/\(deviceId)")
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
        let body = ["method": method, "params": [:]] as [String:Any]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        print("[TB] Sending RPC method: \(method)")
        let (_, resp) = try await URLSession.shared.data(for: req)
        if let http = resp as? HTTPURLResponse {
            print("[TB] RPC HTTP Status: \(http.statusCode)")
            guard http.statusCode == 200 else { throw TBError.rpcFailed }
        } else {
            throw TBError.rpcFailed
        }
    }
}
