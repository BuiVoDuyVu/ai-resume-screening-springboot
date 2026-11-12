[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 5433,
    [string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin',
    [switch]$Status
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$clusterRoot = Join-Path $projectRoot '.local\postgres'
$dataDir = Join-Path $clusterRoot 'data'
$passwordFile = Join-Path $clusterRoot 'admin-password.txt'
$markerFile = Join-Path $clusterRoot 'managed-cluster.txt'
$envFile = Join-Path $projectRoot '.env'
$managedMarker = '# Managed by scripts/Start-LocalPostgres.ps1'
$databaseUrl = "jdbc:postgresql://127.0.0.1:$Port/cv_matching"
$utf8 = New-Object Text.UTF8Encoding($false)
$secrets = New-Object 'System.Collections.Generic.List[string]'

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

function Protect-PrivatePath([string]$Path, [switch]$Directory) {
    # Change only the DACL; Set-Acl with a new security descriptor can require
    # SeSecurityPrivilege even though a standard user owns these project files.
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $identities = @($identity, 'S-1-5-18', 'S-1-5-32-544')
    $permissions = @()
    foreach ($sid in $identities) {
        $permissions += '*' + $sid + $(if ($Directory) { ':(OI)(CI)F' } else { ':F' })
    }
    $aclOutput = & (Join-Path $env:SystemRoot 'System32\icacls.exe') $Path '/inheritance:r' '/grant:r' @permissions 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Cannot protect private PostgreSQL files: ' + ($aclOutput -join ' ')) }
}

function Get-NewPassword {
    $bytes = New-Object byte[] 36
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_')
}

function ConvertTo-NativeArgument([string]$Argument) {
    return '"' + (($Argument -replace '(\\*)"', '$1$1\"') -replace '(\\+)$', '$1$1') + '"'
}

function Invoke-PostgresTool([string]$Name, [string[]]$Arguments, [string]$InputText = '', [string]$Password = '') {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = Join-Path $PostgresBin "$Name.exe"
    $info.Arguments = ($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
    $info.WorkingDirectory = $projectRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = $utf8
    $info.StandardErrorEncoding = $utf8
    # Set child-only environment variables; the caller's PostgreSQL environment is unchanged.
    foreach ($key in @('PGPASSWORD', 'PGSERVICE', 'PGSERVICEFILE', 'PGOPTIONS', 'PGPASSFILE')) {
        $info.EnvironmentVariables.Remove($key)
    }
    $info.EnvironmentVariables['PGCONNECT_TIMEOUT'] = '5'
    $info.EnvironmentVariables['PGCLIENTENCODING'] = 'UTF8'
    if ($Password) { $info.EnvironmentVariables['PGPASSWORD'] = $Password }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        if ($InputText) {
            $inputBytes = $utf8.GetBytes($InputText)
            $process.StandardInput.BaseStream.Write($inputBytes, 0, $inputBytes.Length)
        }
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(120000)) {
            $process.Kill()
            throw "$Name did not finish within two minutes."
        }
        # The Windows postmaster can inherit a pg_ctl pipe after pg_ctl exits.
        # Do not wait indefinitely for that background server to close the pipe.
        [void][Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]@($outputTask, $errorTask), 2000)
        $output = ''
        if ($outputTask.IsCompleted) { $output += $outputTask.Result }
        if ($errorTask.IsCompleted) { $output += $errorTask.Result }
        foreach ($secret in $secrets) { $output = $output.Replace($secret, '[redacted]') }
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $output.Trim() }
    } finally { $process.Dispose() }
}

function Assert-ToolSuccess($Result, [string]$Operation) {
    if ($Result.ExitCode -ne 0) { throw "$Operation failed (exit $($Result.ExitCode)). $($Result.Output)" }
}

function Get-PortListeners {
    return @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

# PostgreSQL on Windows works most reliably with an ASCII short path when one exists.
function Get-NativePath([string]$Path) {
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
    $length = [CvMatchingPostgres.NativePath]::GetShortPathName($Path, $buffer, [uint32]$buffer.Capacity)
    if ($length -gt 0 -and $length -lt $buffer.Capacity) { return $buffer.ToString() }
    return $Path
}

foreach ($path in @($clusterRoot, $dataDir, $passwordFile, $markerFile, $envFile)) { Assert-PrivatePath $path }
$PostgresBin = [IO.Path]::GetFullPath($PostgresBin)
foreach ($name in @('initdb', 'pg_ctl', 'pg_isready', 'psql')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin "$name.exe") -PathType Leaf)) {
        throw "Missing $name.exe. Supply -PostgresBin with an existing PostgreSQL bin directory."
    }
}

