@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem ===========================================================================
rem  One-click signed RELEASE build for the Android client.
rem
rem    build-release.bat [--no-pause] [--keep-anchor] [extra gradle -P args]
rem
rem  Steps:
rem    1. preflight: keystore.properties, JDK, clean IntegrityAnchor.java
rem    2. gradlew writeIntegrityAnchor   stamp the release cert anchor
rem    3. gradlew assembleRelease        R8 + shrink + sign, publish to release\
rem    4. verify a signed APK landed in release\ and print the cert SHA-256
rem    5. restore the committed IntegrityAnchor.java placeholder (unless
rem       --keep-anchor) so the working tree is left clean
rem
rem  Requires keystore.properties in this folder (never committed; see
rem  keystore.properties.example). Design + release notes:
rem  ../docs/android-client-hardening-design.md
rem
rem  ASCII-only on purpose: cmd.exe parses batch files in the active OEM/ANSI
rem  code page, so non-ASCII bytes garble and can corrupt parsing on some
rem  consoles.
rem ===========================================================================

cd /d "%~dp0"
set "MODULE_DIR=%CD%"
set "ANCHOR_SRC=src\main\java\com\cube\memorygames\security\IntegrityAnchor.java"
set "LOG_DIR=%MODULE_DIR%\build\release-tool"
set "EXIT_CODE=0"
set "NO_PAUSE="
set "KEEP_ANCHOR="
set "NEW_APK="
set "EXTRA_ARGS="

:parse_args
if "%~1"=="" goto args_done
set "ARG=%~1"
if /i "%ARG%"=="--no-pause" ( set "NO_PAUSE=1" & shift & goto parse_args )
if /i "%ARG%"=="--keep-anchor" ( set "KEEP_ANCHOR=1" & shift & goto parse_args )
if /i "%ARG%"=="-h" goto help
if /i "%ARG%"=="--help" goto help
if /i "%ARG:~0,1%"=="-" (
    echo [ERROR] unknown option: %ARG%
    echo.
    call :print_usage
    set "EXIT_CODE=2" & goto finish
)
set "EXTRA_ARGS=%EXTRA_ARGS% %ARG%"
shift
goto parse_args
:args_done

echo ===========================================================================
echo  Signed release build  -  %MODULE_DIR%
echo ===========================================================================
echo.
echo [1/5] Preflight

if not exist "gradlew.bat" (
    echo   [ERROR] gradlew.bat not found next to this script.
    set "EXIT_CODE=2" & goto finish
)

if not exist "keystore.properties" (
    echo   [ERROR] keystore.properties not found in this folder.
    echo          Copy keystore.properties.example to keystore.properties,
    echo          fill in the release keystore path and passwords, and keep
    echo          keystore.properties out of version control ^(it is already
    echo          listed in .gitignore^).
    set "EXIT_CODE=2" & goto finish
)

call :find_jdk
if not defined JAVA_HOME (
    echo   [ERROR] No JDK found. Set JAVA_HOME, or install one at F:\jdk17
    set "EXIT_CODE=2" & goto finish
)
echo   JAVA_HOME=%JAVA_HOME%

where git >nul 2>nul
if errorlevel 1 (
    echo   [WARN] git not found on PATH - the anchor placeholder cannot be
    echo          restored automatically; IntegrityAnchor.java will stay stamped.
    set "KEEP_ANCHOR=1"
) else (
    for /f "delims=" %%s in ('git status --porcelain -- "%ANCHOR_SRC%" 2^>nul') do set "ANCHOR_DIRTY=1"
)
if defined ANCHOR_DIRTY if not defined KEEP_ANCHOR (
    echo   [ERROR] %ANCHOR_SRC%
    echo          has uncommitted changes. Restore it first:
    echo            git checkout -- "%ANCHOR_SRC%"
    echo          Or pass --keep-anchor to stamp and keep working on it.
    set "EXIT_CODE=2" & goto finish
)

rem Remember how release\ looked before the build, so step 4 can prove that
rem this build published a (signed) APK instead of trusting the exit code.
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1
if exist "%MODULE_DIR%\release" ( dir /a-d "%MODULE_DIR%\release" ) else ( echo MISSING ) > "%LOG_DIR%\release-before.txt" 2>nul

