#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$InstallDirectory = "C:\Tools\WakePlayHost",
    [int]$GatewayPort = 8785,
    [switch]$SkipFirewall,
    [switch]$SkipScheduledTask
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$principal = [Security.Principal.WindowsPrincipal]::new(
    [Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Install-WakePlayHost.ps1 must run from an elevated PowerShell prompt."
}

$hostServicesRoot = Split-Path -Parent $PSScriptRoot
$gatewaySource = Join-Path $hostServicesRoot "gateway"
$bridgeSource = Join-Path $hostServicesRoot "bridges"
if (-not (Test-Path -LiteralPath $gatewaySource) -or
    -not (Test-Path -LiteralPath $bridgeSource)) {
    throw "Run this installer from the versioned host-services package."
}

$gatewayDirectory = Join-Path $InstallDirectory "gateway"
$sourceDirectory = Join-Path $InstallDirectory "bridge-source"
$installScripts = Join-Path $InstallDirectory "install"
New-Item -ItemType Directory -Path $InstallDirectory, $sourceDirectory, $installScripts -Force | Out-Null

Copy-Item -LiteralPath (Join-Path $bridgeSource "discord") `
    -Destination $sourceDirectory -Recurse -Force
Copy-Item -LiteralPath (Join-Path $bridgeSource "vibepollo") `
    -Destination $sourceDirectory -Recurse -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "Install-WakePlayProfile.ps1") `
    -Destination $installScripts -Force

$gatewayInstaller = Join-Path $gatewaySource "Install-WakePlayGateway.ps1"
& $gatewayInstaller -InstallDirectory $gatewayDirectory -Port $GatewayPort `
    -SkipFirewall:$SkipFirewall -SkipScheduledTask:$SkipScheduledTask

Write-Host "Wake & Play host components installed in $InstallDirectory" -ForegroundColor Green
Write-Host "Next, run install\Install-WakePlayProfile.ps1 as each target Windows user."
