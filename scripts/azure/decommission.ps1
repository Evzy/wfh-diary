# Deletes the WFH Diary Azure resource group (API App Service, App Service plan, SQL server/database)
# and optionally the GitHub Actions deploy app registration in Entra ID.
# Run manually, once, after the phone holds all data and a backup file exists. Irreversible.
param(
    [string]$Subscription = "platra-dev",
    [string]$ResourceGroup = "rg-wfh-diary-dev-australiaeast",
    [string]$DeployAppName = "github-wfh-diary-deploy"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    Write-Error "Azure CLI (az) is not installed."
}

az account set --subscription $Subscription
if ($LASTEXITCODE -ne 0) { Write-Error "Could not select subscription '$Subscription'. Run 'az login' first." }

$exists = az group exists --name $ResourceGroup
if ($LASTEXITCODE -ne 0) { Write-Error "az group exists failed. Run 'az login' first." }
if ($exists -ne "true") {
    Write-Host "Resource group '$ResourceGroup' does not exist in '$Subscription'. Skipping resource group deletion."
}
else {
    Write-Host "Resources in ${ResourceGroup}:"
    az resource list --resource-group $ResourceGroup --query "[].{name:name, type:type}" --output table

    Write-Host ""
    Write-Host "This permanently deletes every resource above, including the SQL database." -ForegroundColor Yellow
    $typed = Read-Host "Type the resource group name to confirm"
    if ($typed -ne $ResourceGroup) {
        Write-Host "Confirmation did not match. Nothing deleted."
        exit 1
    }

    az group delete --name $ResourceGroup --yes
    if ($LASTEXITCODE -ne 0) { Write-Error "az group delete failed." }
    Write-Host "Deleted $ResourceGroup."
}

# The GitHub Actions deploy identity lives in Entra ID, outside the resource group.
$appId = az ad app list --display-name $DeployAppName --query "[0].appId" --output tsv
if ($LASTEXITCODE -ne 0) { Write-Error "az ad app list failed." }
if ([string]::IsNullOrWhiteSpace($appId)) {
    Write-Host "No app registration named '$DeployAppName'. Done."
    exit 0
}
$answer = Read-Host "Also delete the GitHub deploy app registration '$DeployAppName' ($appId)? [y/N]"
if ($answer -eq "y") {
    az ad app delete --id $appId
    if ($LASTEXITCODE -ne 0) { Write-Error "az ad app delete failed." }
    Write-Host "Deleted app registration $DeployAppName."
}
