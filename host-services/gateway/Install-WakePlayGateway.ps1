param(
    [string]$InstallDirectory = "C:\Tools\WakePlayGateway",
    [int]$Port = 8785,
    [switch]$SkipFirewall,
    [switch]$SkipScheduledTask
)

$ErrorActionPreference = "Stop"
$sourceDirectory = $PSScriptRoot
$files = @(
    "wakeplay_gateway.py",
    "Start-WakePlayGateway.ps1",
    "gateway.example.json",
    "README.md"
)

New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
foreach ($name in $files) {
    Copy-Item -LiteralPath (Join-Path $sourceDirectory $name) `
        -Destination (Join-Path $InstallDirectory $name) -Force
}

$configPath = Join-Path $InstallDirectory "gateway.json"
if (-not (Test-Path -LiteralPath $configPath)) {
    $config = Get-Content -LiteralPath (Join-Path $InstallDirectory "gateway.example.json") -Raw |
        ConvertFrom-Json
    $config.listen_port = $Port
    $config | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $configPath -Encoding UTF8
}

$certificate = Join-Path $InstallDirectory "gateway-cert.pem"
$privateKey = Join-Path $InstallDirectory "gateway-key.pem"
if (-not (Test-Path -LiteralPath $certificate) -or -not (Test-Path -LiteralPath $privateKey)) {
    $openssl = (Get-Command openssl.exe -ErrorAction Stop).Source
    & $openssl req -x509 -newkey rsa:3072 -sha256 -nodes `
        -keyout $privateKey -out $certificate -days 825 `
        -subj "/CN=Wake and Play Host Gateway" `
        -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
    if ($LASTEXITCODE -ne 0) { throw "OpenSSL certificate generation failed." }
}

# The private key and pairing database must remain readable only by the current
# Windows account, SYSTEM, and local administrators.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User
foreach ($path in @($privateKey, $configPath)) {
    # Use stable SIDs so the ACL works on localized Windows installations.
    & icacls.exe $path /inheritance:r /grant:r `
        "*$($currentSid.Value):(F)" "*S-1-5-18:(F)" "*S-1-5-32-544:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to secure $path" }
}

if (-not $SkipScheduledTask) {
    $taskName = "Wake & Play Host Gateway"
    $arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"" +
        (Join-Path $InstallDirectory "Start-WakePlayGateway.ps1") + "`" -NoPairing"
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
        -ExecutionTimeLimit (New-TimeSpan -Days 3650)
    $principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType S4U -RunLevel Limited
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
        -Settings $settings -Principal $principal -Force | Out-Null
}

if (-not $SkipFirewall) {
    $ruleName = "Wake & Play Host Gateway (Private LAN)"
    Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue |
        Remove-NetFirewallRule -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow `
        -Profile Private -Protocol TCP -LocalPort $Port -RemoteAddress LocalSubnet | Out-Null
}

$pairingCode = [string](Get-Random -Minimum 100000 -Maximum 999999)
$startScript = Join-Path $InstallDirectory "Start-WakePlayGateway.ps1"
$logPath = Join-Path $InstallDirectory "gateway.log"
$errorPath = Join-Path $InstallDirectory "gateway-error.log"
$process = Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -PassThru `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $startScript,
        "-PairingCode", $pairingCode) `
    -RedirectStandardOutput $logPath -RedirectStandardError $errorPath

Start-Sleep -Milliseconds 900
if ($process.HasExited) {
    $details = if (Test-Path -LiteralPath $errorPath) { Get-Content -LiteralPath $errorPath -Raw } else { "" }
    throw "Gateway exited during startup. $details"
}

[pscustomobject]@{
    installed = $true
    directory = $InstallDirectory
    port = $Port
    process_id = $process.Id
    pairing_code = $pairingCode
    pairing_expires_minutes = 10
    scheduled_task = (-not $SkipScheduledTask)
    firewall_rule = (-not $SkipFirewall)
}
