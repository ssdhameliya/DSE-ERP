param([string]$Version = "")
$Script = Join-Path $PSScriptRoot 'scripts/package-windows.ps1'
& $Script -Version $Version
exit $LASTEXITCODE
