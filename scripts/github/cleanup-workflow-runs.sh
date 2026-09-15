#!/usr/bin/env bash
set -euo pipefail

KEEP="${KEEP:-6}"
MODE="${1:-preview}"
REPOS=(
  'ssdhameliya/DSE-ERP'
  'ssdhameliya/DSE-ERP-Enterprise'
  'ssdhameliya/DES_Mobile'
)

[[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || { echo 'KEEP must be a positive integer.' >&2; exit 2; }
[[ "$MODE" == 'preview' || "$MODE" == '--apply' ]] || { echo 'Usage: cleanup-workflow-runs.sh [--apply]' >&2; exit 2; }
command -v gh >/dev/null 2>&1 || { echo 'GitHub CLI (gh) is required.' >&2; exit 2; }
gh auth status >/dev/null

printf 'GitHub workflow cleanup mode: %s\n' "${MODE#--}"
printf 'Policy: keep newest %s COMPLETED workflow runs per repository; never delete queued/in-progress runs.\n\n' "$KEEP"

for repo in "${REPOS[@]}"; do
  echo "=== $repo ==="
  mapfile -t completed < <(
    gh api --paginate "/repos/$repo/actions/runs?per_page=100" \
      --jq '.workflow_runs[] | select(.status == "completed") | [.created_at, (.id|tostring), .name, (.conclusion // "")] | @tsv' \
      | sort -r
  )
  mapfile -t active < <(
    gh api --paginate "/repos/$repo/actions/runs?per_page=100" \
      --jq '.workflow_runs[] | select(.status != "completed") | (.id|tostring)'
  )

  total=${#completed[@]}
  delete_count=0
  (( total > KEEP )) && delete_count=$((total - KEEP))
  printf 'Completed runs : %s\nKeeping        : %s\nDeleting       : %s\nActive skipped : %s\n' \
    "$total" "$(( total < KEEP ? total : KEEP ))" "$delete_count" "${#active[@]}"

  if (( delete_count == 0 )); then
    echo 'Nothing to delete.'
    echo
    continue
  fi

  for ((i=KEEP; i<total; i++)); do
    IFS=$'\t' read -r created id name conclusion <<<"${completed[$i]}"
    printf '%s run=%s workflow=%s conclusion=%s\n' "$created" "$id" "$name" "$conclusion"
    if [[ "$MODE" == '--apply' ]]; then
      gh api --method DELETE "/repos/$repo/actions/runs/$id" >/dev/null
    fi
  done

  if [[ "$MODE" == '--apply' ]]; then
    echo "Deleted $delete_count old completed workflow run(s)."
  else
    echo "PREVIEW ONLY. Re-run with --apply to delete the listed runs."
  fi
  echo
done
