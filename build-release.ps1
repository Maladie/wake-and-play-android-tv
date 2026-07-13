param(
    [string]$Keystore = "$HOME\.android\debug.keystore",
    [string]$StorePassword = "android",
    [string]$KeyAlias = "androiddebugkey",
    [string]$KeyPassword = "android"
)

$ErrorActionPreference = "Stop"
$resolvedKeystore = (Resolve-Path -LiteralPath $Keystore).Path

$previous = @{
    Keystore = $env:WAKE_AND_PLAY_KEYSTORE
    StorePassword = $env:WAKE_AND_PLAY_STORE_PASSWORD
    KeyAlias = $env:WAKE_AND_PLAY_KEY_ALIAS
    KeyPassword = $env:WAKE_AND_PLAY_KEY_PASSWORD
}

try {
    $env:WAKE_AND_PLAY_KEYSTORE = $resolvedKeystore
    $env:WAKE_AND_PLAY_STORE_PASSWORD = $StorePassword
    $env:WAKE_AND_PLAY_KEY_ALIAS = $KeyAlias
    $env:WAKE_AND_PLAY_KEY_PASSWORD = $KeyPassword

    & "$PSScriptRoot\gradlew.bat" :app:assembleRelease
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed with exit code $LASTEXITCODE"
    }

    Write-Host "Release APK: $PSScriptRoot\app\build\outputs\apk\release\app-release.apk"
}
finally {
    $env:WAKE_AND_PLAY_KEYSTORE = $previous.Keystore
    $env:WAKE_AND_PLAY_STORE_PASSWORD = $previous.StorePassword
    $env:WAKE_AND_PLAY_KEY_ALIAS = $previous.KeyAlias
    $env:WAKE_AND_PLAY_KEY_PASSWORD = $previous.KeyPassword
}
