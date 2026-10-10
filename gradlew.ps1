# PowerShell 7 entry point; forwards arguments to the official Gradle Wrapper.
$ErrorActionPreference = 'Stop'
$javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java).Source }
if (-not (Test-Path -LiteralPath $javaPath)) { throw "Java not found: $javaPath" }
& $javaPath '-classpath' (Join-Path $PSScriptRoot 'gradle\wrapper\gradle-wrapper.jar') 'org.gradle.wrapper.GradleWrapperMain' @args
exit $LASTEXITCODE
