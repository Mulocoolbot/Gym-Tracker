import ActivityKit
import ComposeApp
import Foundation
import SwiftUI

@MainActor
final class MFitLiveActivityManager {
    static let shared = MFitLiveActivityManager()

    private final class KotlinBridgeRenderer: NSObject, IosLiveIslandRenderer {
        func render(mode: String, endEpochMs: Int64, statusText: String) {
            Task { @MainActor in
                MFitLiveActivityManager.shared.applyRender(
                    mode: mode,
                    endEpochMs: endEpochMs,
                    statusText: statusText
                )
            }
        }
    }

    private let renderer = KotlinBridgeRenderer()
    private var timerExpiryTask: Task<Void, Never>?
    private var activitySyncTask: Task<Void, Never>?
    private var renderGeneration: UInt64 = 0
    private var isConfigured = false

    private init() {}

    func configureBridge() {
        guard !isConfigured else { return }
        isConfigured = true

        if #available(iOS 17.0, *) {
            MFitLiveIslandActionBridge.onStopRestTimer = {
                LiveIslandIosBridge.shared.dispatchStopRestTimerFromIsland()
            }
            MFitLiveIslandActionBridge.onStopWorkout = {
                LiveIslandIosBridge.shared.dispatchStopWorkoutFromIsland()
            }
        }

        LiveIslandIosBridge.shared.registerRenderer(renderer: renderer)
    }

    func handleScenePhaseChange(_ phase: ScenePhase) {
        switch phase {
        case .active, .inactive, .background:
            LiveIslandIosBridge.shared.reconcileOnLifecycleEvent()
        @unknown default:
            break
        }
    }

    private func applyRender(mode: String, endEpochMs: Int64, statusText: String) {
        timerExpiryTask?.cancel()
        timerExpiryTask = nil
        renderGeneration &+= 1
        let expectedGeneration = renderGeneration

        guard #available(iOS 16.2, *) else { return }

        switch mode {
        case "INACTIVE":
            endAllActivities()

        case MFitWorkoutMode.restTimer:
            let endDate = Date(timeIntervalSince1970: Double(endEpochMs) / 1000.0)
            if endDate <= Date() {
                let ongoingState = MFitWorkoutAttributes.ContentState(
                    mode: MFitWorkoutMode.workoutGoing,
                    timerEndDate: nil,
                    statusText: MFitWorkoutMode.defaultStatusText
                )
                startOrUpdateActivity(
                    contentState: ongoingState,
                    staleDate: nil,
                    expectedGeneration: expectedGeneration
                )
                return
            }

            let timerState = MFitWorkoutAttributes.ContentState(
                mode: MFitWorkoutMode.restTimer,
                timerEndDate: endDate,
                statusText: statusText.isEmpty ? MFitWorkoutMode.defaultStatusText : statusText
            )
            startOrUpdateActivity(
                contentState: timerState,
                staleDate: endDate,
                expectedGeneration: expectedGeneration
            )
            scheduleTimerExpiryFallback(until: endDate)

        case MFitWorkoutMode.workoutGoing:
            let label = statusText.isEmpty ? MFitWorkoutMode.defaultStatusText : statusText
            let ongoingState = MFitWorkoutAttributes.ContentState(
                mode: MFitWorkoutMode.workoutGoing,
                timerEndDate: nil,
                statusText: label
            )
            startOrUpdateActivity(
                contentState: ongoingState,
                staleDate: nil,
                expectedGeneration: expectedGeneration
            )

        default:
            endAllActivities()
        }
    }

    private func enqueueActivityWork(_ operation: @escaping @MainActor () async -> Void) {
        let previousTask = activitySyncTask
        activitySyncTask = Task { @MainActor in
            _ = await previousTask?.value
            await operation()
        }
    }

    @available(iOS 16.2, *)
    private func startOrUpdateActivity(
        contentState: MFitWorkoutAttributes.ContentState,
        staleDate: Date?,
        expectedGeneration: UInt64
    ) {
        enqueueActivityWork { [weak self] in
            guard let self, self.renderGeneration == expectedGeneration else { return }
            guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

            let content = ActivityContent(state: contentState, staleDate: staleDate)
            let existing = Activity<MFitWorkoutAttributes>.activities

            if let active = existing.first {
                await active.update(content)
                if existing.count > 1 {
                    for extra in existing.dropFirst() {
                        await extra.end(nil, dismissalPolicy: .immediate)
                    }
                }
            } else {
                let attributes = MFitWorkoutAttributes()
                _ = try? Activity<MFitWorkoutAttributes>.request(
                    attributes: attributes,
                    content: content,
                    pushType: nil
                )
            }
        }
    }

    @available(iOS 16.2, *)
    private func endAllActivities() {
        enqueueActivityWork {
            let activities = Activity<MFitWorkoutAttributes>.activities
            guard !activities.isEmpty else { return }
            for activity in activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }

    private func scheduleTimerExpiryFallback(until endDate: Date) {
        let remainingSeconds = endDate.timeIntervalSinceNow
        guard remainingSeconds > 0 else { return }

        timerExpiryTask = Task { [weak self] in
            let delayNanos = UInt64((remainingSeconds + 0.15) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: delayNanos)
            guard !Task.isCancelled else { return }
            self?.handleTimerExpired()
        }
    }

    private func handleTimerExpired() {
        LiveIslandIosBridge.shared.reconcileOnLifecycleEvent()
    }
}
