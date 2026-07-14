#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9._-]{1,64}$')]
    [string]$ProfileId = "default",
    [string]$ProfileName = "",
    [int]$DiscordPort = 8765,
    [int]$VibepolloPort = 8775,
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA "WakePlayHost\profiles"),
    [string]$GatewayConfigPath = "C:\Tools\WakePlayHost\gateway\gateway.json",
    [switch]$SkipDiscord,
    [switch]$SkipVibepollo,
    [switch]$SkipGatewayRegistration,
    [switch]$SkipStart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$profileDisplayName = if ([string]::IsNullOrWhiteSpace($ProfileName)) {
    $ProfileId
} else {
    $ProfileName.Trim()
}
if ($profileDisplayName.Length -gt 80 -or $profileDisplayName -match '[\x00-\x1f\x7f]') {
    throw "Profile name must contain at most 80 printable characters."
}

if ($DiscordPort -lt 1024 -or $DiscordPort -gt 65535 -or
    $VibepolloPort -lt 1024 -or $VibepolloPort -gt 65535) {
    throw "Bridge ports must be between 1024 and 65535."
}
if (-not $SkipDiscord -and -not $SkipVibepollo -and $DiscordPort -eq $VibepolloPort) {
    throw "Discord and Vibepollo Bridge must use different ports."
}
if ($ProfileId -ne "default" -and
    -not $PSBoundParameters.ContainsKey("DiscordPort") -and
    -not $PSBoundParameters.ContainsKey("VibepolloPort")) {
    throw "Additional profiles require explicit, unique -DiscordPort and -VibepolloPort values."
}

$hostServicesRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $hostServicesRoot "bridges"
if (-not (Test-Path -LiteralPath $sourceRoot)) {
    $sourceRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "bridge-source"
}
if (-not (Test-Path -LiteralPath $sourceRoot)) {
    throw "Bridge source package was not found next to the installer."
}

$profileRoot = Join-Path $InstallRoot $ProfileId
New-Item -ItemType Directory -Path $profileRoot -Force | Out-Null

function Install-BridgeFiles {
    param([string]$Name, [string[]]$Files)
    $source = Join-Path $sourceRoot $Name
    $destination = Join-Path $profileRoot $Name
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    foreach ($file in $Files) {
        Copy-Item -LiteralPath (Join-Path $source $file) -Destination $destination -Force
    }
    return $destination
}

function Set-ConfigPort {
    param([string]$Path, [string]$Property, [int]$Port)
    $config = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($null -eq $config.PSObject.Properties[$Property]) {
        $config | Add-Member -NotePropertyName $Property -NotePropertyValue $Port
    } else {
        $config.$Property = $Port
    }
    $config | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Register-BridgeTask {
    param([string]$Name, [string]$StartScript)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument (
        "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$StartScript`"")
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $identity
    $settings = New-ScheduledTaskSettingsSet -RestartCount 3 `
        -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Days 3650)
    $taskPrincipal = New-ScheduledTaskPrincipal -UserId $identity `
        -LogonType Interactive -RunLevel Limited
    Register-ScheduledTask -TaskName $Name -Action $action -Trigger $trigger `
        -Settings $settings -Principal $taskPrincipal -Force | Out-Null
}

$discordDirectory = $null
if (-not $SkipDiscord) {
    $discordDirectory = Install-BridgeFiles "discord" @(
        "DiscordBridge.ps1", "Configure-DiscordBridge.ps1",
        "discord_bridge_config.example.json", "Start-DiscordBridge.ps1",
        "Stop-DiscordBridge.ps1", "Test-DiscordBridge.ps1", "WindowsAudio.cs", "README.md")
    $discordConfig = Join-Path $discordDirectory "discord_bridge_config.json"
    if (-not (Test-Path -LiteralPath $discordConfig)) {
        & (Join-Path $discordDirectory "Configure-DiscordBridge.ps1")
    }
    Set-ConfigPort $discordConfig "port" $DiscordPort
    Register-BridgeTask "Wake & Play Discord Bridge ($ProfileId)" `
        (Join-Path $discordDirectory "Start-DiscordBridge.ps1")
}

$vibepolloDirectory = $null
if (-not $SkipVibepollo) {
    $vibepolloDirectory = Install-BridgeFiles "vibepollo" @(
        "VibepolloBridge.ps1", "VibepolloTransport.py",
        "Configure-VibepolloBridge.ps1", "config.example.json",
        "Start-VibepolloBridge.ps1", "Stop-VibepolloBridge.ps1",
        "Test-VibepolloBridge.ps1", "README.md")
    $vibepolloConfig = Join-Path $vibepolloDirectory "config.json"
    if (-not (Test-Path -LiteralPath $vibepolloConfig)) {
        & (Join-Path $vibepolloDirectory "Configure-VibepolloBridge.ps1")
    }
    Set-ConfigPort $vibepolloConfig "listen_port" $VibepolloPort
    Register-BridgeTask "Wake & Play Vibepollo Bridge ($ProfileId)" `
        (Join-Path $vibepolloDirectory "Start-VibepolloBridge.ps1")
}

if (-not $SkipGatewayRegistration) {
    try {
        $gateway = Get-Content -LiteralPath $GatewayConfigPath -Raw | ConvertFrom-Json
        if ($null -eq $gateway.PSObject.Properties["profiles"]) {
            $gateway | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{})
        }
        $entry = [pscustomobject]@{
            name = $profileDisplayName
            discord_bridge = if ($SkipDiscord) { "" } else { "http://127.0.0.1:$DiscordPort" }
            vibepollo_bridge = if ($SkipVibepollo) { "" } else { "http://127.0.0.1:$VibepolloPort" }
        }
        if ($null -eq $gateway.profiles.PSObject.Properties[$ProfileId]) {
            $gateway.profiles | Add-Member -NotePropertyName $ProfileId -NotePropertyValue $entry
        } else {
            $gateway.profiles.$ProfileId = $entry
        }
        $gateway | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $GatewayConfigPath -Encoding UTF8
        Write-Host "Registered profile '$ProfileId' in the Gateway configuration." -ForegroundColor Green
        Write-Warning "Restart the Gateway to load the updated profile registry."
    } catch {
        $registrationPath = Join-Path $profileRoot "gateway-profile-registration.json"
        [ordered]@{
            profile_id = $ProfileId
            name = $profileDisplayName
            discord_bridge = if ($SkipDiscord) { "" } else { "http://127.0.0.1:$DiscordPort" }
            vibepollo_bridge = if ($SkipVibepollo) { "" } else { "http://127.0.0.1:$VibepolloPort" }
        } | ConvertTo-Json | Set-Content -LiteralPath $registrationPath -Encoding UTF8
        Write-Warning "Gateway configuration could not be updated: $($_.Exception.Message)"
        Write-Warning "Registration data was written to $registrationPath for an administrator."
    }
}

if (-not $SkipStart) {
    if ($discordDirectory) { & (Join-Path $discordDirectory "Start-DiscordBridge.ps1") }
    if ($vibepolloDirectory) { & (Join-Path $vibepolloDirectory "Start-VibepolloBridge.ps1") }
}

Write-Host "Wake & Play integration profile '$ProfileId' installed for $env:USERNAME." -ForegroundColor Green
