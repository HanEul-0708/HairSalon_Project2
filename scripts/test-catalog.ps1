param(
 [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
 [string[]]$Tasks = @('test', 'mysqlTest', 'bootJar'),
 [switch]$KeepServer,
 [switch]$Smoke,
 [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runDir = Join-Path $workspace ('build\catalog-mysql\' + [Guid]::NewGuid().ToString('N'))
$dataDir = Join-Path $runDir 'data'
$null = New-Item -ItemType Directory -Path $dataDir -Force
$mysqld = Join-Path $MySqlBin 'mysqld.exe'
$mysql = Join-Path $MySqlBin 'mysql.exe'
$mysqladmin = Join-Path $MySqlBin 'mysqladmin.exe'
if (!(Test-Path -LiteralPath $mysqld)) { throw "MySQL 8 server not found: $mysqld" }
$listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$oldUrl = $env:CATALOG_TEST_DB_URL
$oldUser = $env:CATALOG_TEST_DB_USER
$oldPassword = $env:CATALOG_TEST_DB_PASSWORD
$oldOwnedApp = $env:CATALOG_SMOKE_OWNED_APP
$server = $null
$application = $null
try {
 $init = Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', '--initialize-insecure', ('--datadir="{0}"' -f $dataDir)) -PassThru -Wait -WindowStyle Hidden -RedirectStandardError (Join-Path $runDir 'initialize.log')
 if ($init.ExitCode -ne 0) { throw "MySQL initialization failed; see $runDir\initialize.log" }
 $server = Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', ('--datadir="{0}"' -f $dataDir), "--port=$port", '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-log-bin', '--innodb-buffer-pool-size=64M', '--performance-schema=OFF', '--max-connections=30', ('--log-error="{0}"' -f (Join-Path $runDir 'mysql.log'))) -PassThru -WindowStyle Hidden
 $connection = @('--no-defaults', '--host=127.0.0.1', "--port=$port", '--user=root', '--connect-timeout=2')
 $ready = $false
 for ($attempt = 0; $attempt -lt 100; $attempt++) {
  if ($server.HasExited) { throw "Isolated MySQL stopped; see $runDir\mysql.log" }
  & $mysqladmin @connection ping --silent 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ready = $true; break }
  Start-Sleep -Milliseconds 250
 }
 if (!$ready) { throw 'Isolated MySQL did not become ready.' }
 & $mysql @connection --execute='CREATE DATABASE catalog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'
 if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated test database.' }
 $env:CATALOG_TEST_DB_URL = "jdbc:mysql://127.0.0.1:$port/catalog_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
 $env:CATALOG_TEST_DB_USER = 'root'
 $env:CATALOG_TEST_DB_PASSWORD = ''
 $manifest = @{ pid = $server.Id; port = $port; url = $env:CATALOG_TEST_DB_URL; directory = $runDir }
 $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $workspace 'build\catalog-mysql\latest.json') -Encoding UTF8
 Write-Output "Isolated MySQL ready on 127.0.0.1:$port (PID $($server.Id))."
 if ($Tasks.Count -gt 0) {
  Push-Location $workspace
  try {
   & .\gradlew.bat @Tasks --offline --console=plain
   if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed with exit code $LASTEXITCODE." }
  } finally { Pop-Location }
 }
 if ($Smoke) {
  if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
   throw 'Port 8080 is already in use. Stop the existing application before running the smoke test.'
  }
  if (!$JavaHome) { throw 'Set JAVA_HOME to JDK 21 or pass -JavaHome.' }
  $java = Join-Path $JavaHome 'bin\java.exe'
  if (!(Test-Path -LiteralPath $java)) { throw "Java executable not found: $java" }
  $jar = Get-ChildItem (Join-Path $workspace 'build\libs\*SNAPSHOT.jar') | Where-Object { $_.Name -notlike '*plain*' } | Select-Object -First 1
  if (!$jar) { throw 'Build the executable JAR with bootJar first.' }
  $arguments = @('-jar', ('"{0}"' -f $jar.FullName), '--spring.config.location=file:src/test/resources/application-catalog-test.properties', '--server.port=8080', '--spring.devtools.restart.enabled=false')
  for ($round = 1; $round -le 2; $round++) {
  $application = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $workspace -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runDir "application-$round.log") -RedirectStandardError (Join-Path $runDir "application-$round-error.log")
  $httpReady = $false
  for ($attempt = 0; $attempt -lt 90; $attempt++) {
   if ($application.HasExited) { throw "Application startup failed; see $runDir\application-$round.log and application-$round-error.log" }
   try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/members/login' -TimeoutSec 2
    if ($response.StatusCode -eq 200) { $httpReady = $true; break }
   } catch { }
   Start-Sleep -Milliseconds 500
  }
  if (!$httpReady) { throw 'Application did not become ready on port 8080.' }
  $env:CATALOG_SMOKE_OWNED_APP = 'true'
  Push-Location $workspace
  try {
   & node src/test/browser/catalog-smoke.mjs
   if ($LASTEXITCODE -ne 0) { throw "Live catalog smoke failed; see $runDir\application-$round.log" }
  } finally { Pop-Location }
  Stop-Process -Id $application.Id
  $null = $application.WaitForExit(10000)
  Write-Output "Port 8080 application verification round $round completed and stopped."
  }
 }
} finally {
 try {
  if ($null -ne $application -and !$application.HasExited) {
   Stop-Process -Id $application.Id
   $null = $application.WaitForExit(10000)
  }
  if ($null -ne $server -and !$server.HasExited -and !$KeepServer) {
   & $mysqladmin --no-defaults --host=127.0.0.1 "--port=$port" --user=root shutdown 2>$null | Out-Null
   if (!$server.WaitForExit(10000)) { Stop-Process -Id $server.Id }
  }
 } finally {
  $env:CATALOG_TEST_DB_URL = $oldUrl
  $env:CATALOG_TEST_DB_USER = $oldUser
  $env:CATALOG_TEST_DB_PASSWORD = $oldPassword
  $env:CATALOG_SMOKE_OWNED_APP = $oldOwnedApp
 }
}
