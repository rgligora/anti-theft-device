//
//  DeviceViewModel.swift
//  Antitheft
//
//  Created by Roko Gligora on 6/9/25.
//
// Manages telemetry polling and RPC invocations

import Foundation
import CoreLocation
import Combine
import SwiftUI

@MainActor
class DeviceViewModel: ObservableObject {
    @Published var coordinate: CLLocationCoordinate2D = CLLocationCoordinate2D(latitude: 45.8080, longitude: 15.9719)
    @Published var isArmed: Bool = false
    @Published var isMoving: Bool = false
    private var wasMoving: Bool = false  // Track previous state

    private var timerCancellable: AnyCancellable?

    init() {
        startUpdating()
    }

    private func startUpdating() {
        timerCancellable = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                Task { await self?.fetchLatestTelemetry() }
            }
    }

    func fetchLatestTelemetry() async {
        do {
            let (loc, armedState, moving) = try await ThingsBoardAPI.shared.fetchLatest()
            withAnimation(.easeInOut) {
                self.coordinate = loc
                self.isArmed = armedState
                self.isMoving = moving
                
                // Check if device just started moving
                if moving && !self.wasMoving {
                    NotificationService.shared.scheduleMovementNotification()
                }
                self.wasMoving = moving
            }
        } catch {
            print("[ViewModel] Fetch error: \(error)")
        }
    }

    func toggleArmed(_ armed: Bool) {
        Task {
            let method = armed ? "armDevice" : "disarmDevice"
            try? await ThingsBoardAPI.shared.sendRPC(method: method)

            // wait 1 s, then pull once
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            await fetchLatestTelemetry()
        }
    }
}
