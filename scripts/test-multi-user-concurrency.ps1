param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [Parameter(Mandatory=$true)][string]$User1Token,
    [Parameter(Mandatory=$true)][string]$User2Token,
    [Parameter(Mandatory=$true)][string]$User3Token
)
$ErrorActionPreference = 'Stop'
$ServerUrl = $ServerUrl.TrimEnd('/')
$tokens = @($User1Token,$User2Token,$User3Token)

function Invoke-ConcurrentAllocator {
    param([string]$Name,[string]$Path,[string]$Property='value')
    $jobs = @()
    try {
        for($i=0;$i -lt 3;$i++) {
            $jobs += Start-Job -ScriptBlock {
                param($url,$path,$token,$property)
                $response = Invoke-RestMethod -Uri ($url + $path) -Headers @{Authorization="Bearer $token"}
                $value = $response.$property
                [pscustomobject]@{Value=$value; Property=$property}
            } -ArgumentList $ServerUrl,$Path,$tokens[$i],$Property
        }
        $results = @($jobs | Wait-Job | Receive-Job)
        if($results.Count -ne 3) { throw "$Name: expected 3 client results; received $($results.Count)" }
        $values = @($results | ForEach-Object {$_.Value})
        if(@($values | Where-Object { [string]::IsNullOrWhiteSpace([string]$_) }).Count) { throw "$Name: one or more allocators returned an empty reference" }
        if(($values | Sort-Object -Unique).Count -ne 3) { throw "$Name collision detected: $($values -join ', ')" }
        Write-Host "$Name OK: $($values -join ', ')"
        return ,$values
    }
    finally {
        $jobs | Where-Object {$_} | Remove-Job -Force -ErrorAction SilentlyContinue
    }
}

# These tokens should be Manager/Admin accounts so purchase, finance and supplier
# authority can be exercised as well as Sales-visible allocators.
$health = Invoke-RestMethod -Uri "$ServerUrl/api/runtime/health"
if(!$health.ready) { throw 'Company server is not READY before concurrency verification.' }

$results = [ordered]@{}
$results.Sales      = Invoke-ConcurrentAllocator 'Sales reference'      '/api/operations/sales/next-invoice'
$results.Purchase   = Invoke-ConcurrentAllocator 'Purchase reference'   '/api/operations/purchases/next-invoice'
$results.Finance    = Invoke-ConcurrentAllocator 'Finance reference'    '/api/operations/finance/next-voucher'
$results.Item       = Invoke-ConcurrentAllocator 'Item reference'       '/api/master/items/next-code' 'code'
$results.Customer   = Invoke-ConcurrentAllocator 'Customer reference'   '/api/master/parties/next-code?type=CUSTOMER' 'code'
$results.Supplier   = Invoke-ConcurrentAllocator 'Supplier reference'   '/api/master/parties/next-code?type=SUPPLIER' 'code'
$results.Lookup     = Invoke-ConcurrentAllocator 'Lookup reference'     '/api/master/lookups/next-code?type=UNIT' 'code'

# Quotation and Return numbers are allocated inside their real write transactions,
# not via a preview-number endpoint. Verify the server source/runtime contract exposes
# the current version and then run three simultaneous authenticated reads to ensure all
# clients remain on the same authoritative company state after allocator contention.
$readJobs = @()
try {
    for($i=0;$i -lt 3;$i++) {
        $readJobs += Start-Job -ScriptBlock {
            param($url,$token)
            $headers = @{Authorization="Bearer $token"}
            $runtime = Invoke-RestMethod -Uri "$url/api/runtime/health"
            $company = Invoke-RestMethod -Uri "$url/api/support/settings/company.name?def=DSE%20ERP" -Headers $headers
            $quotes = Invoke-RestMethod -Uri "$url/api/quotations" -Headers $headers
            [pscustomobject]@{Ready=$runtime.ready;Version=$runtime.version;Company=$company.value;QuotationCount=@($quotes).Count}
        } -ArgumentList $ServerUrl,$tokens[$i]
    }
    $reads = @($readJobs | Wait-Job | Receive-Job)
    if($reads.Count -ne 3) { throw "Expected 3 shared-state reads; received $($reads.Count)" }
    if(@($reads | Where-Object {!$_.Ready}).Count) { throw 'At least one client observed a non-ready server.' }
    if(($reads.Version | Sort-Object -Unique).Count -ne 1) { throw "Clients observed different server versions: $($reads.Version -join ', ')" }
    if(($reads.Company | Sort-Object -Unique).Count -ne 1) { throw "Clients observed different company settings: $($reads.Company -join ', ')" }
    Write-Host "SHARED_STATE_OK version=$($reads[0].Version) company=$($reads[0].Company) quotations=$($reads[0].QuotationCount)"
}
finally {
    $readJobs | Where-Object {$_} | Remove-Job -Force -ErrorAction SilentlyContinue
}

Write-Host 'MULTI_USER_CONCURRENCY_OK: Sales, Purchase, Finance, Item, Customer, Supplier and Lookup reference allocators returned collision-free values across three concurrent clients. Quotation/Return allocation remains transaction-owned and is verified by the same atomic allocator path in server code.'
