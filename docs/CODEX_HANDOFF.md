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
- Wake & Play can pair a selected Moonlight host with the authenticated Wake &
  Play Host Gateway over HTTPS. The leaf certificate is pinned during pairing.
- Host integration capabilities are detected asynchronously without moving UI
  focus. Discord controls remain hidden when Discord Bridge is unavailable.
- The selected host's integration panel always shows separate Gateway,
  Vibepollo Bridge and Discord Bridge status rows. These rows are non-focusable;
  asynchronous refreshes update their text without taking controller focus.
- The Vibepollo FIX panel supports status refresh, confirmed Vibepollo restart,
  confirmed remembered-display reset, and host-side log export.
- The side panel is vertically scrollable. Its compact action rows use an
  explicit two-dimensional focus grid: Left/Right stays in a row and Up/Down
  moves to the same column in the adjacent row. Whenever asynchronous
  capability discovery reveals actions, focus-neighbor IDs are rebuilt without
  requesting focus.
- Discord is presented as a compact social hub with separate favorites/recent,
  servers/channels, voice-controls and profile-settings submenus. Hardware Back
  returns to the parent submenu instead of closing the entire side panel.
- When the selected host exposes Discord Bridge, the dashboard shows a direct
  `DISCORD` entry next to Options. The Discord root shows a compact voice-state
  summary and recent quick channels without scrolling; asynchronous loading
  changes visibility and text without requesting focus.
- While the Discord panel is open, controller X toggles the local microphone
  and Y immediately leaves voice. Both commands are non-repeating and keep the
  current focus. The same actions remain available as visible buttons.
- The People panel lists voice participants, marks the local user and speaking
  participants, and exposes per-user mute plus a 0-200% volume slider for each
  remote participant. The slider uses 10% D-pad steps and serializes only the
  latest requested value so rapid changes cannot race or flood the Bridge.
- The Audio Devices panel controls Windows master volume/mute and selects
  Discord or Windows input/output devices from allow-listed device IDs.
- The USB Devices panel reports VirtualHere client/server health, lists shared
  devices, supports connect/disconnect and auto-use, and can restart the
  VirtualHere client. Empty VirtualHere server XML is handled as a valid
  zero-device state.
- Stream and active-session return intents now include the selected host's
  certificate-pinned Gateway endpoint, token and stable Discord profile ID.
  Moonlight X uses this explicit contract for its Discord overlay, so pairing
  remains owned by Wake & Play and follows the selected physical host. No
  Gateway secrets are exposed through a general provider or logged.
- Missing Discord RPC is reported as a controlled conflict instead of an HTTP
  500. Profile settings can safely request Discord startup in the Bridge user
  session without terminating any existing Discord process.
- Discord auto-start/connect is an opt-in preference scoped by both Moonlight
  host UUID and Discord integration profile ID. The current UI uses the legacy
  compatible `default` profile and migrates the older host-only preference.
- Discord auto-start runs only when a stream launch is requested, not when a
  host is merely selected. An additional profile-scoped option can join the
  last remembered voice channel; the settings panel displays its name.

## Host Gateway

The complete versioned host package lives under `host-services/`. It includes
the Gateway and canonical Discord/Vibepollo Bridge sources. The Bridges remain
on loopback and the Gateway exposes only an allow-listed HTTPS API to paired
Android TV clients. Runtime configuration, DPAPI secrets, certificates, logs,
diagnostics and state are excluded from Git.

Use one Gateway per physical streaming host. Discord Bridge is session-bound:
run one Bridge instance for each Windows/Discord profile because DPAPI secrets
belong to that user. Discord desktop RPC itself is nevertheless machine-global:
only one concurrently running Discord client can own `discord-ipc-0` and the
local RPC WebSocket port. With fast user switching, fully exit Discord in the
previous Windows profile before starting it in another profile. The Bridge and
Gateway now preserve this conflict as a specific error instead of reporting a
generic missing pipe. The Gateway profile registry maps stable profile IDs to
loopback Bridge endpoints. Requests without `X-WakePlay-Profile` use the
backward-compatible `default` profile. Wake & Play state is keyed by
`(hostUuid, integrationProfileId)`. Vibepollo also needs a per-profile Bridge
whenever its locally protected API credentials or runtime are profile-specific.

The current single-Bridge configuration is treated as Discord profile
`default`. This preserves existing pairing and Bridge configuration while the
Android profile selector is added later. Do not create a separate Gateway per
Windows profile.

Run `host-services/gateway/Start-WakePlayGateway.ps1` on the streaming PC and enter the
displayed six-digit code under `Options > Host integrations` in Wake & Play.
Pairing is valid for ten minutes. Do not expose the gateway port to the
Internet.

## Latest end-to-end verification

The latest verified session used Steam Big Picture. Remote Back returned to Wake & Play while the Moonlight stream remained alive in the background. Wake & Play displayed the active streaming state and the return-to-game action.

Do not use the `Sleep PC` entry for automated or manual stream testing. Use `Baba Is You` or `Steam Big Picture` only.

The Discord UI was additionally verified on the TV without launching a
Moonlight stream: root-panel navigation, nested Back behavior, USB and audio
panels, joining an empty voice channel, rendering the local participant, and
leaving voice with controller Y. Per-user slider behavior is covered by local
UI/API tests but was deliberately not applied to another real participant.

## Build

Run from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Continuation notes

- Preserve all existing session-handoff extras when changing Moonlight launch intents.
- Keep focus changes user-driven; asynchronous session refreshes must not steal focus.
- Integration callbacks may update text or visibility, but must not request
  focus. A destination panel may render after a user-triggered list load; a
  passive background refresh must not rebuild the currently navigated panel.
- Discord participants, per-user mute/volume, input/output device selection and
  VirtualHere controls are implemented in Wake & Play. Quick Discord controls
  in the Moonlight overlay are implemented on the companion branch, but still
  require an authorized live-stream UI test.
- The multi-profile Gateway registry and per-profile host installers are
  implemented. The Android profile selector is not implemented yet, so current
  requests still resolve to `default`.
- When tuning artwork transitions, keep the cached immediate preview and update the blurred background asynchronously.
- Validate changes together with the matching Moonlight branch because the active-session contract spans both applications.
