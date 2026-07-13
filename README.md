# Wake & Play launcher

`Wake & Play` is an Android TV companion for Moonlight X. It provides a
couch-friendly entry point before Moonlight:

- shows connected gamepads and their battery percentage;
- opens per-controller actions for identification, power-off and Bluetooth
  unpairing, with confirmation before destructive actions;
- reads the saved Vibepollo/Sunshine hosts from Moonlight;
- sends Wake-on-LAN to the selected host;
- displays a native-resolution animated loading slideshow with rotating English
  status messages;
- waits for the host streaming ports to become reachable;
- opens the selected host's application list through Moonlight's public launch
  intent.

## Compatibility and signing

Moonlight X and Wake & Play must be signed with the same certificate. The
saved-host and stream-status providers are protected by signature permissions.
Local debug builds normally share Android's default debug keystore. Release
builds must be configured to use the same release keystore in both projects.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

Install a compatible Moonlight X build first, then the launcher:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The launcher prefers the normal Moonlight X package
`com.limelight.unofficial`. It also supports compatible builds using
`com.limelight` and falls back to `com.limelight.debug` for development. Hosts
must first be added and paired in the selected Moonlight installation.

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

Moonlight exposes two narrowly scoped integration surfaces:

- a read-only saved-host provider derived from the Moonlight package, for
  example `content://hosts.com.limelight.unofficial/hosts`;
- the public action `com.limelight.action.STREAM` with
  `com.limelight.extra.HOST_UUID` to open a saved host.

The provider does not expose credentials or pairing certificates. Its signature
permission limits access to applications signed with the same certificate.
