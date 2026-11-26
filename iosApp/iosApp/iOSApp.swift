import SwiftUI

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        MFitLiveActivityManager.shared.configureBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
        }
        .onChange(of: scenePhase) { newPhase in
            MFitLiveActivityManager.shared.handleScenePhaseChange(newPhase)
        }
    }
}
