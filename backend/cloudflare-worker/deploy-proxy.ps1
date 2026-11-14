# One-Command Deploy & Secret Sync Script for Yomi Cloudflare Worker Proxy
# Reads your API keys from d:\yomi\local.properties, uploads them as encrypted
# Cloudflare Worker secrets, and deploys the Worker.

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$localPropsPath = Join-Path $repoRoot "local.properties"

if (-not (Test-Path $localPropsPath)) {
    Write-Error "Could not find $localPropsPath"
    exit 1
}

$props = @{}
Get-Content $localPropsPath | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $props[$parts[0].Trim()] = $parts[1].Trim()
    }
}

Push-Location $PSScriptRoot
try {
    Write-Host "==> Deploying Yomi Conversion Proxy Worker to Cloudflare..." -ForegroundColor Cyan
    npx --yes wrangler deploy

    $secretMappings = @{
        "ILOVEPDF_PUBLIC_KEY"        = $props["ilovepdf.public.key"]
        "ILOVEPDF_BACKUP_PUBLIC_KEY" = $props["ilovepdf.backup.public.key"]
        "CLOUDCONVERT_API_KEY"       = $props["cloudconvert.api.key"]
        "CLOUDCONVERT_BACKUP_API_KEY"= $props["cloudconvert.backup.api.key"]
    }

    foreach ($secretName in $secretMappings.Keys) {
        $val = $secretMappings[$secretName]
        if (-not [string]::IsNullOrWhiteSpace($val)) {
            Write-Host "==> Uploading encrypted secret: $secretName" -ForegroundColor Green
            $val | npx --yes wrangler secret put $secretName
        }
    }

    Write-Host "`n==> Done! Copy your deployed https://yomi-conversion-proxy.<your-subdomain>.workers.dev URL" -ForegroundColor Yellow
    Write-Host "    and set it in local.properties as:" -ForegroundColor Yellow
    Write-Host "    conversion.proxy.url=https://yomi-conversion-proxy.<your-subdomain>.workers.dev" -ForegroundColor White
} finally {
    Pop-Location
}

