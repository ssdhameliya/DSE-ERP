param([string]$Output=(Join-Path $PSScriptRoot '..\server-package'))
$ErrorActionPreference='Stop';$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& mvn -B -ntp -pl server -am package -DskipTests
if($LASTEXITCODE -ne 0){throw 'Server build failed'}
New-Item -ItemType Directory -Force -Path $Output | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'server\target\dse-erp-server.jar') -Destination (Join-Path $Output 'dse-erp-server.jar') -Force
Copy-Item -LiteralPath (Join-Path $project 'scripts\run-company-server.ps1') -Destination $Output -Force
Copy-Item -LiteralPath (Join-Path $project 'scripts\install-company-server-service.ps1') -Destination $Output -Force
Copy-Item -LiteralPath (Join-Path $project 'scripts\dse-erp-server.env.example') -Destination $Output -Force
Copy-Item -LiteralPath (Join-Path $project 'scripts\dse-erp-server.service') -Destination $Output -Force
Write-Host "Company-server package ready: $Output"
