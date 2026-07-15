# Playnite Bridge

This per-profile loopback service uses the existing `launcher` role exposed by
the installed Sunshine Playnite Connector. It talks to Playnite through the
extension's named pipe and never automates the Playnite UI with keystrokes.

The Bridge exposes library pages, current-game state, readiness and lifecycle
events to the authenticated host Gateway. Commands are limited to launching a
Playnite GUID, graceful game stop and restoring Playnite Fullscreen. Forced
process termination is intentionally unavailable.

The readiness response is privacy-sensitive. `ready=false` means MoonWaker must
keep its opaque loading surface visible. A timeout is not permission to reveal
the Windows desktop.

The current Sunshine connector already supports the launcher handshake,
`launch` commands and game start/stop status. The next integration step extends
that connector with `Install-WakePlayConnectorPatch.ps1`. The patch is
idempotent, validates exact structural anchors and creates a
`.wakeplay-backup` before changing the installed module. It reuses the
connector's existing metadata functions to mirror library snapshots only to the
requesting Bridge. Restart Playnite after applying it.

Graceful stop/show commands and verified window readiness remain separate next
steps; the snapshot patch does not alter those behaviors.
