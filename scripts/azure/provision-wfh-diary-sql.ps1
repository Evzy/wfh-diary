# Provision Azure SQL Database (free offer) for WFH Diary API.
# Prerequisite: az login, subscription set (e.g. platra-dev).
# Usage:
#   ./scripts/azure/provision-wfh-diary-sql.ps1
#   ./scripts/azure/provision-wfh-diary-sql.ps1 -AdminPassword 'YourStrong!Pass'

param(
    [string]$ResourceGroup = "rg-wfh-diary-dev-australiaeast",
    [string]$Location = "australiaeast",
    [string]$ServerName = "sql-wfh-diary-dev",
    [string]$DatabaseName = "wfh-diary",
    [string]$AdminUser = "wfhadmin",
    [string]$AdminPassword = "",
    [switch]$SkipClientFirewallRule,
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    throw "Azure CLI (az) not found. Install from https://learn.microsoft.com/cli/azure/install-azure-cli"
}

Write-Host "Ensuring Microsoft.Sql provider is registered..."
az provider register --namespace Microsoft.Sql --wait | Out-Null

if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    $AdminPassword = "Wfh" + [guid]::NewGuid().ToString("N").Substring(0, 20) + "!"
}

$serverExists = az sql server show --resource-group $ResourceGroup --name $ServerName 2>$null
if (-not $serverExists) {
    Write-Host "Creating SQL server $ServerName..."
    az sql server create `
        --resource-group $ResourceGroup `
        --name $ServerName `
        --location $Location `
        --admin-user $AdminUser `
        --admin-password $AdminPassword | Out-Null
} else {
    Write-Host "SQL server $ServerName already exists — skipping create."
}

Write-Host "Ensuring firewall rules..."
az sql server firewall-rule create `
    --resource-group $ResourceGroup `
    --server $ServerName `
    --name AllowAzureServices `
    --start-ip-address 0.0.0.0 `
    --end-ip-address 0.0.0.0 | Out-Null

if (-not $SkipClientFirewallRule) {
    $clientIp = (Invoke-RestMethod -Uri "https://api.ipify.org" -TimeoutSec 15).Trim()
    Write-Host "Allowing client IP $clientIp..."
    az sql server firewall-rule create `
        --resource-group $ResourceGroup `
        --server $ServerName `
        --name AllowDevClient `
        --start-ip-address $clientIp `
        --end-ip-address $clientIp | Out-Null
}

$dbExists = az sql db show --resource-group $ResourceGroup --server $ServerName --name $DatabaseName 2>$null
if (-not $dbExists) {
    Write-Host "Creating database $DatabaseName (free offer)..."
    az sql db create `
        --resource-group $ResourceGroup `
        --server $ServerName `
        --name $DatabaseName `
        --edition GeneralPurpose `
        --family Gen5 `
        --capacity 2 `
        --compute-model Serverless `
        --use-free-limit `
        --free-limit-exhaustion-behavior AutoPause `
        --yes | Out-Null
} else {
    Write-Host "Database $DatabaseName already exists — skipping create."
}

$credDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path "azure-logs"
New-Item -ItemType Directory -Force -Path $credDir | Out-Null
$credFile = Join-Path $credDir "sql-wfh-diary-dev-credentials.local.txt"
$conn = "Server=tcp:$ServerName.database.windows.net,1433;Initial Catalog=$DatabaseName;User ID=$AdminUser;Password=$AdminPassword;Encrypt=True;TrustServerCertificate=False;Connection Timeout=30;"
@(
    "Azure SQL credentials (local only — do not commit)",
    "Generated: $(Get-Date -Format o)",
    "Server: $ServerName.database.windows.net",
    "Database: $DatabaseName",
    "Admin user: $AdminUser",
    "Admin password: $AdminPassword",
    "",
    "App Service setting (Phase 3):",
    "WfhDiary__ConnectionString=$conn"
) | Set-Content -Path $credFile -Encoding UTF8

Write-Host ""
Write-Host "Done."
Write-Host "  Server:  $ServerName.database.windows.net"
Write-Host "  Database: $DatabaseName (free offer, AutoPause on limit)"
Write-Host "  Credentials: $credFile"
Write-Host ""
Write-Host "Next: implement Phase 1 (EF SqlServer + migrations), then set WfhDiary__ConnectionString on app-wfh-diary-api-dev."
