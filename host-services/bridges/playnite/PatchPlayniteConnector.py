#!/usr/bin/env python3
"""Idempotently add WakePlay launcher snapshots to SunshinePlaynite.psm1."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path


PATCH_MARKER = "# WAKEPLAY-CONSOLE-SNAPSHOT-V1"

READER_ANCHOR = """        if ($obj.type -eq 'command' -and $obj.command -eq 'launch' -and $obj.id) {
          Register-SunshineLaunchedGame -Id $obj.id
          [UIBridge]::StartGameByGuidStringOnUIThread([string]$obj.id)
          Write-Log \"LauncherConn[$Guid]: launch dispatched for $($obj.id)\"
        }
        elseif ($obj.type -and $obj.command) {"""

READER_REPLACEMENT = """        if ($obj.type -eq 'command' -and $obj.command -eq 'launch' -and $obj.id) {
          Register-SunshineLaunchedGame -Id $obj.id
          [UIBridge]::StartGameByGuidStringOnUIThread([string]$obj.id)
          Write-Log \"LauncherConn[$Guid]: launch dispatched for $($obj.id)\"
        }
        elseif ($obj.type -eq 'command' -and $obj.command -eq 'snapshot') {
          Send-WakePlaySnapshotToLauncher -Target $Guid
          Write-Log \"LauncherConn[$Guid]: WakePlay snapshot dispatched\"
        }
        elseif ($obj.type -and $obj.command) {"""

FUNCTION_ANCHOR = "function Start-ConnectorLoop {"

SNAPSHOT_FUNCTION = r'''# WAKEPLAY-CONSOLE-SNAPSHOT-V1
function Send-WakePlaySnapshotToLauncher {
  param([Parameter(Mandatory)][string]$Target)
  try {
    $targets = @($Target)
    $plugins = @(Get-PlaynitePlugins)
    $payload = @{ type = 'plugins'; payload = $plugins } | ConvertTo-Json -Depth 6 -Compress
    Send-PayloadToLauncherConnections -Payload $payload -Targets $targets -Context 'WakePlay plugins snapshot' | Out-Null

    $categories = @(Get-PlayniteCategories)
    $payload = @{ type = 'categories'; payload = $categories } | ConvertTo-Json -Depth 6 -Compress
    Send-PayloadToLauncherConnections -Payload $payload -Targets $targets -Context 'WakePlay categories snapshot' | Out-Null

    $games = @(Get-PlayniteGames)
    $batchSize = 100
    for ($i = 0; $i -lt $games.Count; $i += $batchSize) {
      $last = [Math]::Min($i + $batchSize - 1, $games.Count - 1)
      $chunk = $games[$i..$last]
      $payload = @{ type = 'games'; payload = $chunk } | ConvertTo-Json -Depth 8 -Compress
      Send-PayloadToLauncherConnections -Payload $payload -Targets $targets -Context 'WakePlay games snapshot' | Out-Null
    }
    $complete = @{ type = 'snapshotComplete'; payload = @{ games = $games.Count } } | ConvertTo-Json -Depth 4 -Compress
    Send-PayloadToLauncherConnections -Payload $complete -Targets $targets -Context 'WakePlay snapshot complete' | Out-Null
  }
  catch {
    Write-Log "WakePlay snapshot failed for $Target`: $($_.Exception.Message)"
  }
}

'''


def patch_text(source: str) -> tuple[str, bool]:
    if PATCH_MARKER in source:
        return source, False
    missing = [name for name, anchor in (
        ("launcher reader", READER_ANCHOR),
        ("connector loop", FUNCTION_ANCHOR),
    ) if anchor not in source]
    if missing:
        raise ValueError("Unsupported Sunshine Playnite Connector; missing " + ", ".join(missing))
    patched = source.replace(READER_ANCHOR, READER_REPLACEMENT, 1)
    patched = patched.replace(FUNCTION_ANCHOR, SNAPSHOT_FUNCTION + FUNCTION_ANCHOR, 1)
    return patched, True


def patch_file(path: Path, apply: bool) -> str:
    resolved = path.resolve()
    source = resolved.read_text(encoding="utf-8-sig")
    patched, changed = patch_text(source)
    if not changed:
        return "already-patched"
    if not apply:
        return "compatible"
    backup = resolved.with_suffix(resolved.suffix + ".wakeplay-backup")
    if not backup.exists():
        shutil.copy2(resolved, backup)
    temporary = resolved.with_suffix(resolved.suffix + ".wakeplay-new")
    temporary.write_text(patched, encoding="utf-8")
    temporary.replace(resolved)
    return "patched"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("module", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    print(patch_file(args.module, args.apply))


if __name__ == "__main__":
    main()
