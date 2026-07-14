# Codex handoff

Updated: 2026-07-14

## Repository

- Project: Wake & Play for Android TV
- Branch: `main`
- Remote: `origin` (`Maladie/wake-and-play-android-tv`)
- Companion project: Moonlight Android, branch `feature/android-tv-session-controls`

## Implemented behavior

- The dashboard reports useful host and active-session state.
- The active session panel offers a prominent return action and keeps its focus stable.
- Returning from Moonlight refreshes session and application data.
- Application tiles keep a fixed viewport when a controller is connected, so posters are not cropped differently.
- Controller discovery is event-driven with a conservative fallback while the dashboard is active.
- Selected artwork is shown immediately from cached poster data, followed by an asynchronous blurred backdrop crossfade.
- Launch and return transitions request no window animation to reduce task-to-task flashes and scaling.
- Returning to Moonlight reattaches the external-frontend session contract.

## Latest end-to-end verification

The latest verified session used Steam Big Picture. Remote Back returned to Wake & Play while the Moonlight stream remained alive in the background. Wake & Play displayed the active streaming state and the return-to-game action.

Do not use the `Sleep PC` entry for automated or manual stream testing. Use `Baba Is You` or `Steam Big Picture` only.

## Build

Run from the repository root:

```powershell
gradle.bat :app:assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Continuation notes

- Preserve all existing session-handoff extras when changing Moonlight launch intents.
- Keep focus changes user-driven; asynchronous session refreshes must not steal focus.
- When tuning artwork transitions, keep the cached immediate preview and update the blurred background asynchronously.
- Validate changes together with the matching Moonlight branch because the active-session contract spans both applications.
