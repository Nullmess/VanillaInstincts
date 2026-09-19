@echo off
setlocal
set APP_HOME=%~dp0
if /I "%~1"=="cleanAll" goto clean_all
if /I "%~1"=="cleanAllBuilds" goto clean_all
set FORGE_12111=false
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_MAIN=org.gradle.wrapper.GradleWrapperMain
for %%A in (%*) do (
  if "%%~A"=="-Ptarget=forge-1.21.11" (
    set FORGE_12111=true
    set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper-fg7.jar
    set WRAPPER_MAIN=fr.vanillainstincts.wrapper.Gradle95WrapperMain
  )
)

if "%FORGE_12111%"=="true" (
  if not defined JAVA_HOME (
    for %%I in (java.exe) do set "JAVA_EXE_FOUND=%%~$PATH:I"
    if not defined JAVA_EXE_FOUND (
      echo ERROR: Java 21 JDK is required for Forge 1.21.11. 1>&2
      exit /b 1
    )
    for %%I in ("%JAVA_EXE_FOUND%\..\..") do set "JAVA_HOME=%%~fI"
  )
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: JAVA_HOME must point to a Java 21 JDK for Forge 1.21.11: %JAVA_HOME% 1>&2
    exit /b 1
  )
  if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: JAVA_HOME must point to a full Java 21 JDK for Forge 1.21.11: %JAVA_HOME% 1>&2
    exit /b 1
  )
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  rem Isolate Forge 1.21.11 from any stale JDK cached in the global Gradle home.
  set "GRADLE_USER_HOME=%APP_HOME%.gradle\forge-1.21.11-user-home"
  if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"
  set "GRADLE_JAVA_ARGS=-Dorg.gradle.java.home=%JAVA_HOME% -Dorg.gradle.java.installations.auto-detect=false -Dorg.gradle.java.installations.auto-download=false -Dorg.gradle.java.installations.paths=%JAVA_HOME%"
  if defined GRADLE_OPTS (
    set "GRADLE_OPTS=%GRADLE_OPTS% %GRADLE_JAVA_ARGS%"
  ) else (
    set "GRADLE_OPTS=%GRADLE_JAVA_ARGS%"
  )
) else (
  if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
  ) else (
    set JAVA_EXE=java.exe
  )
  set "GRADLE_JAVA_ARGS="
)

cd /d "%APP_HOME%"
"%JAVA_EXE%" %GRADLE_JAVA_ARGS% -classpath "%WRAPPER_JAR%" %WRAPPER_MAIN% %*
exit /b %ERRORLEVEL%

:clean_all
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$root=[IO.Path]::GetFullPath('%APP_HOME%'); $cache=[IO.Path]::GetFullPath((Join-Path $root '.gradle')); $git=[IO.Path]::GetFullPath((Join-Path $root '.git')); $dirs=@(Get-ChildItem -LiteralPath $root -Directory -Filter build -Recurse -Force -ErrorAction SilentlyContinue ^| Where-Object { -not $_.FullName.StartsWith($cache,[StringComparison]::OrdinalIgnoreCase) -and -not $_.FullName.StartsWith($git,[StringComparison]::OrdinalIgnoreCase) }); $count=$dirs.Count; $dirs ^| Sort-Object { $_.FullName.Length } -Descending ^| Remove-Item -Recurse -Force -ErrorAction Stop; $rootBuild=Join-Path $cache 'root-project'; if(Test-Path -LiteralPath $rootBuild){ Remove-Item -LiteralPath $rootBuild -Recurse -Force; $count++ }; Write-Host ('CLEAN_ALL: removed {0} generated build directories; caches/toolchains preserved.' -f $count)"
exit /b %ERRORLEVEL%
