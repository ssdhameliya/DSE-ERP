param([Parameter(Mandatory=$true)][string]$EnvironmentFile)
$ErrorActionPreference='Stop';Get-Content -LiteralPath $EnvironmentFile | Where-Object {$_ -match '^[A-Za-z_][A-Za-z0-9_]*='} | ForEach-Object {$name,$value=$_.Split('=',2);[Environment]::SetEnvironmentVariable($name,$value,'Process')}
if(!$env:DSE_SERVER_ADDRESS){$env:DSE_SERVER_ADDRESS='0.0.0.0'}
& java -jar (Join-Path $PSScriptRoot 'dse-erp-server.jar')
exit $LASTEXITCODE
