# Bumps version.json (versionCode + patch versionName); the Android build reads it.
# Called automatically by .githooks/pre-commit. Skip with: SKIP_VERSION_BUMP=1 git commit ...

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $repoRoot "version.json"

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content, $utf8)
}

if (-not (Test-Path $versionFile)) {
    Write-Error "Missing version.json at repo root."
}

$json = Get-Content $versionFile -Raw | ConvertFrom-Json
$versionCode = [int]$json.versionCode
$versionName = [string]$json.versionName

$versionCode += 1
if ($versionName -match '^(\d+)\.(\d+)\.(\d+)$') {
    $patch = [int]$Matches[3] + 1
    $versionName = "{0}.{1}.{2}" -f $Matches[1], $Matches[2], $patch
}
else {
    $versionName = "$versionName.$versionCode"
}

$versionJson = "{""versionCode"":$versionCode,""versionName"":""$versionName""}"
Write-Utf8NoBom $versionFile $versionJson

Write-Host "Bumped to $versionName ($versionCode)"
