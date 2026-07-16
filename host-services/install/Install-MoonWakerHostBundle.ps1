#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')]
    [string]$ProfileId = "default",
    [string]$ProfileName = "",
    [int]$GatewayPort = 8785,
    [int]$DiscordPort = 0,
    [int]$VibepolloPort = 0,
    [int]$PlaynitePort = 0,
    [string]$PlayniteDirectory = "",
    [switch]$SkipDiscord,
    [switch]$SkipVibepollo,
    [switch]$SkipPlaynite,
    [switch]$SkipFirewall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Assert-AdministratorAndInteractiveUser {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "MoonWaker Host Installer requires administrator privileges."
    }
    try {
        $interactiveUser = [string](Get-CimInstance Win32_ComputerSystem).UserName
        if (-not [string]::IsNullOrWhiteSpace($interactiveUser) -and
            -not $identity.Name.Equals($interactiveUser, [StringComparison]::OrdinalIgnoreCase)) {
            throw "The installer is elevated as '$($identity.Name)', but the active game profile is '$interactiveUser'. Sign in and elevate with the same Windows account."
        }
    } catch [Microsoft.Management.Infrastructure.CimException] {
        Write-Warning "The active Windows account could not be verified: $($_.Exception.Message)"
    }
}

function Get-PortFromEndpoint {
    param([object]$Entry, [string]$Property)
    if ($null -eq $Entry -or $null -eq $Entry.PSObject.Properties[$Property]) { return 0 }
    $uri = $null
    if ([uri]::TryCreate([string]$Entry.$Property, [UriKind]::Absolute, [ref]$uri)) {
        return $uri.Port
    }
    return 0
}

function Resolve-ProfilePorts {
    param([string]$ConfigPath)
    $defaults = @(8765, 8775, 8780)
    if ($ProfileId -eq "default") {
        return @(
            $(if ($DiscordPort) { $DiscordPort } else { $defaults[0] }),
            $(if ($VibepolloPort) { $VibepolloPort } else { $defaults[1] }),
            $(if ($PlaynitePort) { $PlaynitePort } else { $defaults[2] }))
    }
    $gateway = $null
    if (Test-Path -LiteralPath $ConfigPath) {
        $gateway = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    }
    $existing = if ($gateway -and $gateway.profiles) {
        $gateway.profiles.PSObject.Properties[$ProfileId]
    } else { $null }
    if ($existing) {
        $entry = $existing.Value
        return @(
            $(if ($DiscordPort) { $DiscordPort } else { Get-PortFromEndpoint $entry "discord_bridge" }),
            $(if ($VibepolloPort) { $VibepolloPort } else { Get-PortFromEndpoint $entry "vibepollo_bridge" }),
            $(if ($PlaynitePort) { $PlaynitePort } else { Get-PortFromEndpoint $entry "playnite_bridge" }))
    }
    $used = [Collections.Generic.HashSet[int]]::new()
    if ($gateway -and $gateway.profiles) {
        foreach ($property in $gateway.profiles.PSObject.Properties) {
            foreach ($name in @("discord_bridge", "vibepollo_bridge", "playnite_bridge")) {
                $port = Get-PortFromEndpoint $property.Value $name
                if ($port) { [void]$used.Add($port) }
            }
        }
    }
    for ($slot = 1; $slot -le 50; $slot++) {
        $candidate = @($defaults[0] + 100 * $slot, $defaults[1] + 100 * $slot,
            $defaults[2] + 100 * $slot)
        if (-not $used.Contains($candidate[0]) -and -not $used.Contains($candidate[1]) -and
            -not $used.Contains($candidate[2])) {
            return @(
                $(if ($DiscordPort) { $DiscordPort } else { $candidate[0] }),
                $(if ($VibepolloPort) { $VibepolloPort } else { $candidate[1] }),
                $(if ($PlaynitePort) { $PlaynitePort } else { $candidate[2] }))
        }
    }
    throw "No free Bridge port set was found for the new profile."
}

