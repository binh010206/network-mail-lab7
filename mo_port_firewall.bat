@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ========================================================
echo   MO CONG TUONG LUA WINDOWS (PORTS 2525 VA 1110)
echo ========================================================
echo.

net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [!] Vui long xac nhan "YES" tren hop thoai he thong de cap quyen...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo -> Dang them quyen Inbound cho cong SMTP (2525)...
netsh advfirewall firewall add rule name="VKU Mail Server Port 2525" dir=in action=allow protocol=TCP localport=2525 >nul

echo -> Dang them quyen Inbound cho cong POP3 (1110)...
netsh advfirewall firewall add rule name="VKU Mail Server Port 1110" dir=in action=allow protocol=TCP localport=1110 >nul

echo.
echo ========================================================
echo   [OK] MO CONG THANH CONG!
echo   Cac may Client trong mang LAN bay gio da co the
echo   ket noi truc tiep vao may chu Server nay!
echo ========================================================
echo.
pause
