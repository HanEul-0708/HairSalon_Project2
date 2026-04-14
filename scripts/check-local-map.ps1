param(
    [int]$Port = 8090
)

$ErrorActionPreference = "Stop"

$MapUrl = "http://localhost:${Port}/salons/map"
$ScriptUrl = "http://localhost:${Port}/js/salon-map.js"

Write-Host "Checking $MapUrl"
$MapResponse = Invoke-WebRequest -UseBasicParsing $MapUrl

Write-Host ""
Write-Host "Rendered map page lines:"
$MapResponse.Content -split [char]10 |
    Select-String -Pattern "data-has-key|data-has-rest-key|salon-map.js|sdk.js" |
    ForEach-Object { $_.Line.Trim() -replace "appkey=[^&""]+", "appkey=***" }

Write-Host ""
Write-Host "Checking $ScriptUrl"
$ScriptResponse = Invoke-WebRequest -UseBasicParsing $ScriptUrl

Write-Host ""
Write-Host "First salon-map.js lines:"
$ScriptResponse.Content -split [char]10 | Select-Object -First 45
