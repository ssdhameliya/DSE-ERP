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

$Jar = Join-Path $Root "target/DSE_Final.jar"
if (-not (Test-Path $Jar)) {
    throw "Packaged JAR not found: $Jar"
}

$Input = Join-Path $Root "target/jpackage-input"
$Dest = Join-Path $Root "target/windows-installer"
$AppImage = Join-Path $Root "target/windows-app-image"
Remove-Item $Input, $Dest, $AppImage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $Input, $Dest, $AppImage | Out-Null
Copy-Item $Jar (Join-Path $Input "DSE_Final.jar")

$Icon = Join-Path $Root "src/main/resources/installer/DSE-ERP.ico"
$CommonArgs = @(
    '--name', 'DSE ERP',
    '--app-version', $Version,
    '--vendor', 'DS Engineers',
    '--description', 'Open-source JavaFX ERP desktop application',
    '--copyright', 'Copyright (c) DS Engineers',
    '--input', $Input,
    '--main-jar', 'DSE_Final.jar',
    '--main-class', 'org.example.app.Launcher',
    '--java-options', '-Dfile.encoding=UTF-8'
)
if (Test-Path $Icon) { $CommonArgs += @('--icon', $Icon) }

# Build an app image first so packaging failures are easier to diagnose.
$AppImageArgs = @('--type', 'app-image') + $CommonArgs + @('--dest', $AppImage)
& jpackage @AppImageArgs

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

$Exe = Get-ChildItem $Dest -Filter '*.exe' | Select-Object -First 1
if (-not $Exe) { throw "Windows installer was not produced." }
$FinalName = "DSE-ERP-$Version-Windows-x64.exe"
$FinalPath = Join-Path $Dest $FinalName
Move-Item $Exe.FullName $FinalPath -Force
$Hash = (Get-FileHash $FinalPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$Hash  $FinalName" | Set-Content (Join-Path $Dest 'checksums-windows.txt') -Encoding utf8

Write-Host "Created: $FinalPath" -ForegroundColor Green
