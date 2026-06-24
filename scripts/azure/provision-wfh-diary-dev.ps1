#Requires -Version 5.1
<#
.SYNOPSIS
  Create WFH Diary dev resources in Azure (Australia East) on the Free (F1) Linux App Service tier.

.DESCRIPTION
  Run in YOUR PowerShell after a successful: az login
  Then this script runs: az account set --subscription "platra-dev"

  Does not set Firebase or database secrets — use Portal or az webapp config appsettings set (see docs/AZURE_DEPLOY.md).

.PARAMETER Subscription
  Subscription name or ID (default: platra-dev).

.PARAMETER WebAppName
  Globally unique app name (default: app-wfh-diary-api-dev).

.PARAMETER DotnetRuntime
  Linux stack, e.g. DOTNETCORE:9.0 — list: az webapp list-runtimes --os-type linux
#>
param(
    [string] $Subscription = "platra-dev",
    [string] $WebAppName = "app-wfh-diary-api-dev",
    [string] $DotnetRuntime = "DOTNETCORE:9.0"
)

$ErrorActionPreference = "Stop"

$AzExe = "az"
if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    $AzCmd = "C:\Program Files\Microsoft SDKs\Azure\CLI2\wbin\az.cmd"
    if (Test-Path $AzCmd) { $AzExe = $AzCmd }
    else { throw "Azure CLI not found. Install it and ensure 'az' is on PATH." }
}

function Invoke-Az {
    param([Parameter(Mandatory = $true)][string[]] $CommandArgs)
    & $AzExe @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Azure CLI failed (exit $LASTEXITCODE): az $($CommandArgs -join ' ')"
    }
}

$null = & $AzExe @("account", "show") 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Not logged in. Run: az login`nThen re-run this script."
}

Write-Host "Selecting subscription: $Subscription"
Invoke-Az @("account", "set", "--subscription", $Subscription)
Invoke-Az @("account", "show", "--output", "table")

$Rg = "rg-wfh-diary-dev-australiaeast"
$Location = "australiaeast"
$Plan = "asp-wfh-diary-dev-linux"

Write-Host "Creating resource group $Rg ..."
Invoke-Az @(
    "group", "create",
    "--name", $Rg,
    "--location", $Location,
    "--tags", "Project=wfh-diary Environment=dev ManagedBy=manual"
)

Write-Host "Creating Linux App Service plan $Plan (SKU F1 Free) ..."
Invoke-Az @(
    "appservice", "plan", "create",
    "--resource-group", $Rg,
    "--name", $Plan,
    "--location", $Location,
    "--is-linux",
    "--sku", "F1"
)

Write-Host "Creating web app $WebAppName (runtime $DotnetRuntime) ..."
Invoke-Az @(
    "webapp", "create",
    "--resource-group", $Rg,
    "--plan", $Plan,
    "--name", $WebAppName,
    "--runtime", $DotnetRuntime
)

Write-Host "Setting ASPNETCORE_ENVIRONMENT=Production ..."
Invoke-Az @(
    "webapp", "config", "appsettings", "set",
    "--resource-group", $Rg,
    "--name", $WebAppName,
    "--settings", "ASPNETCORE_ENVIRONMENT=Production"
)

Write-Host ""
Write-Host "Done. Default URL: https://$WebAppName.azurewebsites.net/"
Write-Host "Health:           https://$WebAppName.azurewebsites.net/health"
Write-Host "Version:          https://$WebAppName.azurewebsites.net/v1/version"
Write-Host ""
Write-Host "Next: GitHub OIDC (docs/CI_CD.md) and optional Firebase (docs/AZURE_DEPLOY.md)."
