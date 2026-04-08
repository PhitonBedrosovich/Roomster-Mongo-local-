@echo off

REM Версия JavaFX должна совпадать с версией в pom.xml (21.0.8)
REM Убедись что папка существует: C:\Program Files\Apache\javafx-jmods-21.0.8
set "JAVAFX_JMODS=C:\Program Files\Apache\javafx-jmods-21.0.8"

set "JDK_JMODS=%JAVA_HOME%\jmods"

cd frontend

echo Cleaning previous build...
call mvn clean

echo Building project...
call mvn package

cd ..

REM Создаем custom runtime через jlink
set "MODULE_PATH=%JDK_JMODS%;%JAVAFX_JMODS%"
echo Creating custom runtime...
jlink --module-path "%MODULE_PATH%" ^
  --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,java.security.jgss,java.security.sasl,jdk.crypto.ec,jdk.unsupported,java.net.http,javafx.base,javafx.controls,javafx.fxml,javafx.graphics ^
  --strip-debug ^
  --compress=2 ^
  --no-header-files ^
  --no-man-pages ^
  --output frontend\target\custom-runtime

REM Создаем installer через jpackage
REM --input указывает папку с jar и папкой libs/
echo Creating .msi installer...
jpackage ^
  --input frontend\target ^
  --name Roomster ^
  --main-jar frontend-1.0-SNAPSHOT.jar ^
  --main-class com.example.chat.frontend.ChatClient ^
  --runtime-image frontend\target\custom-runtime ^
  --type msi ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --dest frontend\target

echo Build completed!
echo Check frontend\target folder for the installer file.
pause