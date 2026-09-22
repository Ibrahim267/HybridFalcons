@echo off
rem ============================================================
rem  CrunchGuard cert generator (Windows)
rem  Regenerates the self-signed cert used for HTTPS :8788.
rem  iOS motion sensors need HTTPS; Android works over HTTP.
rem  Requires openssl - included with Git for Windows.
rem ============================================================
where openssl >nul 2>nul
if errorlevel 1 (
  echo.
  echo   openssl not found. Install Git for Windows ^(it includes openssl^),
  echo   or run gen_cert.sh from Git Bash.
  echo.
  pause
  exit /b 1
)
cd /d "%~dp0"
openssl req -x509 -newkey rsa:2048 -sha256 -days 365 -nodes -keyout key.pem -out cert.pem -subj "/CN=CrunchGuard"
echo.
echo   Done: cert.pem + key.pem generated (365 days, self-signed).
echo   Restart the server:  restart_server.bat
echo.
pause
