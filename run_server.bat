@echo off
chcp 65001 >nul
cd /d "%~dp0"
title VKU Mail Server Console
echo ========================================================
echo   HE THONG MAY CHU MAIL SERVER (SMTP 2525 - POP3 1110)
echo ========================================================

if not exist "lib\javafx-sdk-21.0.2" (
    echo [!] Chua tim thay JavaFX SDK. Dang tu dong tai ve...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_windows-x64_bin-sdk.zip', 'lib\javafx-sdk.zip'); Expand-Archive -Path 'lib\javafx-sdk.zip' -DestinationPath 'lib' -Force; Remove-Item 'lib\javafx-sdk.zip'"
    echo [OK] Da cai dat xong JavaFX SDK!
)

if not exist bin mkdir bin
if not exist bin\resources mkdir bin\resources
copy /Y src\resources\style.css bin\resources\style.css >nul

echo [*] Dang tu dong bien dich ma nguon moi nhat...
javac -encoding UTF-8 -cp "lib\javafx-sdk-21.0.2\lib\*;lib\mongo\*" -d bin src\model\*.java src\config\*.java src\database\*.java src\network\*.java src\server\*.java src\client\*.java

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] CO LOI BIEN DICH XAY RA! Vui long kiem tra code Java.
    pause
    exit /b %ERRORLEVEL%
)

echo [OK] Bien dich thanh cong! Dang khoi chay Server...
java --module-path "lib\javafx-sdk-21.0.2\lib" --add-modules javafx.controls,javafx.fxml -cp "bin;lib\mongo\*" server.ServerMainApp
