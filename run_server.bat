@echo off
set JAVA_HOME=C:\Users\madrid\.jdks\corretto-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
cd java-server
echo Compilando cambios...
javac -cp "target/classes;target/java-server-1.0.jar" -d target/classes src/main/java/server/Main.java src/main/java/model/*.java
if %errorlevel% neq 0 (
    echo Error en la compilacion
    pause
    exit /b
)
echo Iniciando servidor en puerto 3002...
java -cp "target/classes;target/java-server-1.0.jar" server.Main
pause