import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

private enum MFitTheme {
    static let background = Color(red: 11.0 / 255.0, green: 12.0 / 255.0, blue: 14.0 / 255.0)
    static let surface = Color(red: 19.0 / 255.0, green: 21.0 / 255.0, blue: 27.0 / 255.0)
    static let border = Color(red: 34.0 / 255.0, green: 37.0 / 255.0, blue: 48.0 / 255.0)
    static let volt = Color(red: 212.0 / 255.0, green: 1.0, blue: 0.0)
    static let textPrimary = Color(red: 245.0 / 255.0, green: 247.0 / 255.0, blue: 250.0 / 255.0)
    static let textMuted = Color(red: 142.0 / 255.0, green: 149.0 / 255.0, blue: 165.0 / 255.0)
    static let dangerRed = Color(red: 239.0 / 255.0, green: 68.0 / 255.0, blue: 68.0 / 255.0)
}

@available(iOS 17.0, *)
struct MFitWorkoutLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MFitWorkoutAttributes.self) { context in
            LockScreenBannerView(context: context)
                .activityBackgroundTint(MFitTheme.background)
                .activitySystemActionForegroundColor(MFitTheme.volt)
        } dynamicIsland: { context in
            let endDate = activeTimerEndDate(for: context)

            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        Image(systemName: endDate != nil ? "timer" : "dumbbell.fill")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(MFitTheme.volt)
                        Text("MFit")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(MFitTheme.textMuted)
                    }
                    .padding(.leading, 4)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    StopSquareActionButton(isRestTimer: endDate != nil)
                        .padding(.trailing, 4)
                }

                DynamicIslandExpandedRegion(.bottom) {
                    if let endDate {
                        HStack(alignment: .center) {
                            Text("Rest Timer")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(MFitTheme.textMuted)
                            Spacer()
                            Text(timerInterval: Date()...endDate, countsDown: true)
                                .font(.system(size: 28, weight: .bold, design: .monospaced))
                                .monospacedDigit()
                                .foregroundColor(MFitTheme.volt)
                        }
                        .padding(.horizontal, 6)
                        .padding(.top, 4)
                    } else {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(MFitTheme.volt)
                                .frame(width: 8, height: 8)
                            Text(resolvedStatusText(context.state.statusText))
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(MFitTheme.textPrimary)
                            Spacer()
                        }
                        .padding(.horizontal, 6)
                        .padding(.top, 6)
                    }
                }
            } compactLeading: {
                Image(systemName: endDate != nil ? "timer" : "dumbbell.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(MFitTheme.volt)
            } compactTrailing: {
                if let endDate {
                    Text(timerInterval: Date()...endDate, countsDown: true)
                        .font(.system(size: 13, weight: .bold, design: .monospaced))
                        .monospacedDigit()
                        .foregroundColor(MFitTheme.volt)
                        .frame(maxWidth: 54)
                } else {
                    Text("LIVE")
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundColor(MFitTheme.volt)
                }
            } minimal: {
                Image(systemName: endDate != nil ? "timer" : "dumbbell.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(MFitTheme.volt)
            }
            .keylineTint(MFitTheme.volt)
        }
    }
}

@available(iOS 17.0, *)
private struct LockScreenBannerView: View {
    let context: ActivityViewContext<MFitWorkoutAttributes>

    var body: some View {
        let endDate = activeTimerEndDate(for: context)

        HStack(spacing: 14) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(MFitTheme.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(MFitTheme.border, lineWidth: 1)
                    )
                    .frame(width: 40, height: 40)

                Image(systemName: endDate != nil ? "timer" : "dumbbell.fill")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(MFitTheme.volt)
            }

            if let endDate {
                VStack(alignment: .leading, spacing: 2) {
                    Text("MFIT · REST TIMER")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(MFitTheme.textMuted)
                    Text(timerInterval: Date()...endDate, countsDown: true)
                        .font(.system(size: 24, weight: .bold, design: .monospaced))
                        .monospacedDigit()
                        .foregroundColor(MFitTheme.volt)
                }
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    Text("MFIT WORKOUT")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(MFitTheme.textMuted)
                    Text(resolvedStatusText(context.state.statusText))
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(MFitTheme.textPrimary)
                }
            }

            Spacer()

            StopSquareActionButton(isRestTimer: endDate != nil)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(MFitTheme.background)
    }
}

@available(iOS 17.0, *)
private struct StopSquareActionButton: View {
    let isRestTimer: Bool

    var body: some View {
        if isRestTimer {
            Button(intent: StopRestTimerIntent()) {
                StopSquareIcon(accentColor: MFitTheme.volt)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop rest timer")
        } else {
            Button(intent: StopWorkoutIntent()) {
                StopSquareIcon(accentColor: MFitTheme.dangerRed)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Stop workout")
        }
    }
}

private struct StopSquareIcon: View {
    let accentColor: Color

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(MFitTheme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(accentColor.opacity(0.65), lineWidth: 1.2)
                )
                .frame(width: 34, height: 34)

            Image(systemName: "stop.fill")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(accentColor)
        }
    }
}

@available(iOS 16.2, *)
private func activeTimerEndDate(for context: ActivityViewContext<MFitWorkoutAttributes>) -> Date? {
    guard context.state.mode == MFitWorkoutMode.restTimer, !context.isStale else {
        return nil
    }
    guard let timerEndDate = context.state.timerEndDate, timerEndDate > Date() else {
        return nil
    }
    return timerEndDate
}

private func resolvedStatusText(_ raw: String) -> String {
    raw.isEmpty ? MFitWorkoutMode.defaultStatusText : raw
}
