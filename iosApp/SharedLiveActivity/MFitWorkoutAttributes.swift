import ActivityKit
import Foundation

public enum MFitWorkoutMode {
    public static let restTimer = "REST_TIMER"
    public static let workoutGoing = "WORKOUT_GOING"
    public static let defaultStatusText = "Workout Still Going"
}

@available(iOS 16.2, *)
public struct MFitWorkoutAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var mode: String
        public var timerEndDate: Date?
        public var statusText: String

        public init(
            mode: String,
            timerEndDate: Date? = nil,
            statusText: String = MFitWorkoutMode.defaultStatusText
        ) {
            self.mode = mode
            self.timerEndDate = timerEndDate
            self.statusText = statusText
        }
    }

    public var sessionId: String

    public init(sessionId: String = "active_workout") {
        self.sessionId = sessionId
    }
}
