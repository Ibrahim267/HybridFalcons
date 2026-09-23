@echo off
REM ============================================================
REM  FitDeveloper plugin - detached build driver
REM  Builds plugin zip into build\distributions\ using:
REM    - JDK21 : Android Studio's bundled JBR (full JDK, has javac)
REM    - Gradle: cached 8.12 distribution (no wrapper download)
REM  Output log: build_log.txt (safe to poll while running)
REM ============================================================
setlocal
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE=C:\Users\loq\.gradle\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\bin\gradle.bat"
cd /d "C:\Users\loq\Documents\hackathons\JetBrains\FitDeveloper\plugin"

echo === FitDeveloper plugin build started %DATE% %TIME% === > build_log.txt
echo JAVA_HOME=%JAVA_HOME% >> build_log.txt
call "%GRADLE%" --no-daemon clean buildPlugin --stacktrace >> build_log.txt 2>&1
echo EXITCODE=%ERRORLEVEL% >> build_log.txt
echo === build finished %DATE% %TIME% === >> build_log.txt
endlocal
