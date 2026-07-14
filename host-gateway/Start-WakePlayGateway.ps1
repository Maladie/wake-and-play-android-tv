param(
    [switch]$NoPairing,
    [ValidatePattern('^[0-9]{6}$')]
    [string]$PairingCode,
    [string]$ConfigPath = (Join-Path $PSScriptRoot "gateway.json")
)

$ErrorActionPreference = "Stop"
$python = (Get-Command python.exe -ErrorAction Stop).Source
$openssl = (Get-Command openssl.exe -ErrorAction Stop).Source
$certificate = Join-Path $PSScriptRoot "gateway-cert.pem"
$privateKey = Join-Path $PSScriptRoot "gateway-key.pem"

if (-not (Test-Path -LiteralPath $certificate) -or -not (Test-Path -LiteralPath $privateKey)) {
    & $openssl req -x509 -newkey rsa:3072 -sha256 -nodes `
        -keyout $privateKey -out $certificate -days 825 `
        -subj "/CN=Wake and Play Host Gateway" `
        -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
    if ($LASTEXITCODE -ne 0) { throw "OpenSSL certificate generation failed." }
}

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "gateway.example.json") -Destination $ConfigPath
}

$arguments = @((Join-Path $PSScriptRoot "wakeplay_gateway.py"), "--config", $ConfigPath)
if (-not $NoPairing) {
    if (-not $PairingCode) {
        $PairingCode = [string](Get-Random -Minimum 100000 -Maximum 999999)
    }
    $arguments += @("--pairing-code", $PairingCode)
    Write-Host ""
    Write-Host "Wake & Play pairing code: $PairingCode" -ForegroundColor Cyan
    Write-Host "The code remains valid for 10 minutes while this process is running."
    Write-Host ""
}

& $python @arguments
