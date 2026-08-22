param([Parameter(Mandatory=$true)][string]$Workspace,[Parameter(Mandatory=$true)][string]$DatabaseUrl,[Parameter(Mandatory=$true)][string]$DatabaseUser,[string]$PostgresHome="")
$ErrorActionPreference='Stop'
$backup=Join-Path $Workspace 'Backups\Server\restore-pending.pgbackup';$marker=Join-Path $Workspace 'Backups\Server\restore-pending.marker'
if(!(Test-Path -LiteralPath $backup)){throw "No staged server restore exists at $backup"}
$tool=if($PostgresHome){Join-Path $PostgresHome 'bin\pg_restore.exe'}else{'pg_restore'}
& $tool '--clean' '--if-exists' '--no-owner' '--no-privileges' "--username=$DatabaseUser" "--dbname=$DatabaseUrl" $backup
if($LASTEXITCODE -ne 0){throw "Server restore failed with exit code $LASTEXITCODE"}
Remove-Item -LiteralPath $backup -Force;if(Test-Path -LiteralPath $marker){Remove-Item -LiteralPath $marker -Force}
Write-Host 'Server restore completed successfully.'
