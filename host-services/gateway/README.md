# Wake & Play Host Gateway

Gateway exposes a small authenticated HTTPS API to Wake & Play while keeping
Discord Bridge and Vibepollo Bridge bound to `127.0.0.1`.

For a complete installation use `../install/Install-WakePlayHost.ps1`. For
development, run `Start-WakePlayGateway.ps1` and enter its six-digit code under
Wake & Play's host integrations panel within ten minutes.

The first start generates `gateway.json`, a private TLS key and a certificate.
They are ignored by Git. Paired client tokens are stored only as SHA-256 hashes,
and Wake & Play pins the certificate received during pairing. The private-LAN
firewall rule must never be exposed to the Internet.

## Profiles

Use one Gateway per physical host and one Bridge instance per Windows
integration profile. `profiles` maps stable profile IDs to distinct loopback
ports. Authenticated requests select a profile with `X-WakePlay-Profile`; the
header defaults to `default` when omitted. Unknown IDs and non-loopback Bridge
URLs are rejected.

A Discord Bridge must run in the same interactive Windows session as its
Discord client because named-pipe RPC and DPAPI credentials are user-bound.
Discord itself can only own one machine-global RPC endpoint at a time, so fully
exit it in the previous profile before switching. Vibepollo also needs a
per-profile Bridge when its credentials or runtime state differ.

## API v1

- `GET /api/v1/hello` - unauthenticated discovery response.
- `POST /api/v1/pair` - exchanges a short-lived pairing code for a client token.
- `GET /api/v1/capabilities` - reports Gateway, selected profile and Bridges.
- `GET /api/v1/vibepollo/repair/status` - Vibepollo health summary.
- `POST /api/v1/vibepollo/repair/{restart|reset-display|export-logs}` - repair action.
- `GET /api/v1/discord/status` - Bridge, RPC and authorization status.
- `GET /api/v1/discord/home` - favorites, recent channels and servers.
- `GET /api/v1/discord/channels?guild_id=...` - allow-listed voice channels.
- `GET /api/v1/discord/voice` - selected channel and voice state.
- `GET /api/v1/discord/audio` - Windows and Discord audio state/devices.
- `POST /api/v1/discord/start` - starts Discord in the Bridge user session.
- `POST /api/v1/discord/{connect|join|leave|mute|deafen}` - Discord action.
- `POST /api/v1/discord/{user-volume|user-mute}` - participant control.
- `POST /api/v1/discord/audio/{select|volume|mute}` - audio control.
- `GET /api/v1/virtualhere/state` - VirtualHere state and shared devices.
- `POST /api/v1/virtualhere/{use|stop|auto|restart}` - VirtualHere action.

All endpoints except `hello` and `pair` require `Authorization: Bearer ...`.
Mutating actions also require a unique `X-Request-Id`. Discord snowflakes,
participant volume, audio device IDs and VirtualHere addresses are validated;
Gateway never exposes a general-purpose Bridge proxy.
