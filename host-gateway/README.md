# Wake & Play Host Gateway

The gateway exposes a small authenticated HTTPS API to Wake & Play while keeping
Discord Bridge and Vibepollo Bridge bound to `127.0.0.1`.

## Start and pair

1. Ensure the existing Discord/Vibepollo bridges are installed and running.
2. Run `Start-WakePlayGateway.ps1` from a normal PowerShell prompt.
3. Enter the displayed six-digit code in Wake & Play within ten minutes.

For a persistent installation, run `Install-WakePlayGateway.ps1` from an
elevated PowerShell prompt. It installs to `C:\Tools\WakePlayGateway`, secures
the private files, adds a private-LAN firewall rule, registers a logon task,
and starts one ten-minute pairing session.

The first start generates a private TLS certificate and `gateway.json`. These
files are intentionally ignored by Git. Paired client tokens are stored only as
SHA-256 hashes. Wake & Play pins the certificate presented during pairing.

The Windows firewall must allow inbound TCP traffic to the configured port on
the private network. Do not expose this port to the Internet.

## API v1

- `GET /api/v1/hello` — unauthenticated discovery response.
- `POST /api/v1/pair` — exchanges a short-lived pairing code for a client token.
- `GET /api/v1/capabilities` — reports available local bridges.
- `GET /api/v1/vibepollo/repair/status` — Vibepollo health summary.
- `POST /api/v1/vibepollo/repair/{restart|reset-display|export-logs}` — repair action.
- `GET /api/v1/discord/status` — Bridge, RPC and authorization status.
- `GET /api/v1/discord/home` — favorites, recent channels and servers.
- `GET /api/v1/discord/channels?guild_id=...` — allow-listed voice channels.
- `GET /api/v1/discord/voice` — selected channel and voice state.
- `GET /api/v1/discord/audio` — Windows and Discord audio state/devices.
- `POST /api/v1/discord/start` — safely starts Discord in the Bridge user session.
- `POST /api/v1/discord/{connect|join|leave|mute|deafen}` — explicit Discord action.
- `POST /api/v1/discord/{user-volume|user-mute}` — validated participant control.
- `POST /api/v1/discord/audio/{select|volume|mute}` — allow-listed device and
  Windows master-volume control.
- `GET /api/v1/virtualhere/state` — VirtualHere client, server and device state.
- `POST /api/v1/virtualhere/{use|stop|auto|restart}` — validated USB-device or
  client action.

All endpoints except `hello` and `pair` require `Authorization: Bearer ...`.
Mutating Discord and repair actions additionally require a unique
`X-Request-Id` header. Discord IDs are validated as numeric snowflakes and the
gateway never exposes a general-purpose Bridge proxy.
Participant volume accepts an absolute value from 0 to 200 in 10% increments;
VirtualHere addresses and audio-device IDs are separately validated before
they reach the Bridge.

## Discord profiles

Deploy one Gateway per physical host and one Discord Bridge per Windows/Discord
profile. A Bridge must run in the same interactive Windows session as its
Discord client; named-pipe RPC and DPAPI credentials cannot be shared across
profiles. The current `discord_bridge` setting represents profile `default`.
A future profile registry can map additional stable profile IDs to distinct
loopback ports while retaining the same Gateway certificate and TV pairing.
