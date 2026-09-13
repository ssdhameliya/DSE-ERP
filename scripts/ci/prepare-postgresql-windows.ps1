param(
    [string]$RuntimeDir = ""
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($RuntimeDir)) {
    if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) {
        throw "RUNNER_TEMP is not set and RuntimeDir was not supplied."
    }
    $RuntimeDir = Join-Path $env:RUNNER_TEMP 'dse-postgresql-18-runtime'
}

$initdb = Join-Path $RuntimeDir 'bin\initdb.exe'
if (-not (Test-Path -LiteralPath $initdb -PathType Leaf)) {
    Write-Host 'PostgreSQL 18 portable runtime cache miss; installing the verified Chocolatey package once to seed the default-branch cache.' -ForegroundColor Cyan
    choco install postgresql18 --yes --no-progress --params '/NoPath /Password:DseReleaseBuildOnly!'
    if ($LASTEXITCODE -ne 0) {
        throw "Chocolatey PostgreSQL 18 installation failed with exit code $LASTEXITCODE"
    }

    $installedPg = 'C:\Program Files\PostgreSQL\18'
    if (-not (Test-Path -LiteralPath (Join-Path $installedPg 'bin\initdb.exe') -PathType Leaf)) {
        throw "PostgreSQL 18 runtime not found at $installedPg"
    }

    Remove-Item -LiteralPath $RuntimeDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
    foreach ($folder in @('bin','lib','share')) {
        $source = Join-Path $installedPg $folder
        if (-not (Test-Path -LiteralPath $source -PathType Container)) {
            throw "PostgreSQL runtime folder missing: $source"
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $RuntimeDir $folder) -Recurse -Force
    }
} else {
    Write-Host "Using cached PostgreSQL 18 runtime: $RuntimeDir" -ForegroundColor DarkCyan
}

$required = @('initdb.exe','pg_ctl.exe','pg_isready.exe','psql.exe','createdb.exe','pg_dump.exe','pg_restore.exe')
$missing = $required | Where-Object {
    -not (Test-Path -LiteralPath (Join-Path $RuntimeDir "bin\$_") -PathType Leaf)
}
if ($missing) {
    throw "PostgreSQL 18 runtime is incomplete. Missing: $($missing -join ', ')"
}
foreach ($folder in @('lib','share')) {
    if (-not (Test-Path -LiteralPath (Join-Path $RuntimeDir $folder) -PathType Container)) {
        throw "PostgreSQL 18 runtime is incomplete. Missing folder: $folder"
    }
}

if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_ENV)) {
    "DSE_POSTGRES_RUNTIME_DIR=$RuntimeDir" | Out-File -FilePath $env:GITHUB_ENV -Encoding utf8 -Append
}
Write-Host "POSTGRES_RUNTIME_READY platform=windows path=$RuntimeDir" -ForegroundColor Green
