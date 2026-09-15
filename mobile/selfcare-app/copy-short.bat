@echo off
REM Short-path copy script for React Native project
REM Creates a copy of the RN project at C:\omrna and sets up symlinks for node_modules

REM Create target directory
if not exist C:\omrna mkdir C:\omrna

REM Copy android directory (skip node_modules, build, .cxx)
robocopy "D:\MDA\Architectures\Product Architecture\Selfcare\selfcaremicroservices\Selfcare Product\selfcare-platform\mobile\selfcare-app" "C:\omrna" /E /XF "node_modules" /XF "build" /XF ".cxx" /XF "*.log" /XJ /R:3 /W:5

REM Create symlink for node_modules at short path
if exist "C:\omrna\node_modules" rmdir "C:\omrna\node_modules"
mklink /J "C:\omrna\node_modules" "D:\MDA\Architectures\Product Architecture\Selfcare\selfcaremicroservices\Selfcare Product\selfcare-platform\mobile\selfcare-app\node_modules"

echo Done. Project copied to C:\omrna
echo Next: cd C:\omrna\android && npm install && ./gradlew assembleDebug
