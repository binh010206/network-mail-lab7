@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ========================================================
echo   KHOI DONG MAY CHU (LAN MAIL SERVER ^& MONGODB GATEWAY)
echo ========================================================

if not exist "lib\javafx-sdk-21.0.2" (
    echo [!] Chua tim thay JavaFX SDK. Dang tu dong tai ve...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_windows-x64_bin-sdk.zip', 'lib\javafx-sdk.zip'); Expand-Archive -Path 'lib\javafx-sdk.zip' -DestinationPath 'lib' -Force; Remove-Item 'lib\javafx-sdk.zip'"
    echo [OK] Da cai dat xong JavaFX SDK!
)

if not exist bin\server\ServerMainApp.class (
    echo [!] Dang bien dich ma nguon lan dau...
    call compile.bat
)

echo [*] Dang khoi chay giao dien Server JavaFX...
start "VKU Mail Server" javaw --module-path "lib\javafx-sdk-21.0.2\lib" --add-modules javafx.controls,javafx.fxml -cp "bin;lib\mongo\*" server.ServerMainApp
