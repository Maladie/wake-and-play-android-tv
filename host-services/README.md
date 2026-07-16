# Wake & Play host services

This directory is the canonical source for the Windows side of Wake & Play:

- `gateway/` is the only LAN-facing component. It provides certificate-pinned,
  authenticated HTTPS and an allow-listed API.
- `bridges/discord/` talks to the Discord client, Windows audio and
  VirtualHere from the interactive Windows session.
- `bridges/vibepollo/` talks to the local Vibepollo API and provides health and
  repair operations.
- `bridges/playnite/` reuses the Playnite connector's launcher pipe for library,
  lifecycle and privacy-readiness coordination in the interactive profile.
- `install/` contains the machine-level and per-profile installers.
- `tests/` prevents runtime secrets and state from entering the repository.

The Gateway is installed once per physical streaming host. Bridge instances
are installed per Windows integration profile because Discord RPC and DPAPI
credentials are session/user-bound. Every concurrently installed profile must
use distinct loopback ports.

## Install

From an elevated PowerShell prompt, install the machine-level package:

```powershell
.\host-services\install\Install-WakePlayHost.ps1
```

Then sign in as each target Windows user and install that user's profile. The
`default` profile retains the existing ports:

```powershell
C:\Tools\WakePlayHost\install\Install-WakePlayProfile.ps1 -ProfileId default
```

Additional profiles require explicit unique ports:

```powershell
C:\Tools\WakePlayHost\install\Install-WakePlayProfile.ps1 `
  -ProfileId basia -ProfileName "Basia" -DiscordPort 8865 -VibepolloPort 8875 `
  -PlaynitePort 8880
```

The profile installer copies clean Bridge sources into the current user's
`LocalAppData`, runs interactive credential configuration when necessary,
registers per-user logon tasks and adds the loopback endpoints to the Gateway
profile registry. Restart the Gateway after changing the registry.

Older single-profile installations that already use the flat `C:\Tools`
layout can add only the Playnite component without replacing credentials:

```powershell
.\host-services\install\Install-FlatLayoutPlayniteBridge.ps1
```

The compatibility installer preserves `gateway.json`, certificates and paired
clients. It does not restart Gateway or Playnite.

Wake & Play lists the registered display names under Host integrations and
stores the selected profile independently for every Moonlight host.

Authenticated API calls may select a profile with `X-WakePlay-Profile`. When
the header is absent, Gateway uses `default` for backward compatibility.

## Security boundary

Only Gateway may listen on the private LAN. Bridge endpoints must remain on
`127.0.0.1` or `localhost`. Do not commit generated configuration, `*.dpapi`,
TLS keys, certificates, client records, logs, exports, diagnostics or state.

Run the host tests with:

```powershell
python -m unittest discover host-services\tests
python host-services\gateway\test_gateway.py
```
