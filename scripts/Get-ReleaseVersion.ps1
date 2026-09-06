$config=Join-Path $PSScriptRoot '..\.mvn\maven.config'
$line=Get-Content -LiteralPath $config | Where-Object {$_ -match '^-Drevision='} | Select-Object -First 1
if(!$line){throw 'Missing -Drevision in .mvn/maven.config'}
($line -replace '^-Drevision=','').Trim()