$hasCluster = Test-Path -LiteralPath (Join-Path $dataDir 'PG_VERSION') -PathType Leaf
if ($hasCluster -and (-not (Test-Path -LiteralPath $markerFile) -or [IO.File]::ReadAllText($markerFile).Trim() -ne $managedMarker)) {
    throw 'The data directory is not marked as a cluster managed by this project. It will not be touched.'
}
$running = $false
if ($hasCluster) {
    $clusterStatus = Invoke-PostgresTool 'pg_ctl' @('status', '-D', (Get-NativePath $dataDir))
    if ($clusterStatus.ExitCode -notin @(0, 3)) { Assert-ToolSuccess $clusterStatus 'Checking the project cluster' }
    $running = $clusterStatus.ExitCode -eq 0
}
$listeners = @(Get-PortListeners)
if ($running) {
    $pidFile = Join-Path $dataDir 'postmaster.pid'
    Assert-PrivatePath $pidFile
    $pidLines = [IO.File]::ReadAllLines($pidFile)
    if ($pidLines.Length -lt 6 -or $pidLines[3] -ne "$Port" -or $pidLines[5].Trim() -ne '127.0.0.1') {
        throw 'This project cluster is running with different port/listen settings. Stop it with the project stop helper first.'
    }
    $serverProcessId = [int]$pidLines[0]
    if ($listeners.Count -eq 0 -or @($listeners | Where-Object { $_.OwningProcess -ne $serverProcessId }).Count -gt 0) {
        throw 'The requested port cannot be verified as belonging only to this project cluster.'
    }
} elseif ($listeners.Count -gt 0) {
    throw "Port $Port is already occupied. No existing server was stopped or changed; choose another -Port."
}

if ($Status) {
    if (-not $running) { Write-Host "Project PostgreSQL: stopped (configured helper port $Port)."; return }
    $ready = Invoke-PostgresTool 'pg_isready' @('-h', '127.0.0.1', '-p', "$Port", '-t', '3')
    Assert-ToolSuccess $ready 'Checking readiness'
    Write-Host "Project PostgreSQL: running and ready on 127.0.0.1:$Port."
    return
}

