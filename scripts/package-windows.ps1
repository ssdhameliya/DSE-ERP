param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = (mvn help:evaluate -Dexpression=project.version -q -DforceStdout).Trim()
}
if ($Version -notmatch '^\d+\.\d+\.\d+([.-][0-9A-Za-z.-]+)?$') {
    throw "Invalid application version: $Version"
}

Write-Host "Building DSE ERP $Version for Windows..." -ForegroundColor Cyan
mvn -B -ntp clean verify

$Jar = Join-Path $Root "desktop/target/DSE_Final.jar"
$ServerJar = Join-Path $Root "server/target/dse-erp-server.jar"
if (-not (Test-Path $Jar)) { throw "Packaged desktop JAR not found: $Jar" }
if (-not (Test-Path $ServerJar)) { throw "Packaged server JAR not found: $ServerJar" }

$Input = Join-Path $Root "target/jpackage-input"
$Dest = Join-Path $Root "target/windows-installer"
$AppImage = Join-Path $Root "target/windows-app-image"
Remove-Item $Input, $Dest, $AppImage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $Input, $Dest, $AppImage | Out-Null
Copy-Item $Jar (Join-Path $Input "DSE_Final.jar")
New-Item -ItemType Directory -Path (Join-Path $Input "server") -Force | Out-Null
Copy-Item $ServerJar (Join-Path $Input "server\dse-erp-server.jar")

# 5.1.40 managed PostgreSQL payload. DSE_POSTGRES_RUNTIME_DIR may point to an extracted
# PostgreSQL 18 binary distribution. Development/release machines can also use the standard install.
$PostgresCandidates = @()
if ($env:DSE_POSTGRES_RUNTIME_DIR) { $PostgresCandidates += $env:DSE_POSTGRES_RUNTIME_DIR }
$PostgresCandidates += @('C:\Program Files\PostgreSQL\18', 'D:\PostgreSQL\18\pgsql')
$PostgresRuntime = $PostgresCandidates | Where-Object {
    Test-Path (Join-Path $_ 'bin\initdb.exe')
} | Select-Object -First 1
if (-not $PostgresRuntime) {
    throw "PostgreSQL 18 runtime not found. Set DSE_POSTGRES_RUNTIME_DIR to an extracted PostgreSQL 18 binary distribution."
}
$PostgresInput = Join-Path $Input 'runtime\postgresql'
New-Item -ItemType Directory -Path $PostgresInput -Force | Out-Null
foreach ($folder in @('bin','lib','share')) {
    $source = Join-Path $PostgresRuntime $folder
    if (-not (Test-Path $source)) { throw "PostgreSQL runtime folder missing: $source" }
    Copy-Item $source (Join-Path $PostgresInput $folder) -Recurse -Force
}
Copy-Item (Join-Path $Root 'runtime\runtime-manifest.properties') (Join-Path $Input 'runtime\runtime-manifest.properties') -Force
Write-Host "Bundled PostgreSQL runtime: $PostgresRuntime" -ForegroundColor DarkCyan
$RequiredBundleFiles = @(
    (Join-Path $Input 'DSE_Final.jar'),
    (Join-Path $Input 'server\dse-erp-server.jar'),
    (Join-Path $Input 'runtime\runtime-manifest.properties'),
    (Join-Path $Input 'runtime\postgresql\bin\initdb.exe'),
    (Join-Path $Input 'runtime\postgresql\bin\pg_ctl.exe'),
    (Join-Path $Input 'runtime\postgresql\bin\psql.exe'),
    (Join-Path $Input 'runtime\postgresql\bin\createdb.exe')
)
$MissingBundleFiles = $RequiredBundleFiles | Where-Object { -not (Test-Path -LiteralPath $_ -PathType Leaf) }
if ($MissingBundleFiles) {
    throw "Production runtime bundle verification failed. Missing: $($MissingBundleFiles -join ', ')"
}
Write-Host 'DSE ERP production bundle verification PASS' -ForegroundColor Green

$Icon = Join-Path $Root "desktop/src/main/resources/installer/DSE-ERP.ico"
$CommonArgs = @(
    '--name', 'DSE ERP',
    '--app-version', $Version,
    '--vendor', 'DS Engineers',
    '--description', 'DSE ERP desktop business management application',
    '--copyright', 'Copyright (c) DS Engineers',
    '--input', $Input,
    '--main-jar', 'DSE_Final.jar',
    '--main-class', 'org.example.app.Launcher',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '-Ddse.erp.nativeAccessRelaunch=true',
    '--java-options', '-Ddse.erp.packaged=true'
)
if (Test-Path $Icon) { $CommonArgs += @('--icon', $Icon) }

# Build an app image first so packaging failures are easier to diagnose.
$AppImageArgs = @('--type', 'app-image') + $CommonArgs + @('--dest', $AppImage)
& jpackage @AppImageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image creation failed." }

$BundledJava = Join-Path $AppImage "DSE ERP\runtime\bin\java.exe"
if (-not (Test-Path $BundledJava)) {
    throw "Production app image is missing bundled Java launcher: $BundledJava"
}
Write-Host "Verified bundled Java launcher: $BundledJava" -ForegroundColor DarkCyan

$ExeArgs = @(
    '--type', 'exe'
) + $CommonArgs + @(
    '--dest', $Dest,
    '--win-menu',
    '--win-menu-group', 'DSE ERP',
    '--win-shortcut',
    '--win-dir-chooser',
    '--win-per-user-install',
    '--win-upgrade-uuid', '8cef21e1-7e8c-5b0a-8d50-a38685db0f96'
)
& jpackage @ExeArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage Windows installer creation failed." }

$Exe = Get-ChildItem $Dest -Filter '*.exe' | Select-Object -First 1
if (-not $Exe) { throw "Windows installer was not produced." }
$FinalName = "DSE-ERP-$Version-Windows-x64.exe"
$FinalPath = Join-Path $Dest $FinalName
Move-Item $Exe.FullName $FinalPath -Force
$Hash = (Get-FileHash $FinalPath -Algorithm SHA256).Hash.ToLowerInvariant()
$ChecksumPath = Join-Path $Dest 'checksums-windows.sha256'
"$Hash  $FinalName" | Set-Content $ChecksumPath -Encoding utf8

$ReleaseDir = Join-Path (Split-Path -Parent $Root) "DSE-ERP-$Version-RELEASE"
New-Item -ItemType Directory -Path $ReleaseDir -Force | Out-Null
Copy-Item -LiteralPath $FinalPath -Destination (Join-Path $ReleaseDir $FinalName) -Force
Copy-Item -LiteralPath $ChecksumPath -Destination (Join-Path $ReleaseDir 'checksums-windows.sha256') -Force

# Release artifacts live beside the source copy; remove generated Maven output.
$GeneratedTarget = Join-Path $Root 'target'
if (Test-Path -LiteralPath $GeneratedTarget) {
    Get-ChildItem -LiteralPath $GeneratedTarget -Recurse -File -Force | ForEach-Object {
        if ($_.IsReadOnly) { $_.IsReadOnly = $false }
    }
}
mvn -B -ntp clean
if ($LASTEXITCODE -ne 0) { throw 'The installer was created, but Maven could not clean generated build output.' }

Write-Host "Created: $(Join-Path $ReleaseDir $FinalName)" -ForegroundColor Green
