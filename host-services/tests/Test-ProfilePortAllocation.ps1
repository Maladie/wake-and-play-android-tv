#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$installer = Join-Path (Split-Path -Parent $PSScriptRoot) `
    "install\Install-MoonWakerHostBundle.ps1"
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile(
    $installer, [ref]$tokens, [ref]$errors)
if ($errors) { throw "Installer syntax is invalid." }

foreach ($name in @("Get-PortFromEndpoint", "Resolve-ProfilePorts")) {
    $function = $ast.Find({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq $name
    }, $true)
    if (-not $function) { throw "Function $name was not found." }
    Invoke-Expression $function.Extent.Text
}

$script:ProfileId = "second-profile"
$script:DiscordPort = 0
$script:VibepolloPort = 0
$script:PlaynitePort = 0
$ports = @(Resolve-ProfilePorts (Join-Path $env:TEMP "missing-moonwaker-gateway.json"))
if ($ports.Count -ne 3 -or $ports[0] -ne 8865 -or
    $ports[1] -ne 8875 -or $ports[2] -ne 8880) {
    throw "Unexpected second-profile ports: $($ports -join ', ')"
}
Write-Host "Second-profile port allocation passed: $($ports -join ', ')"