# Do not overwrite a developer's own connection settings, even on the first run.
$appPassword = $null
if (Test-Path -LiteralPath $envFile) {
    $envLines = [IO.File]::ReadAllLines($envFile)
    if ($envLines -notcontains $managedMarker) {
        throw 'An unmanaged .env already exists. Preserve it and configure PostgreSQL manually, or move it before using this helper.'
    }
    $properties = @{}
    foreach ($line in $envLines) {
        if ($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') { $properties[$Matches[1]] = $Matches[2] }
    }
    if ($properties['DB_URL'] -ne $databaseUrl -or $properties['DB_USERNAME'] -ne 'cv_matching' -or $properties['DB_PASSWORD'] -notmatch '^[A-Za-z0-9_-]{48}$') {
        throw 'The managed .env has changed or uses another port. Refusing to overwrite its credentials.'
    }
    $appPassword = $properties['DB_PASSWORD']
    $secrets.Add($appPassword)
    if (-not $hasCluster) { throw 'A managed .env exists without its initialized cluster. Refusing to replace or reset existing credentials.' }
}

if (-not $hasCluster) {
    if ((Test-Path -LiteralPath $dataDir) -and @(Get-ChildItem -LiteralPath $dataDir -Force).Count -gt 0) {
        throw 'The private data directory is nonempty but not initialized. Inspect it before retrying; no files were deleted.'
    }
    [void][IO.Directory]::CreateDirectory($clusterRoot)
    Protect-PrivatePath $clusterRoot -Directory
    [IO.File]::WriteAllText($markerFile, $managedMarker + [Environment]::NewLine, $utf8)
    if (-not (Test-Path -LiteralPath $passwordFile)) {
        [IO.File]::WriteAllText($passwordFile, (Get-NewPassword) + [Environment]::NewLine, $utf8)
    }
    Protect-PrivatePath $passwordFile
    [void][IO.Directory]::CreateDirectory($dataDir)
    $initialization = Invoke-PostgresTool 'initdb' @('-D', (Get-NativePath $dataDir), '--username=postgres', '--encoding=UTF8', '--locale=C', '--auth-host=scram-sha-256', '--auth-local=scram-sha-256', '--pwfile', (Get-NativePath $passwordFile))
    Assert-ToolSuccess $initialization 'Initializing the private PostgreSQL cluster'
}
if (-not (Test-Path -LiteralPath $passwordFile)) { throw 'The private admin password file is missing. Existing credentials will not be reset.' }
$adminPassword = [IO.File]::ReadAllText($passwordFile).Trim()
if ($adminPassword -notmatch '^[A-Za-z0-9_-]{48}$') { throw 'The private admin password file has an unexpected format. It will not be replaced.' }
$secrets.Add($adminPassword)
Protect-PrivatePath $clusterRoot -Directory
Protect-PrivatePath $passwordFile

if (-not $running) {
    $logFile = Join-Path $clusterRoot 'postgres.log'
    Assert-PrivatePath $logFile
    $start = Invoke-PostgresTool 'pg_ctl' @('start', '-D', (Get-NativePath $dataDir), '-l', (Join-Path (Get-NativePath $clusterRoot) 'postgres.log'), '-o', "-h 127.0.0.1 -p $Port", '-w', '-t', '60')
    Assert-ToolSuccess $start "Starting the project PostgreSQL cluster; inspect $logFile for details"
}
$ready = Invoke-PostgresTool 'pg_isready' @('-h', '127.0.0.1', '-p', "$Port", '-t', '3')
Assert-ToolSuccess $ready 'Checking PostgreSQL readiness'

if (-not $appPassword) {
    $appPassword = Get-NewPassword
    $secrets.Add($appPassword)
    # This is a Java .properties file, imported by Spring; generated secrets need no escaping.
    $envContents = "$managedMarker`n# Local development only; keep this file private.`nDB_URL=$databaseUrl`nDB_USERNAME=cv_matching`nDB_PASSWORD=$appPassword`n"
    $temporaryEnv = Join-Path $clusterRoot ('connection-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    Assert-PrivatePath $temporaryEnv
    $stream = New-Object IO.FileStream($temporaryEnv, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = $utf8.GetBytes($envContents)
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
    Protect-PrivatePath $temporaryEnv
    # An atomic move preserves the private ACL and refuses to replace an existing .env.
    [IO.File]::Move($temporaryEnv, $envFile)
} else { Protect-PrivatePath $envFile }

# SQL is passed through stdin, never the command line. The application role has no cluster administration privileges.
$sqlPassword = $appPassword.Replace("'", "''")
$sql = @"
SET password_encryption = 'scram-sha-256';
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', 'cv_matching', '$sqlPassword')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cv_matching') \gexec
ALTER ROLE cv_matching WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$sqlPassword';
SELECT 'CREATE DATABASE cv_matching OWNER cv_matching'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cv_matching') \gexec
ALTER DATABASE cv_matching OWNER TO cv_matching;
REVOKE ALL ON DATABASE cv_matching FROM PUBLIC;
"@
$bootstrap = Invoke-PostgresTool 'psql' @('--no-psqlrc', '--no-password', '--quiet', '--set=ON_ERROR_STOP=1', '--host=127.0.0.1', "--port=$Port", '--username=postgres', '--dbname=postgres') ($sql + "`n") $adminPassword
Assert-ToolSuccess $bootstrap 'Creating the application role and database'
$connection = Invoke-PostgresTool 'psql' @('--no-psqlrc', '--no-password', '--quiet', '--tuples-only', '--set=ON_ERROR_STOP=1', '--host=127.0.0.1', "--port=$Port", '--username=cv_matching', '--dbname=cv_matching') "SELECT current_user;`n" $appPassword
Assert-ToolSuccess $connection 'Verifying the application database connection'
if ($connection.Output.Trim() -ne 'cv_matching') { throw 'Unexpected identity returned by the application database connection.' }
Write-Host "Project PostgreSQL is ready on 127.0.0.1:$Port (database/user: cv_matching)."
Write-Host 'The private connection settings are in .env. Run the backend from the project directory to load them.'
