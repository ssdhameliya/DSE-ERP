param([Parameter(Mandatory=$true)][string]$EnvironmentFile,[string]$ServiceName='DSEERPCompanyServer')
$ErrorActionPreference='Stop';$runner=(Join-Path $PSScriptRoot 'run-company-server.ps1');$bin="powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$runner`" -EnvironmentFile `"$EnvironmentFile`""
if(Get-Service -Name $ServiceName -ErrorAction SilentlyContinue){throw "Service $ServiceName already exists"}
New-Service -Name $ServiceName -BinaryPathName $bin -DisplayName 'DSE ERP Company Server' -StartupType Automatic
Start-Service -Name $ServiceName;Write-Host "Installed and started $ServiceName"
