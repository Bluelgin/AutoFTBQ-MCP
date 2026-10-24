param(
    [Parameter(Mandatory = $false)]
    [string]$RuntimeFile
)

$ErrorActionPreference = "Stop"

function Resolve-RuntimeFile {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        return [System.IO.Path]::GetFullPath($ExplicitPath)
    }

    if ($env:AUTOFTBQ_MCP_RUNTIME) {
        return [System.IO.Path]::GetFullPath($env:AUTOFTBQ_MCP_RUNTIME)
    }

    $localCandidate = Join-Path (Get-Location) "config\autoftbq-mcp\runtime.json"
    if (Test-Path -LiteralPath $localCandidate) {
        return [System.IO.Path]::GetFullPath($localCandidate)
    }

    throw "AutoFTBQ MCP runtime.json not found. Pass -RuntimeFile or set AUTOFTBQ_MCP_RUNTIME."
}

try {
    $resolved = Resolve-RuntimeFile $RuntimeFile
    if (-not (Test-Path -LiteralPath $resolved)) {
        throw "AutoFTBQ MCP runtime.json does not exist: $resolved"
    }

    $runtime = Get-Content -LiteralPath $resolved -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $runtime.token) {
        throw "runtime.json does not contain a token. Is Minecraft with AutoFTBQ MCP currently running?"
    }

    @{
        Authorization = "Bearer $($runtime.token)"
    } | ConvertTo-Json -Compress
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
