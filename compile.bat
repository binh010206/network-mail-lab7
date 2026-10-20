@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ========================================================
echo   DANG BIEN DICH HE THONG MAIL SERVER ^& CLIENT (LAB 7)
echo ========================================================

if not exist "lib\javafx-sdk-21.0.2" (
    echo [!] Chua tim thay JavaFX SDK. Dang tu dong tai ve...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://download2.gluonhq.com/openjfx/21.0.2/openjfx-21.0.2_windows-x64_bin-sdk.zip', 'lib\javafx-sdk.zip'); Expand-Archive -Path 'lib\javafx-sdk.zip' -DestinationPath 'lib' -Force; Remove-Item 'lib\javafx-sdk.zip'"
    echo [OK] Da cai dat xong JavaFX SDK!
)

if not exist bin mkdir bin

javac -encoding UTF-8 -cp "lib\javafx-sdk-21.0.2\lib\*";"lib\mongo\*" -d bin src\model\*.java src\config\*.java src\database\*.java src\network\*.java src\server\*.java src\client\*.java

if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] Bien dich thanh cong 100%!
    if not exist bin\resources mkdir bin\resources
    copy /Y src\resources\style.css bin\resources\style.css >nul
) else (
    echo.
    echo [!] Co loi xay ra trong qua trinh bien dich.
)
pause
