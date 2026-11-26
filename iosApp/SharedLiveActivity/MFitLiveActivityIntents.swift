import ActivityKit
import AppIntents
import Foundation

@MainActor
public enum MFitLiveIslandActionBridge {
    public static var onStopRestTimer: (() -> Void)?
    public static var onStopWorkout: (() -> Void)?
}

@available(iOS 17.0, *)
public struct StopRestTimerIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Stop Rest Timer"
    public static var openAppWhenRun: Bool = false

    public init() {}

    public func perform() async throws -> some IntentResult {
        await MainActor.run {
            MFitLiveIslandActionBridge.onStopRestTimer?()
        }
        let nextState = MFitWorkoutAttributes.ContentState(
            mode: MFitWorkoutMode.workoutGoing,
            timerEndDate: nil,
            statusText: MFitWorkoutMode.defaultStatusText
        )
        for activity in Activity<MFitWorkoutAttributes>.activities {
            await activity.update(ActivityContent(state: nextState, staleDate: nil))
        }
        return .result()
    }
}

@available(iOS 17.0, *)
public struct StopWorkoutIntent: LiveActivityIntent {
    public static var title: LocalizedStringResource = "Stop Workout"
    public static var openAppWhenRun: Bool = false

    public init() {}

    public func perform() async throws -> some IntentResult {
        await MainActor.run {
            MFitLiveIslandActionBridge.onStopWorkout?()
        }
        for activity in Activity<MFitWorkoutAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        return .result()
    }
}