function Resolve-PlayniteInstall {
    if (-not [string]::IsNullOrWhiteSpace($PlayniteDirectory)) {
        return $PlayniteDirectory.Trim()
    }
    $process = Get-Process -Name "Playnite.DesktopApp", "Playnite.FullscreenApp" `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($process -and $process.Path) { return Split-Path -Parent $process.Path }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Playnite"),
        (Join-Path $env:ProgramFiles "Playnite"),
        $(if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} "Playnite" } else { "" })
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate "Playnite.FullscreenApp.exe"))) {
            return $candidate
        }
    }
    throw "Playnite was not detected. Start Playnite or choose its installation directory."
}

function Protect-MachineText {
    param([Parameter(Mandatory)][string]$Value)
    Add-Type -AssemblyName System.Security
    $plain = [Text.Encoding]::UTF8.GetBytes($Value)
    $protected = [Security.Cryptography.ProtectedData]::Protect(
        $plain, $null, [Security.Cryptography.DataProtectionScope]::LocalMachine)
    return [Convert]::ToBase64String($protected)
}

function Unprotect-MachineText {
    param([Parameter(Mandatory)][string]$Value)
    Add-Type -AssemblyName System.Security
    $protected = [Convert]::FromBase64String($Value.Trim())
    $plain = [Security.Cryptography.ProtectedData]::Unprotect(
        $protected, $null, [Security.Cryptography.DataProtectionScope]::LocalMachine)
    return [Text.Encoding]::UTF8.GetString($plain)
}

function Initialize-MachineDiscordApplication {
    param([bool]$ProfileAlreadyConfigured)
    $machineRoot = Join-Path $env:ProgramData "MoonWakerHost"
    $applicationPath = Join-Path $machineRoot "discord-app.json"
    $secretPath = Join-Path $machineRoot "discord-app-secret.dpapi"
    $providedId = [string]$env:MOONWAKER_DISCORD_CLIENT_ID
    $providedSecret = [string]$env:MOONWAKER_DISCORD_CLIENT_SECRET
    if (-not [string]::IsNullOrWhiteSpace($providedId) -or
        -not [string]::IsNullOrWhiteSpace($providedSecret)) {
        if ($providedId -notmatch '^[0-9]{17,20}$' -or [string]::IsNullOrWhiteSpace($providedSecret)) {
            throw "Both Discord Client ID and Client Secret are required when updating the machine application."
        }
        New-Item -ItemType Directory -Path $machineRoot -Force | Out-Null
        [ordered]@{ client_id = $providedId } | ConvertTo-Json |
            Set-Content -LiteralPath $applicationPath -Encoding UTF8
        Protect-MachineText $providedSecret |
            Set-Content -LiteralPath $secretPath -Encoding ASCII
        $currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        foreach ($path in @($applicationPath, $secretPath)) {
            & icacls.exe $path /inheritance:r /grant:r `
                "*$currentSid`:(F)" "*S-1-5-18:(F)" "*S-1-5-32-544:(F)" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Unable to secure machine Discord application data." }
        }
        return
    }
    if ((Test-Path -LiteralPath $applicationPath) -and (Test-Path -LiteralPath $secretPath)) {
        $application = Get-Content -LiteralPath $applicationPath -Raw | ConvertFrom-Json
        $env:MOONWAKER_DISCORD_CLIENT_ID = [string]$application.client_id
        $env:MOONWAKER_DISCORD_CLIENT_SECRET = Unprotect-MachineText `
            (Get-Content -LiteralPath $secretPath -Raw)
        return
    }
    if (-not $ProfileAlreadyConfigured) {
        throw "Discord application data is required once for this computer."
    }
}

Assert-AdministratorAndInteractiveUser
foreach ($command in @("python.exe", "openssl.exe")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command is required but was not found in PATH."
    }
}

$packageRoot = Split-Path -Parent $PSScriptRoot
$hostInstaller = Join-Path $PSScriptRoot "Install-WakePlayHost.ps1"
$profileInstaller = Join-Path $PSScriptRoot "Install-WakePlayProfile.ps1"
if (-not (Test-Path -LiteralPath $hostInstaller) -or -not (Test-Path -LiteralPath $profileInstaller)) {
    throw "The embedded MoonWaker host package is incomplete."
}

$hostRoot = "C:\Tools\WakePlayHost"
$canonicalGateway = Join-Path $hostRoot "gateway"
$legacyGateway = "C:\Tools\WakePlayGateway"
$gatewayDirectory = if ((Test-Path -LiteralPath (Join-Path $legacyGateway "gateway.json")) -and
    -not (Test-Path -LiteralPath (Join-Path $canonicalGateway "gateway.json"))) {
    $legacyGateway
} else { $canonicalGateway }
$gatewayConfig = Join-Path $gatewayDirectory "gateway.json"
$ports = Resolve-ProfilePorts $gatewayConfig
$profileRoot = Join-Path $env:LOCALAPPDATA "WakePlayHost\profiles\$ProfileId"
if (-not $SkipDiscord) {
    Initialize-MachineDiscordApplication `
        (Test-Path -LiteralPath (Join-Path $profileRoot "discord\discord_bridge_config.json"))
}
$resolvedPlaynite = if ($SkipPlaynite) { "" } else { Resolve-PlayniteInstall }
if (-not $SkipPlaynite) {
    $connector = Join-Path $resolvedPlaynite "Extensions\SunshinePlaynite\SunshinePlaynite.psm1"
    if (-not (Test-Path -LiteralPath $connector)) {
        throw "Sunshine Playnite Connector was not found in $resolvedPlaynite."
    }
}

if ([string]::IsNullOrWhiteSpace($ProfileName)) { $ProfileName = $env:USERNAME }
try {
    Write-Host "Installing machine Gateway and Bridge package..."
    & $hostInstaller -InstallDirectory $hostRoot -GatewayDirectory $gatewayDirectory `
        -GatewayPort $GatewayPort -SkipFirewall:$SkipFirewall -SkipStart

    Write-Host "Installing integration profile '$ProfileId'..."
    & $profileInstaller -ProfileId $ProfileId -ProfileName $ProfileName `
        -DiscordPort $ports[0] -VibepolloPort $ports[1] -PlaynitePort $ports[2] `
        -GatewayConfigPath $gatewayConfig -SkipDiscord:$SkipDiscord `
        -SkipVibepollo:$SkipVibepollo -SkipPlaynite:$SkipPlaynite `
        -NonInteractiveConfiguration -SkipStart

    if (-not $SkipPlaynite) {
        $installedPatch = Join-Path $env:LOCALAPPDATA `
            "WakePlayHost\profiles\$ProfileId\playnite\Install-WakePlayConnectorPatch.ps1"
        Write-Host "Updating the installed Playnite Connector..."
        & $installedPatch -PlayniteDirectory $resolvedPlaynite
    }

    [ordered]@{
        ok = $true
        profile_id = $ProfileId
        profile_name = $ProfileName
        gateway_directory = $gatewayDirectory
        discord_port = if ($SkipDiscord) { 0 } else { $ports[0] }
        vibepollo_port = if ($SkipVibepollo) { 0 } else { $ports[1] }
        playnite_port = if ($SkipPlaynite) { 0 } else { $ports[2] }
        restart_required = $true
    } | ConvertTo-Json -Compress | ForEach-Object { "MOONWAKER_INSTALL_RESULT=$_" }
} finally {
    $env:MOONWAKER_DISCORD_CLIENT_SECRET = $null
    $env:MOONWAKER_VIBEPOLLO_TOKEN = $null
}
