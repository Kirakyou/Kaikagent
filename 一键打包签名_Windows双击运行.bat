@echo off
chcp 65001 >nul
echo ====================================================================
echo   VisualAgent 一键对齐与 V2/V3 强签名脚本 (Windows 零基础版)
echo   解决 Redmi K80 至尊版 (HyperOS 3) 安装报 packageInfo is null (33)
echo ====================================================================

set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
if not exist "%SDK_DIR%\build-tools" (
    echo [X] 未找到默认 Android SDK 目录: %SDK_DIR%\build-tools
    echo 请先安装 Android Studio 并完成首次打开自动下载 SDK。
    pause
    exit /b 1
)

for /f "delims=" %%i in ('dir /b /ad /on "%SDK_DIR%\build-tools"') do set "LATEST_BUILD_TOOLS=%%i"
set "ZIPALIGN=%SDK_DIR%\build-tools\%LATEST_BUILD_TOOLS%\zipalign.exe"
set "APKSIGNER=%SDK_DIR%\build-tools\%LATEST_BUILD_TOOLS%\apksigner.bat"

echo [*] 使用 Build-Tools: %LATEST_BUILD_TOOLS%

if not exist "visualagent-release.jks" (
    echo [0/4] 正在自动生成签名证书 visualagent-release.jks ...
    keytool -genkeypair -v -keystore visualagent-release.jks -alias visualagent_key -keyalg RSA -keysize 2048 -validity 10000 -storepass visualagent123456 -keypass visualagent123456 -dname "CN=VisualAgent, OU=MobileAI, O=OpenSource, L=Beijing, ST=Beijing, C=CN"
)

echo [1/4] 请确保已在 Android Studio 中点击 Build -^> Build APK(s) 或执行 gradlew assembleRelease
set "UNSIGNED_APK=app\build\outputs\apk\release\app-release-unsigned.apk"
if not exist "%UNSIGNED_APK%" (
    echo [!] 未找到 %UNSIGNED_APK%，正在尝试调用 gradlew.bat 编译...
    call gradlew.bat assembleRelease
)

echo [2/4] 正在执行 zipalign 4字节与16KB页对齐...
"%ZIPALIGN%" -p -f -v 4 "%UNSIGNED_APK%" "app-aligned.apk"

echo [3/4] 正在执行 V2 + V3 强签名...
call "%APKSIGNER%" sign --ks visualagent-release.jks --ks-key-alias visualagent_key --ks-pass pass:visualagent123456 --key-pass pass:visualagent123456 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "VisualAgent-HyperOS3-Signed.apk" "app-aligned.apk"

echo [4/4] 正在验证 V2/V3 签名...
call "%APKSIGNER%" verify --verbose "VisualAgent-HyperOS3-Signed.apk"

echo ====================================================================
echo [√] 打包成功！请将当前文件夹下的 VisualAgent-HyperOS3-Signed.apk 发送到手机安装！
echo ====================================================================
pause
