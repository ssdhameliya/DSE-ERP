param(
    [ValidateRange(1, 100)]
    [int]$Keep = 6,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

$Repositories = @(
    'ssdhameliya/DSE-ERP',
    'ssdhameliya/DSE-ERP-Enterprise',
    'ssdhameliya/DES_Mobile'
)

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI (gh) is required. Install/authenticate gh, then run this script again.'
}

& gh auth status 1>$null
if ($LASTEXITCODE -ne 0) {
    throw 'GitHub CLI is not authenticated. Run: gh auth login'
}

function Get-AllWorkflowRuns {
    param([Parameter(Mandatory)][string]$Repository)

    $all = @()
    $page = 1
    while ($true) {
        $json = & gh api `
            -H 'Accept: application/vnd.github+json' `
            "/repos/$Repository/actions/runs?per_page=100&page=$page"
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to list workflow runs for $Repository"
        }

        $response = $json | ConvertFrom-Json
        $batch = @($response.workflow_runs)
        if ($batch.Count -eq 0) { break }

        $all += $batch
        if ($batch.Count -lt 100) { break }
        $page++
    }
    return $all
}

$mode = if ($Apply) { 'APPLY' } else { 'PREVIEW' }
Write-Host "GitHub workflow cleanup mode: $mode"
Write-Host "Policy: keep the newest $Keep COMPLETED workflow runs in each repository; never delete queued/in-progress runs."
Write-Host ''

foreach ($repo in $Repositories) {
    Write-Host "=== $repo ==="
    $runs = @(Get-AllWorkflowRuns -Repository $repo)
    $completed = @(
        $runs |
            Where-Object { $_.status -eq 'completed' } |
            Sort-Object { [DateTimeOffset]$_.created_at } -Descending
    )

    $kept = @($completed | Select-Object -First $Keep)
    $delete = @($completed | Select-Object -Skip $Keep)
    $active = @($runs | Where-Object { $_.status -ne 'completed' })

    Write-Host "Completed runs : $($completed.Count)"
    Write-Host "Keeping        : $($kept.Count)"
    Write-Host "Deleting       : $($delete.Count)"
    Write-Host "Active skipped : $($active.Count)"

    if ($delete.Count -eq 0) {
        Write-Host 'Nothing to delete.'
        Write-Host ''
        continue
    }

    foreach ($run in $delete) {
        $stamp = ([DateTimeOffset]$run.created_at).ToString('yyyy-MM-dd HH:mm:ss K')
        Write-Host ("{0} run={1} workflow={2} conclusion={3}" -f $stamp, $run.id, $run.name, $run.conclusion)
        if ($Apply) {
            & gh api --method DELETE "/repos/$repo/actions/runs/$($run.id)" 1>$null
            if ($LASTEXITCODE -ne 0) {
                throw "Failed deleting workflow run $($run.id) from $repo"
            }
        }
    }

    if (-not $Apply) {
        Write-Host "PREVIEW ONLY. Re-run with -Apply to delete the $($delete.Count) listed run(s)."
    } else {
        Write-Host "Deleted $($delete.Count) old completed workflow run(s)."
    }
    Write-Host ''
}
