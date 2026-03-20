param(
    [switch]$IncludeConnectedTests,
    [switch]$SkipClean,
    [ValidateRange(0, 5)][int]$MaxRetries = 2
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

if (-not (Test-Path ".\gradlew.bat")) {
    throw "gradlew.bat was not found at $projectRoot"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logsDir = Join-Path $projectRoot "build-logs"
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
$logFile = Join-Path $logsDir "pre-release-$timestamp.log"
$gradleArgs = "--console=plain --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Pkotlin.incremental=false"

function Invoke-ReleaseCleanup {
    $cleanupTargets = @(
        ".\app\build\kotlin",
        ".\app\build\intermediates\built_in_kotlinc",
        ".\app\build\intermediates\compile_and_runtime_r_class_jar",
        ".\app\build\intermediates\runtime_app_classes_jar"
    )

    foreach ($target in $cleanupTargets) {
        if (Test-Path $target) {
            try {
                Remove-Item -Recurse -Force $target -ErrorAction Stop
            }
            catch {
                Write-Warning "Cleanup skipped for locked path: $target"
            }
        }
    }
}

function Invoke-GradleStep {
    param(
        [Parameter(Mandatory = $true)][string]$Task,
        [Parameter(Mandatory = $true)][string]$Label,
        [switch]$AllowFailure
    )

    for ($attempt = 1; $attempt -le ($MaxRetries + 1); $attempt++) {
        Write-Host "==> $Label ($Task) [attempt $attempt/$($MaxRetries + 1)]"
        $previousErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            cmd /c ".\gradlew.bat $Task $gradleArgs" 2>&1 | Tee-Object -FilePath $logFile -Append
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorPreference
        }

        if ($exitCode -eq 0) {
            return
        }

        if ($attempt -le $MaxRetries) {
            Write-Warning "Gradle task failed: $Task. Retrying after daemon stop."
            cmd /c ".\gradlew.bat --stop" 2>&1 | Tee-Object -FilePath $logFile -Append | Out-Null
            Invoke-ReleaseCleanup
            Start-Sleep -Seconds 3
            continue
        }

        if ($AllowFailure) {
            Write-Warning "Gradle task failed but is allowed to fail: $Task"
            return
        }
        throw "Gradle task failed: $Task"
    }
}

"Pre-release verification started: $(Get-Date -Format o)" | Out-File -FilePath $logFile -Encoding utf8
"Project root: $projectRoot" | Out-File -FilePath $logFile -Encoding utf8 -Append

if (-not $SkipClean) {
    Invoke-GradleStep -Task ":app:clean" -Label "Clean" -AllowFailure
}

Invoke-GradleStep -Task ":app:testDebugUnitTest" -Label "Unit tests"
Invoke-GradleStep -Task ":app:lintDebug" -Label "Lint (debug)"
Invoke-GradleStep -Task ":app:assembleRelease" -Label "Release APK"
Invoke-GradleStep -Task ":app:bundleRelease" -Label "Release App Bundle"
Invoke-GradleStep -Task ":app:compileDebugAndroidTestKotlin" -Label "Android test compile"

if ($IncludeConnectedTests) {
    Invoke-GradleStep -Task ":app:connectedDebugAndroidTest" -Label "Connected Android tests"
}

"Pre-release verification completed: $(Get-Date -Format o)" | Out-File -FilePath $logFile -Encoding utf8 -Append
Write-Host "Pre-release verification complete. Log: $logFile"




