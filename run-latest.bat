@echo off
rem ============================================================================
rem  Creator's Kit -- one-click "latest" launcher.
rem
rem  Clones (first run) or updates Saxerpillar/creators-kit (branch main) from
rem  GitHub, then builds + runs the RuneLite dev client with the plugin
rem  sideloaded (gradlew runClient -> ExternalPluginManager.loadBuiltin). Always
rem  runs the latest pushed version -- no IDE needed.
rem
rem  USAGE: drop this .bat in an EMPTY folder and double-click. It manages a
rem  "creators-kit" checkout next to itself. (If this file already sits inside a
rem  creators-kit clone, it updates that clone in place with a safe fast-forward
rem  instead -- your local edits are kept.)
rem
rem  REQUIRES: git and a JDK 11+ on PATH (you already have both if you run the
rem  plugin from IntelliJ). Any extra args pass through to gradle runClient.
rem ============================================================================
setlocal
cd /d "%~dp0"

set "REPO=https://github.com/Saxerpillar/creators-kit.git"
set "BRANCH=main"

rem --- prerequisites ---------------------------------------------------------
where git  >nul 2>nul || (echo [ERROR] git not found on PATH.  Install: https://git-scm.com/download/win & goto :fail)
where java >nul 2>nul || (echo [ERROR] Java ^(JDK 11+^) not found on PATH.  Install: https://adoptium.net & goto :fail)

rem --- already inside a creators-kit checkout? update in place (safe) ---------
set "INPLACE="
if exist ".git" if exist "gradlew.bat" if exist "settings.gradle" findstr /C:"CreatorsKit" settings.gradle >nul 2>nul && set "INPLACE=1"

if defined INPLACE (
    echo Updating current checkout to latest %BRANCH% ...
    git pull --ff-only origin %BRANCH% || echo [warn] could not fast-forward -- running whatever is on disk.
    set "RUNDIR=."
) else (
    if not exist "creators-kit\.git" (
        echo Cloning %REPO% ^(%BRANCH%^) ...
        git clone --branch %BRANCH% "%REPO%" "creators-kit" || goto :fail
    ) else (
        echo Updating creators-kit to latest %BRANCH% ...
        pushd "creators-kit"
        git fetch --prune origin && git checkout %BRANCH% && git reset --hard origin/%BRANCH% || (popd & goto :fail)
        popd
    )
    set "RUNDIR=creators-kit"
)

rem --- build + run -----------------------------------------------------------
pushd "%RUNDIR%"
title Creator's Kit  -  %BRANCH% (latest)
echo.
echo ============================================================
echo   Creator's Kit  --  running latest %BRANCH%
echo   RuneLite dev client, plugin sideloaded
echo ============================================================
echo.
call gradlew.bat runClient --console=plain %*
popd

echo.
echo [client exited - press any key to close this window]
pause >nul
endlocal
goto :eof

:fail
echo.
echo [launch failed - press any key to close this window]
pause >nul
endlocal
exit /b 1
