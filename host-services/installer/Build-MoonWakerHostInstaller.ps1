#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot "dist")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$hostServices = Split-Path -Parent $PSScriptRoot
$staging = Join-Path $env:TEMP ("MoonWakerInstallerBuild-" + [guid]::NewGuid().ToString("N"))
$payloadRoot = Join-Path $staging "payload\host-services"
$archive = Join-Path $staging "payload.zip"

try {
    New-Item -ItemType Directory -Path $payloadRoot, $OutputDirectory -Force | Out-Null
    foreach ($directory in @("gateway", "bridges", "install")) {
        Copy-Item -LiteralPath (Join-Path $hostServices $directory) `
            -Destination $payloadRoot -Recurse -Force
    }
    Compress-Archive -Path (Join-Path $staging "payload\host-services") `
        -DestinationPath $archive -CompressionLevel Optimal

    $compiler = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    if (-not (Test-Path -LiteralPath $compiler)) {
        $compiler = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    }
    if (-not (Test-Path -LiteralPath $compiler)) { throw ".NET Framework C# compiler was not found." }

    $output = Join-Path $OutputDirectory "MoonWakerHostInstaller.exe"
    & $compiler /nologo /target:winexe /optimize+ "/out:$output" `
        "/win32manifest:$(Join-Path $PSScriptRoot 'MoonWakerHostInstaller.manifest')" `
        "/resource:$archive,MoonWakerHost.Payload" `
        /reference:System.dll /reference:System.Core.dll /reference:System.Drawing.dll `
        /reference:System.Windows.Forms.dll /reference:System.IO.Compression.dll `
        /reference:System.IO.Compression.FileSystem.dll `
        (Join-Path $PSScriptRoot "MoonWakerHostInstaller.cs")
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output)) {
        throw "MoonWaker Host Installer compilation failed."
    }
    Get-Item -LiteralPath $output
} finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
}
