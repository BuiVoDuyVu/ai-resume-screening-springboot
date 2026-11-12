[CmdletBinding()]
param(
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$clusterRoot = Join-Path $projectRoot '.local\postgres'
$dataDir = Join-Path $clusterRoot 'data'
$markerFile = Join-Path $clusterRoot 'managed-cluster.txt'

function Assert-PrivatePath([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The PostgreSQL path must be inside this project.'
    }
    $current = $resolved
    while ($current -ne $projectRoot) {
        if ((Test-Path -LiteralPath $current) -and ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing a symbolic link or junction in the private PostgreSQL path: $current"
        }
        $current = [IO.Path]::GetDirectoryName($current)
    }
}

function ConvertTo-NativeArgument([string]$Argument) {
    return '"' + (($Argument -replace '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
}

function Invoke-Control([string[]]$Arguments) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $control
    $info.Arguments = ($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
    $info.WorkingDirectory = $projectRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = ($outputTask.Result + $errorTask.Result).Trim() }
    } finally { $process.Dispose() }
}

foreach ($path in @($clusterRoot, $dataDir, $markerFile, (Join-Path $dataDir 'postmaster.pid'))) { Assert-PrivatePath $path }
if (-not (Test-Path -LiteralPath (Join-Path $dataDir 'PG_VERSION'))) {
    Write-Host 'There is no initialized PostgreSQL cluster in this project.'
    return
}
if (-not (Test-Path -LiteralPath $markerFile) -or [IO.File]::ReadAllText($markerFile).Trim() -ne '# Managed by scripts/Start-LocalPostgres.ps1') {
    throw 'This data directory is not marked as a cluster managed by this project. It will not be stopped.'
}
$control = Join-Path ([IO.Path]::GetFullPath($PostgresBin)) 'pg_ctl.exe'
if (-not (Test-Path -LiteralPath $control -PathType Leaf)) { throw 'Missing pg_ctl.exe; supply the correct -PostgresBin directory.' }

# Resolve the same optional Windows short path used by the start helper.
if (-not ('CvMatchingPostgres.NativePath' -as [type])) {
    Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;
using System.Text;
namespace CvMatchingPostgres {
    public static class NativePath {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern uint GetShortPathName(string path, StringBuilder buffer, uint size);
    }
}
'@
}
$buffer = New-Object Text.StringBuilder 32768
$length = [CvMatchingPostgres.NativePath]::GetShortPathName($dataDir, $buffer, [uint32]$buffer.Capacity)
$nativeDataDir = if ($length -gt 0 -and $length -lt $buffer.Capacity) { $buffer.ToString() } else { $dataDir }
$status = Invoke-Control @('status', '-D', $nativeDataDir)
if ($status.ExitCode -eq 3) { Write-Host 'Project PostgreSQL is already stopped.'; return }
if ($status.ExitCode -ne 0) { throw "Cannot verify the project cluster status (exit $($status.ExitCode)). $($status.Output)" }

# Verify the recorded server process and its exact data directory before signaling it.
$pidLines = [IO.File]::ReadAllLines((Join-Path $dataDir 'postmaster.pid'))
if ($pidLines.Length -lt 4) { throw 'The project postmaster.pid file is incomplete. No process was stopped.' }
$serverProcessId = 0
if (-not [int]::TryParse($pidLines[0], [ref]$serverProcessId)) { throw 'The project postmaster.pid file contains an invalid process ID.' }
$server = Get-CimInstance Win32_Process -Filter "ProcessId = $serverProcessId"
if (-not $server -or $server.Name -ne 'postgres.exe' -or -not $server.CommandLine) {
    throw 'The recorded process cannot be verified as this project PostgreSQL server. No process was stopped.'
}
$escapedDataDir = [regex]::Escape($nativeDataDir)
$escapedLongDataDir = [regex]::Escape($dataDir)
$serverCommand = $server.CommandLine.Replace('/', '\')
if ($serverCommand -notmatch "(?i)(?:^|\s)(?:-D|`"-D`")\s+(?:`"(?:$escapedDataDir|$escapedLongDataDir)`"|(?:$escapedDataDir|$escapedLongDataDir)(?=\s|$))") {
    throw 'The PostgreSQL process command line does not match this project data directory. No process was stopped.'
}
$stop = Invoke-Control @('stop', '-D', $nativeDataDir, '-m', 'fast', '-w', '-t', '60')
if ($stop.ExitCode -ne 0) { throw "Stopping the project PostgreSQL cluster failed (exit $($stop.ExitCode)). $($stop.Output)" }
Write-Host 'Project PostgreSQL stopped. Its data and credentials were preserved.'
