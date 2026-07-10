# Rest Timer Reliability

Issues: [#18](https://github.com/dcmcand/gymlog/issues/18), [#19](https://github.com/dcmcand/gymlog/issues/19), [#20](https://github.com/dcmcand/gymlog/issues/20)

## Status going in

- #19 (haptics when backgrounded) is already implemented by the foreground service's `vibrate()` call. Verification only.
- #18 (timer persists across navigation) is half-done: the foreground service keeps the timer running and the notification deep-links into the workout. Missing: cold launch (tap app icon) routes to Calendar even when a session is in progress.
- #20 (lockscreen visibility) is not working despite `IMPORTANCE_HIGH` + `VISIBILITY_PUBLIC`. The notification calls `.setSilent(true)` which demotes lockscreen rendering on many devices.

## Scope

### #18 — Cold-launch routing
In `MainActivity.onCreate`, after the existing `handleTimerIntent`, kick off a `lifecycleScope.launch` that queries `WorkoutSessionDao.getInProgressSession()`. If a session exists and `pendingSessionId` is still null, set it. The existing `GymLogNavigation` LaunchedEffect then navigates to `ResumeWorkout`. Notification-launched flows still win because `handleTimerIntent` runs first.

### #20 — Lockscreen visibility (light-touch)
In `RestTimerNotification.buildNotification` and `buildCompletedNotification`:
- Remove `.setSilent(true)` (channel already silences via `setSound(null, null)`).
- Add `.setCategory(NotificationCompat.CATEGORY_PROGRESS)`.
- Add `.setPriority(NotificationCompat.PRIORITY_HIGH)` for pre-O backwards compatibility.

If after device testing the timer still doesn't render on the lockscreen, escalate to MediaStyle in a follow-up.

### #19 — Verify only
No code change. Confirm on device that vibration fires when the phone is locked or the app is swiped away.

## Out of scope
- MediaStyle notification (escalation path if light-touch fails).
- Lockscreen widget (mentioned in #20 as an alternative; the notification approach is preferred).

## Risks

- `getInProgressSession()` is suspend - calling it on the main thread would freeze cold-launch. Mitigated by `lifecycleScope.launch`. Brief delay before navigation kicks in is acceptable.
- A user who deliberately swiped away from an active workout and returns might find it jarring to land on the workout again. The existing nav graph already shows the workout via `ResumeWorkout` rather than starting fresh, so no data is lost; the user can navigate back to Calendar manually. Issue text explicitly requests this behavior.
