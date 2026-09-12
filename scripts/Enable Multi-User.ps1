param(
 [Parameter(Mandatory=$true)][string]$Workspace,
 [Parameter(Mandatory=$true)][string]$ServerUrl,
 [Parameter(Mandatory=$true)][string]$AdminToken,
 [string]$LocalDatabaseUrl='',
 [string]$LocalDatabaseUser='',
 [ValidateSet('UAT','PROD')][string]$Environment='UAT',
 [string]$ExpectedVersion='', [string]$PostgresHome='', [switch]$Finalize)
$ErrorActionPreference='Stop'
$ServerUrl=$ServerUrl.TrimEnd('/')
if([string]::IsNullOrWhiteSpace($ExpectedVersion)){
 $versionConfig=Join-Path $PSScriptRoot '..\.mvn\maven.config'
 if(Test-Path -LiteralPath $versionConfig){
   $revision=Get-Content -LiteralPath $versionConfig | Where-Object {$_ -match '^-Drevision='} | Select-Object -First 1
   if($revision){$ExpectedVersion=($revision -replace '^-Drevision=','').Trim()}
 }
}
if([string]::IsNullOrWhiteSpace($ExpectedVersion)){throw 'Expected release version could not be resolved. Pass -ExpectedVersion explicitly.'}
$health=Invoke-RestMethod -Uri "$ServerUrl/api/runtime/health" -Method Get
if(!$health.ready){throw 'Company server is not READY'}
if($health.service -ne 'dse-erp-server'){throw "The target is not a DSE ERP server: $($health.service)"}
if($health.apiRevision -ne 'spring-security-bearer-v5'){throw "Company server API revision is incompatible: $($health.apiRevision)"}
if($health.environment -ne $Environment){throw "Target environment mismatch. Expected $Environment; server reports $($health.environment)"}
function Convert-DseVersion([string]$Value,[string]$Label){
 if([string]::IsNullOrWhiteSpace($Value)){throw "$Label was not reported by the company server."}
 try{return [version]($Value.Trim())}catch{throw "$Label is not a valid DSE ERP semantic version: $Value"}
}
$desktopVersion=Convert-DseVersion $ExpectedVersion 'Desktop version'
$serverVersion=Convert-DseVersion ([string]$health.version) 'Server version'
$minimumText=([string]$health.minimumSupportedDesktopVersion).Trim()
if([string]::IsNullOrWhiteSpace($minimumText)){$minimumText=[string]$health.version}
$minimumVersion=Convert-DseVersion $minimumText 'Minimum supported desktop version'
if($minimumVersion -gt $serverVersion){throw "Company server compatibility policy is invalid. Minimum desktop $minimumText is newer than server $($health.version)."}
if($desktopVersion -lt $minimumVersion){throw "This desktop $ExpectedVersion is below the company server minimum $minimumText. Update the desktop before enabling Shared Client mode."}
if($desktopVersion -gt $serverVersion){throw "Company server $($health.version) is older than this desktop $ExpectedVersion. Update the company server first."}
if($desktopVersion -eq $serverVersion -and ([string]$health.buildRevision).Trim() -ne $ExpectedVersion){throw "Company server build is stale for DSE ERP $ExpectedVersion; found build $($health.buildRevision)."}
$config=Join-Path $Workspace 'Config\config.properties';if(!(Test-Path -LiteralPath $config)){throw "Workspace configuration not found: $config"}
$headers=@{Authorization="Bearer $AdminToken"}
function Put-Setting([string]$Key,[string]$Value){$json=@{value=$Value}|ConvertTo-Json -Compress;Invoke-RestMethod -Uri "$ServerUrl/api/support/settings/$([uri]::EscapeDataString($Key))" -Method Put -Headers $headers -ContentType application/json -Body $json|Out-Null}
function Put-Resource([string]$Type,[string]$Key,[string]$File){$uri="$ServerUrl/api/authority/resources/$Type/$([uri]::EscapeDataString($Key))?filename=$([uri]::EscapeDataString((Split-Path $File -Leaf)))";Invoke-RestMethod -Uri $uri -Method Put -Headers $headers -ContentType application/octet-stream -InFile $File|Out-Null}
if($Finalize){
 $backup=Join-Path $Workspace 'Backups\promotion-local-safety.pgbackup';if(!(Test-Path -LiteralPath $backup)){throw 'Promotion safety backup is missing; run the staging phase first'}
 $properties=@{};Get-Content -LiteralPath $config|Where-Object {$_ -match '^[^#!][^=]*='}|ForEach-Object {$k,$v=$_.Split('=',2);$properties[$k.Trim()]=$v}
 foreach($entry in $properties.GetEnumerator()){if($entry.Key -match '^(company\.|payment\.|invoice\.|business\.|tax\.|reference\.)'){Put-Setting $entry.Key $entry.Value}}
 foreach($assetKey in @('company.logoPath','company.signaturePath','payment.qrImagePath')){if($properties[$assetKey] -and (Test-Path -LiteralPath $properties[$assetKey])){Put-Resource 'BUSINESS_ASSET' $assetKey $properties[$assetKey];Put-Setting $assetKey "server-resource:$assetKey"}}
 $templateRoot=Join-Path $Workspace 'Templates\DocumentStudio';$tempArchives=@();try{foreach($spec in @(@('PDF_TEMPLATE',$templateRoot),@('EXCEL_TEMPLATE',(Join-Path $templateRoot 'Excel')))){if(!(Test-Path -LiteralPath $spec[1])){continue};Get-ChildItem -LiteralPath $spec[1] -Directory|Where-Object {Test-Path -LiteralPath (Join-Path $_.FullName 'template.json')}|ForEach-Object {$archive=Join-Path ([IO.Path]::GetTempPath()) ("dse-promotion-"+[guid]::NewGuid()+'.zip');Compress-Archive -Path (Join-Path $_.FullName '*') -DestinationPath $archive -Force;$tempArchives+=$archive;Put-Resource $spec[0] $_.Name $archive}}}finally{foreach($archive in $tempArchives){Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue}}
 $lines=Get-Content -LiteralPath $config | Where-Object {$_ -notmatch '^(deployment.mode|deployment.environment|server.baseUrl)='}
 $lines+=@('deployment.mode=SHARED_CLIENT',"deployment.environment=$Environment","server.baseUrl=$ServerUrl")
 $temp="$config.$ExpectedVersion.tmp";Set-Content -LiteralPath $temp -Value $lines -Encoding ISO8859-1;Move-Item -LiteralPath $temp -Destination $config -Force
 Write-Host "Promotion finalized for $Environment. Restart DSE ERP and sign in to the company server.";return
}
if([string]::IsNullOrWhiteSpace($LocalDatabaseUrl) -or [string]::IsNullOrWhiteSpace($LocalDatabaseUser)){throw 'LocalDatabaseUrl and LocalDatabaseUser are required during the staging phase; they are not required with -Finalize'}
$backupFolder=Join-Path $Workspace 'Backups';New-Item -ItemType Directory -Force -Path $backupFolder|Out-Null
$backup=Join-Path $backupFolder 'promotion-local-safety.pgbackup'
$pgDump=if($PostgresHome){Join-Path $PostgresHome 'bin\pg_dump.exe'}else{'pg_dump'}
& $pgDump '--format=custom' '--no-owner' '--no-privileges' "--username=$LocalDatabaseUser" "--file=$backup" ($LocalDatabaseUrl -replace '^jdbc:','')
if($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $backup)){throw 'Local promotion safety backup failed'}
$uploadHeaders=@{Authorization="Bearer $AdminToken";'Content-Type'='application/octet-stream'}
Invoke-RestMethod -Uri "$ServerUrl/api/authority/backups/restore/stage?filename=promotion-local-safety.pgbackup" -Method Post -Headers $uploadHeaders -InFile $backup|Out-Null
Write-Host "Promotion staged safely for $Environment. Apply the staged restore on the server, restart it, verify READY, then rerun with -Finalize. Local mode has not been changed."
