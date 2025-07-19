@echo off

REM Путь к JavaFX JMODS
set "JAVAFX_JMODS=C:\Program Files\Apache\javafx-jmods-21.0.8"

REM Путь к JDK JMODS
set "JDK_JMODS=%JAVA_HOME%\jmods"

REM Переходим в папку frontend
cd frontend

REM Очищаем предыдущую сборку
echo Cleaning previous build...
call mvn clean

REM Собираем проект
echo Building project...
call mvn package

REM Возвращаемся в корень проекта
cd ..

REM Создаем кастомный runtime с ВСЕМИ модулями JavaFX
set "MODULE_PATH=%JDK_JMODS%;%JAVAFX_JMODS%"
echo Creating custom runtime with ALL JavaFX modules...
jlink --module-path "%MODULE_PATH%" --add-modules java.base,java.desktop,java.logging,java.naming,java.sql,java.xml,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web --output frontend\target\custom-runtime

REM Создаем .msi файл с помощью jpackage
echo Creating .msi installer...
jpackage --input frontend\target --name Roomster --main-jar frontend-1.0-SNAPSHOT.jar --main-class com.example.chat.frontend.ChatClient --runtime-image frontend\target\custom-runtime --type msi --win-menu --win-shortcut --win-dir-chooser --dest frontend\target

echo Build completed!
echo Check frontend\target folder for the installer file.
pause 