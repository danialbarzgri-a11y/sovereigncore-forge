@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem Builds the release JAR using ForgeGradle. Needs Java 17 and internet access.
set "GRADLE_VERSION=8.1.1"
set "TOOLS_DIR=%~dp0.tools"
set "GRADLE_HOME=%TOOLS_DIR%\gradle-%GRADLE_VERSION%"
set "GRADLE_EXE=%GRADLE_HOME%\bin\gradle.bat"

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java was not found. Install a JDK 17 and try again.
  pause
  exit /b 1
)

if not exist "%GRADLE_EXE%" (
  echo Gradle %GRADLE_VERSION% is not installed in this project. Downloading it now...
  if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP 'sovereigncore-gradle-8.1.1-bin.zip'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.1.1-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%TOOLS_DIR%' -Force; Remove-Item $zip -Force"
  if errorlevel 1 (
    echo ERROR: Could not download Gradle. Check your internet connection and try again.
    pause
    exit /b 1
  )
)

call "%GRADLE_EXE%" --version
if errorlevel 1 (
  echo ERROR: Gradle could not start. Check your Java installation.
  pause
  exit /b 1
)

echo Building SovereignCore for Minecraft 1.20.1 Forge...
call "%GRADLE_EXE%" build --stacktrace
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the full error above to diagnose it.
  pause
  exit /b 1
)

echo.
echo BUILD SUCCESSFUL. Look inside build\libs for the reobfuscated JAR.
pause
