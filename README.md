# Wake & Play launcher

`Wake & Play` is an Android TV companion for Moonlight X. It provides a
couch-friendly entry point before Moonlight:

- shows connected gamepads and their battery percentage;
- opens per-controller actions for identification, power-off and Bluetooth
  unpairing, with confirmation before destructive actions;
- reads the saved Vibepollo/Sunshine hosts from Moonlight;
- displays Moonlight's cached applications and poster art in its own TV UI;
- sends Wake-on-LAN to the selected host;
- displays a native-resolution animated loading slideshow with rotating English
  status messages;
- waits for the host streaming ports to become reachable;
- starts the selected application directly through Moonlight's public launch
  intent and returns to Wake & Play when the stream ends;
- opens Moonlight's real streaming settings from the launcher.

## Compatibility and signing

The default runtime target is the normal Moonlight X application:

```text
package: com.limelight.unofficial
label:   Moonlight
```

The `.debug` package is only a development fallback. Wake & Play does not
require Moonlight Debug when the compatible normal Moonlight X build is
installed.

Moonlight X and Wake & Play must be signed with the same certificate. The
saved-host and stream-status providers are protected by signature permissions.
Local debug builds normally share Android's default debug keystore. Release
builds must be configured to use the same release keystore in both projects.

## Download and install

Download the signed APK from the project's
[Releases](https://github.com/Maladie/wake-and-play-android-tv/releases) page.
Install the compatible normal Moonlight X build first, then install Wake & Play.
The APK can be transferred to Android TV with any sideloading tool or file
manager; ADB is optional.

The launcher prefers the normal Moonlight X package
`com.limelight.unofficial`. It also supports compatible builds using
`com.limelight` and falls back to `com.limelight.debug` for development. Hosts
must first be added and paired in the selected Moonlight installation.

## Build a signed release

Wake & Play and Moonlight X must use the same signing keystore. Build the
installable release with:

```powershell
.\build-release.bat `
  -Keystore "C:\path\to\shared-release.keystore" `
  -StorePassword "..." `
  -KeyAlias "..." `
  -KeyPassword "..."
```

Output:

```text
app/build/outputs/apk/release/app-release.apk
```

For local testing, running `build-release.bat` without arguments uses Android's
standard local debug keystore. Public releases should always use a backed-up,
dedicated keystore shared with the corresponding Moonlight X release.

## Development build

```powershell
.\gradlew.bat :app:assembleDebug
```

This produces `app/build/outputs/apk/debug/app-debug.apk`. The debug build is
intended for development and is not the package distributed to users.

## Controller actions

Select a controller card to open its action menu. Identification uses the
controller LED and/or vibration capabilities exposed by Android. Some Android
TV firmware, including certain Sony builds, does not expose either capability.

Power-off and unpair are best-effort on current Android TV releases because
Bluetooth HID management is normally reserved for privileged system apps. The
launcher never chooses between multiple identically named paired controllers;
when the selected input device cannot be mapped unambiguously, it leaves every
controller unchanged and offers a shortcut to the system Bluetooth settings.

## Integration contract

Moonlight exposes narrowly scoped integration surfaces:

- a read-only saved-host provider derived from the Moonlight package, for
  example `content://hosts.com.limelight.unofficial/hosts`;
- a same-signature cached-app provider, for example
  `content://apps.com.limelight.unofficial/apps/<host-uuid>`;
- the public action `com.limelight.action.STREAM` with
  host/app identifiers and `com.limelight.extra.EXTERNAL_FRONTEND=true` to
  start a shell-owned session without leaving Moonlight's host UI underneath;
- `com.limelight.action.OPEN_SETTINGS` for the real Moonlight settings screen.
- `com.limelight.action.RETURN_STREAM` to reveal an existing stream without
  relaunching its host application;
- the stream-status provider for live session state and elapsed time.

Wake & Play probes saved hosts asynchronously and labels them as online,
Wake-on-LAN ready, or offline. While a live Moonlight activity exists, the last
launch shortcut becomes **Return to Stream** and the matching host card shows
the active application, client, and elapsed session time. Moonlight moves video
decoding to a persistent background surface during the handoff, allowing Wake &
Play to use a fully opaque window and avoid partial-redraw artifacts on TV
compositors.
Moonlight routes HOME and BACK from externally launched streams back to Wake &
Play, so these keys open the session controls rather than ending the stream.

The active-session panel shows the current stream format and provides confirmed
actions to return, disconnect while leaving the host app running, or end the
host app. Destructive controls use Moonlight's signature-protected
`CONTROL_STREAM` permission. Focusing an application tile also blends its
poster into a lightly softened color wash plus a proportion-preserving hero
image. Focus changes are debounced and cross-faded for a calmer console-style
background without blocking the UI. Application, host, and controller tiles use
a subtle translucent glass treatment, while Options stays in the upper-right
corner and Session matches the primary action's height. Moving between
application tiles also uses
the Android TV focus sound, and any manual D-pad input cancels delayed default
focus so navigation is never pulled back to the Resume action.

The provider does not expose credentials or pairing certificates. Its signature
permission limits access to applications signed with the same certificate. The
permission name follows the Moonlight package ID, for example
`com.limelight.unofficial.permission.READ_SAVED_HOSTS` for the normal Moonlight
X release.
