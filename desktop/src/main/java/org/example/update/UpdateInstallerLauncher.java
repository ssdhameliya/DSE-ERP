package org.example.update;

import org.example.config.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * Starts a detached operating-system helper which waits for the ERP process to
 * stop, installs the verified package, and starts DSE ERP again.
 *
 * <p>The helper is deliberately outside the application process because an
 * installed application cannot safely replace its own files while running.</p>
 */
public final class UpdateInstallerLauncher {
    private UpdateInstallerLauncher() {
    }

    public static LaunchResult launch(Path installer, String targetVersion) throws IOException {
        if (installer == null || !Files.isRegularFile(installer)) {
            throw new IllegalArgumentException("The downloaded installer does not exist.");
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        long currentPid = ProcessHandle.current().pid();
        String applicationPath = System.getProperty("jpackage.app-path", "").trim();
        Path helperFolder = ConfigManager.getConfigFolder().resolve("Updates").resolve("helpers");
        Files.createDirectories(helperFolder);

        if (os.contains("win")) {
            return launchWindows(installer.toAbsolutePath(), targetVersion, currentPid, applicationPath, helperFolder);
        }
        if (os.contains("mac")) {
            return launchMac(installer.toAbsolutePath(), targetVersion, currentPid, applicationPath, helperFolder);
        }
        throw new UnsupportedOperationException("Automatic installation is supported on Windows and macOS only.");
    }

    private static LaunchResult launchWindows(Path installer,
                                              String targetVersion,
                                              long currentPid,
                                              String applicationPath,
                                              Path helperFolder) throws IOException {
        Path script = helperFolder.resolve("install-dse-erp-" + safeVersion(targetVersion) + ".ps1");
        String scriptText = """
                param(
                    [Parameter(Mandatory=$true)][long]$ProcessId,
                    [Parameter(Mandatory=$true)][string]$Installer,
                    [string]$ApplicationPath = ''
                )
                $ErrorActionPreference = 'Stop'
                $log = Join-Path $env:TEMP 'DSE-ERP-update.log'
                function Write-UpdateLog([string]$Message) {
                    Add-Content -Path $log -Value (('[{0}] {1}' -f (Get-Date -Format s), $Message)) -Encoding UTF8
                }
                function Resolve-ApplicationPath {
                    if ($ApplicationPath -and (Test-Path -LiteralPath $ApplicationPath -PathType Leaf)) {
                        return [System.IO.Path]::GetFullPath($ApplicationPath)
                    }
                    $fallback = Join-Path $env:LOCALAPPDATA 'DSE ERP\\DSE ERP.exe'
                    if (Test-Path -LiteralPath $fallback -PathType Leaf) {
                        return [System.IO.Path]::GetFullPath($fallback)
                    }
                    return ''
                }
                function Resolve-PostgresRuntime([string]$AppPath) {
                    if (-not $AppPath) { return '' }
                    $appRoot = Split-Path -Parent $AppPath
                    return (Join-Path $appRoot 'app\\runtime\\postgresql')
                }
                function Assert-PostgresReleased([string]$Runtime) {
                    if (-not $Runtime -or -not (Test-Path -LiteralPath $Runtime -PathType Container)) {
                        throw "Bundled PostgreSQL runtime could not be located before update: $Runtime"
                    }
                    $runtimeFull = [System.IO.Path]::GetFullPath($Runtime).TrimEnd('\\')
                    $locked = @(Get-CimInstance Win32_Process -Filter "Name='postgres.exe'" -ErrorAction Stop | Where-Object {
                        if (-not $_.ExecutablePath) { return $false }
                        try {
                            $exe = [System.IO.Path]::GetFullPath($_.ExecutablePath)
                            return $exe.StartsWith($runtimeFull, [System.StringComparison]::OrdinalIgnoreCase)
                        } catch { return $false }
                    })
                    if ($locked.Count -gt 0) {
                        $ids = ($locked | ForEach-Object { $_.ProcessId }) -join ', '
                        throw "Bundled PostgreSQL is still running (PID(s): $ids). Installer launch blocked to protect runtime files."
                    }
                }
                function Assert-RuntimeHealthy([string]$Runtime) {
                    if (-not $Runtime -or -not (Test-Path -LiteralPath $Runtime -PathType Container)) {
                        throw "Installed PostgreSQL runtime is missing: $Runtime"
                    }
                    $required = @(
                        'bin\\postgres.exe',
                        'bin\\pg_ctl.exe',
                        'bin\\pg_isready.exe',
                        'bin\\psql.exe',
                        'bin\\createdb.exe',
                        'bin\\libpq.dll',
                        'bin\\libssl-3-x64.dll',
                        'bin\\libcrypto-3-x64.dll'
                    )
                    $missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $Runtime $_) -PathType Leaf) })
                    if ($missing.Count -gt 0) {
                        throw "Installed PostgreSQL runtime verification failed. Missing: $($missing -join ', ')"
                    }
                    $rollbackFiles = @(Get-ChildItem -LiteralPath $Runtime -Filter '*.rbf' -Recurse -File -ErrorAction SilentlyContinue)
                    if ($rollbackFiles.Count -gt 0) {
                        throw "Installed PostgreSQL runtime contains rollback/locked-file artifacts (*.rbf). DSE ERP will not restart."
                    }
                }
                function Try-RestartExistingApplication([string]$AppPath) {
                    if (-not $AppPath -or -not (Test-Path -LiteralPath $AppPath -PathType Leaf)) { return }
                    try {
                        $runtime = Resolve-PostgresRuntime $AppPath
                        Assert-RuntimeHealthy $runtime
                        Write-UpdateLog "Restarting verified existing application after failed update: $AppPath"
                        Start-Process -FilePath $AppPath
                    } catch {
                        Write-UpdateLog "Existing application was not restarted because runtime verification failed: $($_.Exception.Message)"
                    }
                }
                $resolvedApp = Resolve-ApplicationPath
                $postgresRuntime = Resolve-PostgresRuntime $resolvedApp
                try {
                    Write-UpdateLog "Waiting for DSE ERP process $ProcessId to close."
                    try {
                        Wait-Process -Id $ProcessId -Timeout 180 -ErrorAction Stop
                    } catch {
                        if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
                            throw "DSE ERP process $ProcessId did not exit within 180 seconds. Installer launch blocked."
                        }
                    }
                    Assert-PostgresReleased $postgresRuntime
                    Write-UpdateLog "PostgreSQL runtime is fully released: $postgresRuntime"
                    Write-UpdateLog "Starting silent installer: $Installer"
                    $process = Start-Process -FilePath $Installer -ArgumentList @('/quiet','/norestart') -Wait -PassThru
                    Write-UpdateLog "Installer exit code: $($process.ExitCode)"
                    if ($process.ExitCode -ne 0) { throw "Installer returned exit code $($process.ExitCode)." }
                    Start-Sleep -Seconds 2

                    $resolvedApp = Resolve-ApplicationPath
                    $postgresRuntime = Resolve-PostgresRuntime $resolvedApp
                    Assert-RuntimeHealthy $postgresRuntime
                    Assert-PostgresReleased $postgresRuntime
                    Write-UpdateLog "Installed PostgreSQL runtime verification passed."

                    if ($resolvedApp) {
                        Write-UpdateLog "Restarting application: $resolvedApp"
                        Start-Process -FilePath $resolvedApp
                    } else {
                        throw "DSE ERP executable could not be located after installation."
                    }
                } catch {
                    Write-UpdateLog "Automatic update failed safely: $($_.Exception.Message)"
                    # Never relaunch the installer interactively after an automatic failure.
                    # A file-lock failure must fail closed rather than attempting replacement again.
                    Try-RestartExistingApplication $resolvedApp
                    exit 1
                }
                """;
        Files.writeString(script, scriptText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden",
                "-File", script.toString(),
                "-ProcessId", Long.toString(currentPid),
                "-Installer", installer.toString(),
                "-ApplicationPath", applicationPath
        ).start();
        return new LaunchResult(true, script, "Windows silent installer helper started.");
    }

    private static LaunchResult launchMac(Path installer,
                                          String targetVersion,
                                          long currentPid,
                                          String applicationPath,
                                          Path helperFolder) throws IOException {
        Path script = helperFolder.resolve("install-dse-erp-" + safeVersion(targetVersion) + ".command");
        String scriptText = """
                #!/bin/zsh
                set -u
                PID="$1"
                DMG="$2"
                APP_PATH="${3:-}"
                LOG="$HOME/Library/Logs/DSE-ERP-update.log"
                mkdir -p "$(dirname "$LOG")"
                log() { print -r -- "[$(date '+%Y-%m-%dT%H:%M:%S')] $*" >> "$LOG"; }
                log "Waiting for DSE ERP process $PID to close."
                for _ in {1..180}; do
                  kill -0 "$PID" 2>/dev/null || break
                  sleep 1
                done
                MOUNT="$(mktemp -d /tmp/dse-erp-update.XXXXXX)"
                cleanup() {
                  hdiutil detach "$MOUNT" -quiet 2>/dev/null || true
                  rmdir "$MOUNT" 2>/dev/null || true
                }
                trap cleanup EXIT
                if ! hdiutil attach "$DMG" -nobrowse -readonly -mountpoint "$MOUNT" -quiet; then
                  log "Unable to mount $DMG"
                  open "$DMG"
                  exit 1
                fi
                SOURCE_APP="$(find "$MOUNT" -maxdepth 2 -name 'DSE ERP.app' -print -quit)"
                if [[ -z "$SOURCE_APP" ]]; then
                  log "DSE ERP.app was not found in the disk image."
                  open "$DMG"
                  exit 1
                fi
                TARGET_APP=""
                if [[ "$APP_PATH" == *'.app/'* ]]; then
                  TARGET_APP="${APP_PATH%%.app/*}.app"
                elif [[ "$APP_PATH" == *'.app' ]]; then
                  TARGET_APP="$APP_PATH"
                fi
                [[ -n "$TARGET_APP" ]] || TARGET_APP="/Applications/DSE ERP.app"
                TARGET_PARENT="$(dirname "$TARGET_APP")"
                mkdir -p "$TARGET_PARENT" 2>/dev/null || true
                install_direct() {
                  rm -rf "$TARGET_APP"
                  /usr/bin/ditto "$SOURCE_APP" "$TARGET_APP"
                  /usr/bin/xattr -dr com.apple.quarantine "$TARGET_APP" 2>/dev/null || true
                }
                if [[ -w "$TARGET_PARENT" ]]; then
                  install_direct
                else
                  SRC_Q="$(printf %q "$SOURCE_APP")"
                  DST_Q="$(printf %q "$TARGET_APP")"
                  /usr/bin/osascript -e "do shell script \"rm -rf $DST_Q && /usr/bin/ditto $SRC_Q $DST_Q && /usr/bin/xattr -dr com.apple.quarantine $DST_Q\" with administrator privileges"
                fi
                log "Update installed at $TARGET_APP"
                cleanup
                trap - EXIT
                open "$TARGET_APP"
                """;
        Files.writeString(script, scriptText, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        script.toFile().setExecutable(true, true);

        new ProcessBuilder(
                "/bin/zsh", script.toString(),
                Long.toString(currentPid),
                installer.toString(),
                applicationPath
        ).start();
        return new LaunchResult(true, script, "macOS automatic installer helper started.");
    }

    private static String safeVersion(String version) {
        return version == null ? "update" : version.replaceAll("[^0-9A-Za-z._-]", "-");
    }

    public record LaunchResult(boolean automatic, Path helper, String message) {
    }
}
