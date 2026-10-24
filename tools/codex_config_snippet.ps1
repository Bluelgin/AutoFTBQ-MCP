param(
    [Parameter(Mandatory = $true)]
    [string]$RuntimeFile,

    [Parameter(Mandatory = $false)]
    [string]$ServerId = "autoftbq"
)

$ErrorActionPreference = "Stop"

function Escape-TomlBasicString {
    param([string]$Value)
    return $Value.Replace("\", "\\").Replace('"', '\"')
}

try {
    $runtimePath = [System.IO.Path]::GetFullPath($RuntimeFile)
    if (-not (Test-Path -LiteralPath $runtimePath)) {
        throw "runtime.json does not exist: $runtimePath"
    }

    $runtime = Get-Content -LiteralPath $runtimePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not $runtime.url) {
        throw "runtime.json does not contain an MCP URL."
    }

    $helper = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "codex_headers.ps1"))
    $helperCommand = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + $helper + '" -RuntimeFile "' + $runtimePath + '"'

    $escapedUrl = Escape-TomlBasicString ([string]$runtime.url)
    $escapedCommand = Escape-TomlBasicString $helperCommand

    @"
[mcp_servers.$ServerId]
enabled = true
required = false
url = "$escapedUrl"
http_headers_helper = "$escapedCommand"
startup_timeout_sec = 10
tool_timeout_sec = 60
default_tools_approval_mode = "writes"
"@
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
