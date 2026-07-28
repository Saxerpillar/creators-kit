@echo off
rem ============================================================================
rem  Creator's Kit test client launcher (branch-aware).
rem  The SAME script lives in every worktree: it auto-detects the git branch and
rem  labels the console window so parallel clients are easy to tell apart. It
rem  builds from whatever is on disk first, so it always runs your latest local
rem  changes. Double-click, or run from a terminal. Assertions are ON (dev
rem  client) -- the engine uses them to enforce client-thread access.
rem  Pass-through: any extra args you give this script go to gradle runClient.
rem ============================================================================
setlocal
cd /d "%~dp0"

rem Identify this worktree: prefer the git branch, fall back to the folder name.
set "WT="
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD 2^>nul') do set "WT=%%b"
if not defined WT for %%i in ("%~dp0.") do set "WT=%%~nxi"

title Creator's Kit  -  %WT%
echo ============================================================
echo   Creator's Kit test client
echo   Branch   : %WT%
echo   Worktree : %~dp0
echo ============================================================
echo.

call gradlew.bat runClient --console=plain %*

echo.
echo [client exited - press any key to close this window]
pause >/dev/null
endlocal
