# Load the private configuration for this checkout, also used by scheduled tasks.
param(
    [Parameter(Mandatory)]
    [ValidateSet('backup', 'diagnose', 'activate', 'rollback', 'deploy', 'environment')]
    [string]$Command,
    [string]$Tag,
    [switch]$Scheduled
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$settings = Get-Content -LiteralPath (Join-Path $repositoryRoot '.runtime/windows/settings.json') -Raw | ConvertFrom-Json
foreach ($name in @('RESTIC_REPOSITORY', 'RESTIC_PASSWORD_FILE', 'TROPICUBE_OPS_STATE', 'TROPICUBE_BACKUP_MODE')) {
    [Environment]::SetEnvironmentVariable($name, [string]$settings.$name, 'Process')
}
$env:PATH = "$($settings.toolsDirectory);$($settings.dockerDirectory);$($settings.gitDirectory);$env:PATH"
Set-Location $repositoryRoot
if ($Command -eq 'environment') { return }
# Docker Desktop and this user's session must be running. Do not start a stopped
# development stack simply to satisfy a timer, or advance its backup timestamp.
if ($Scheduled -and $Command -eq 'backup') {
    $running = & docker ps --filter 'name=^/tropicube-velocity$' --format '{{.ID}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $running) {
        'Backup skipped: development stack is stopped.' | Set-Content -LiteralPath (Join-Path $env:TROPICUBE_OPS_STATE 'backup-task.log')
        exit 0
    }
}
if ($Command -eq 'deploy') {
    & (Join-Path $repositoryRoot 'deploy.ps1')
} else {
    $arguments = @((Join-Path $PSScriptRoot 'tropicube_ops.py'), $Command)
    if ($Command -in @('activate', 'rollback')) {
        if ($Tag -notmatch '^\d{8}-\d{6}$') { throw 'Tag must be YYYYMMDD-HHMMSS.' }
        $arguments += $Tag
    }
    if ($Scheduled) {
        & $settings.python @arguments *> (Join-Path $env:TROPICUBE_OPS_STATE "$Command-task.log")
    } else {
        & $settings.python @arguments
    }
}
exit $LASTEXITCODE