echo.
echo [2/5] Stamping the release certificate anchor ^(writeIntegrityAnchor^)
call gradlew.bat --console=plain writeIntegrityAnchor %EXTRA_ARGS%
if errorlevel 1 (
    echo   [ERROR] writeIntegrityAnchor failed. Check keystore.properties
    echo          ^(keys present, storeFile exists, storeType matches^).
    set "EXIT_CODE=1" & goto anchor_restore
)

echo.
echo [3/5] Building the signed release package ^(assembleRelease^)
call gradlew.bat --console=plain assembleRelease %EXTRA_ARGS%
if errorlevel 1 (
    echo   [ERROR] assembleRelease failed - see the Gradle output above.
    set "EXIT_CODE=1" & goto anchor_restore
)

echo.
echo [4/5] Verifying the published package
if exist "%MODULE_DIR%\release" ( dir /a-d "%MODULE_DIR%\release" ) else ( echo MISSING ) > "%LOG_DIR%\release-after.txt" 2>nul
fc "%LOG_DIR%\release-before.txt" "%LOG_DIR%\release-after.txt" >nul 2>&1
if errorlevel 1 goto published
echo   [ERROR] release\ is unchanged - nothing was published.
echo          Most likely the APK came out unsigned. Look for
echo          "no signed release APK" in the Gradle output above and verify
echo          keystore.properties.
set "EXIT_CODE=1" & goto anchor_restore

:published
for /f "delims=" %%f in ('dir /b /o-d "%MODULE_DIR%\release\memory-game-*.apk" 2^>nul') do (
    echo %%f | findstr /i /c:"-unsigned" >nul || ( set "NEW_APK=%%f" & goto found_apk )
)
:found_apk
if not defined NEW_APK (
    echo   [ERROR] no signed APK found in release\ after the build.
    set "EXIT_CODE=1" & goto anchor_restore
)
echo   [OK] signed APK: release\%NEW_APK%
echo.
echo [5/5] Recording certificate fingerprint
call gradlew.bat --console=plain printReleaseCertSha256 %EXTRA_ARGS%

:anchor_restore
if defined KEEP_ANCHOR (
    echo.
    echo   [INFO] --keep-anchor: IntegrityAnchor.java stays stamped; git will
    echo          show it as modified.
    goto finish
)
git checkout -- "%ANCHOR_SRC%" >nul 2>&1
if errorlevel 1 (
    echo.
    echo   [WARN] Could not restore the anchor placeholder. Restore it manually:
    echo            git checkout -- "%ANCHOR_SRC%"
) else (
    echo.
    echo   [INFO] IntegrityAnchor.java restored to the committed placeholder.
)

:finish
echo.
if not "%EXIT_CODE%"=="0" (
    echo [FAILED] exit code %EXIT_CODE% - see the messages above.
) else (
    echo [DONE] Signed release package ready.
    echo        APK     : release\%NEW_APK%
    echo        Mapping : build\outputs\mapping\release\mapping.txt  ^(archive it
    echo                  for crash triage: R8 obfuscates stack traces^)
)
if not defined NO_PAUSE pause
exit /b %EXIT_CODE%

:find_jdk
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" (
    echo   [WARN] JAVA_HOME is set but %%JAVA_HOME%%\bin\java.exe is missing; ignoring it
    set "JAVA_HOME="
)
if not defined JAVA_HOME if exist "F:\jdk17\bin\java.exe" (
    echo   [INFO] JAVA_HOME not set; using F:\jdk17
    set "JAVA_HOME=F:\jdk17"
)
exit /b 0

:help
call :print_usage
exit /b 0

:print_usage
echo Usage: build-release.bat [--no-pause] [--keep-anchor] [gradle -P args]
echo.
echo   --no-pause      exit without waiting for a key press
echo   --keep-anchor   keep the stamped IntegrityAnchor.java
echo                   (default: restore the committed placeholder)
echo.
echo Builds a signed, R8-obfuscated release APK and publishes it to release\
echo together with the certificate SHA-256 for the release notes.
echo Requires keystore.properties in this folder - see keystore.properties.example
exit /b 0
