@echo off
rem ============================================================
rem  CrunchGuard firewall fix - adds inbound rules for the
rem  relay ports (HTTP 8787 + HTTPS 8788). Self-elevates.
rem ============================================================
net session >nul 2>&1
if errorlevel 1 (
  echo Requesting administrator rights...
  powershell -NoProfile -Command "Start-Process -FilePath \"%~f0\" -Verb RunAs"
  exit /b
)
echo Adding firewall rules for CrunchGuard...
netsh advfirewall firewall delete rule name="CrunchGuard HTTP"  >nul 2>&1
netsh advfirewall firewall delete rule name="CrunchGuard HTTPS" >nul 2>&1
netsh advfirewall firewall add rule name="CrunchGuard HTTP"  dir=in action=allow protocol=TCP localport=8787
netsh advfirewall firewall add rule name="CrunchGuard HTTPS" dir=in action=allow protocol=TCP localport=8788
echo.
echo Done. Your phone should now be able to reach the server.
echo (Also make sure phone and PC are on the SAME Wi-Fi.)
echo.
timeout /t 6 /nobreak >nul
