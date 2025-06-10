//
//  DemoApp.swift
//  Antitheft
//
//  Created by Roko Gligora on 6/9/25.
//
// Main entry point and enhanced SwiftUI interface

import SwiftUI
import MapKit
import CoreLocation

// Make CLLocationCoordinate2D Equatable so onChange(of:) compiles
extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}

@main
struct DemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView(viewModel: DeviceViewModel())
        }
    }
}

// Model for map annotations
fileprivate struct DeviceLocation: Identifiable {
    let coordinate: CLLocationCoordinate2D
    let id = "device"            // ← constant, never changes
}

struct ContentView: View {
    @StateObject var viewModel: DeviceViewModel
    @State private var cameraPosition: MapCameraPosition
    @State private var blink = false


    init(viewModel: DeviceViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        let center = viewModel.coordinate
        let span = MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
        self._cameraPosition = State(initialValue: .region(MKCoordinateRegion(center: center, span: span)))
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $cameraPosition) {
                Marker("Location", coordinate: viewModel.coordinate)
            }
            .ignoresSafeArea()
            VStack {
                // Top bar
                HStack {
                    Text("Antitheft")
                        .font(.largeTitle.bold())
                    Spacer()
                    Button(action: {
                        Task { await viewModel.fetchLatestTelemetry() }
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.title2)
                    }
                }
                .padding()

                Spacer()

                // Bottom sheet
                bottomSheet
            }
        }.ignoresSafeArea(edges: .bottom)
        .onChange(of: viewModel.coordinate) { oldCenter, newCenter in
            withAnimation(.easeInOut) {
                cameraPosition = .region(MKCoordinateRegion(
                    center: newCenter,
                    span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
                ))
            }
        }
        .onChange(of: viewModel.isArmed) {oldValue, armed in
            let feedback = UINotificationFeedbackGenerator()
            feedback.notificationOccurred(armed ? .success : .warning)
        }
    }

    private var bottomSheet: some View {
        VStack(spacing: 16) {
            Capsule()
                .frame(width: 40, height: 5)
                .foregroundColor(Color.secondary.opacity(0.5))
                .padding(.top, 8)

            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Status")
                        .font(.headline)
                    Group {
                        if viewModel.isMoving {
                            Text("Moving")
                                .font(.title2.bold())
                                .foregroundColor(.red)
                                .opacity(blink ? 0.25 : 1)                 // simple blink
                                .onAppear {                                // start animation
                                    withAnimation(.easeInOut(duration: 0.6).repeatForever()) {
                                        blink.toggle()
                                    }
                                }
                        } else {
                            Text(viewModel.isArmed ? "Armed" : "Disarmed")
                                .font(.title2.bold())
                                .foregroundColor(viewModel.isArmed ? .red : .green)
                        }
                    }
                }
                Spacer()
                Picker("", selection: $viewModel.isArmed) {
                    Label("Disarm", systemImage: "lock.open.fill").tag(false)
                    Label("Arm", systemImage: "lock.fill").tag(true)
                }
                .pickerStyle(.segmented)
                .onChange(of: viewModel.isArmed) {oldValue, value in
                    viewModel.toggleArmed(value)
                }
                .frame(width: 150)
            }
            .padding(.horizontal)

            Text("Lat: \(String(format: "%.4f", viewModel.coordinate.latitude)), Lon: \(String(format: "%.4f", viewModel.coordinate.longitude))")
                .font(.footnote.monospaced())
                .padding(.bottom, 50)
        }
        .background(.ultraThinMaterial)
        .cornerRadius(20, corners: [.topLeft, .topRight])
        .shadow(radius: 5)
        .ignoresSafeArea(edges: .bottom)
    }
}


// Helper for rounded corners on specific edges
fileprivate extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

fileprivate struct RoundedCorner: Shape {
    var radius: CGFloat = 0
    var corners: UIRectCorner = .allCorners
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
