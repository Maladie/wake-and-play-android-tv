#requires -Version 5.1
[CmdletBinding()]
param([string]$OutputDirectory = (Join-Path $PSScriptRoot "dist"))
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$compiler = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path -LiteralPath $compiler)) { $compiler = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe" }
if (-not (Test-Path -LiteralPath $compiler)) { throw ".NET Framework C# compiler was not found." }
$output = Join-Path $OutputDirectory "MoonWakerHostControl.exe"
& $compiler /nologo /target:winexe /optimize+ "/out:$output" `
    "/win32manifest:$(Join-Path $PSScriptRoot 'MoonWakerHostControl.manifest')" `
    /reference:System.dll /reference:System.Core.dll /reference:System.Drawing.dll `
    /reference:System.Windows.Forms.dll /reference:System.Web.Extensions.dll `
    (Join-Path $PSScriptRoot "MoonWakerHostControl.cs")
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $output)) { throw "MoonWaker Host Control compilation failed." }
Get-Item -LiteralPath $output
