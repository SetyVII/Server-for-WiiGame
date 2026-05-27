@echo off
setlocal EnableExtensions

call :FindJavaHome
if not defined JAVA_HOME (
    echo No se ha encontrado una instalacion valida de Java.
    echo Define JAVA_HOME o instala un JDK 21 compatible.
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
pushd "%~dp0java-server"
if errorlevel 1 (
    echo No se ha podido entrar en la carpeta java-server.
    pause
    exit /b 1
)

echo Compilando cambios...
javac -cp "target/classes;target/java-server-1.0.jar" -d target/classes src/main/java/server/Main.java src/main/java/model/*.java
if %errorlevel% neq 0 (
    echo Error en la compilacion
    popd
    pause
    exit /b 1
)

echo Copiando archivos estaticos...
xcopy "src\main\resources\*" "target\classes\" /Y /I /E >nul 2>&1
if %errorlevel% neq 0 (
    echo Advertencia: No se pudieron copiar todos los recursos
)

echo Iniciando servidor en puerto 3002...
java -cp "target/classes;target/java-server-1.0.jar" server.Main
popd
pause
exit /b 0

:FindJavaHome
rem 1. JAVA_HOME ya definido por el equipo
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" if exist "%JAVA_HOME%\bin\javac.exe" if exist "%JAVA_HOME%\lib\jvm.cfg" exit /b 0
)

rem 2. Rutas habituales por distribucion / instalacion
for %%P in (
    "%USERPROFILE%\.jdks"
    "%ProgramFiles%\Java"
    "%ProgramFiles(x86)%\Java"
    "%ProgramFiles%\Eclipse Adoptium"
    "%ProgramFiles%\Amazon Corretto"
    "%ProgramFiles%\Microsoft"
    "%ProgramFiles%\Zulu"
    "%ProgramFiles%\BellSoft"
) do (
    if exist "%%~P" (
        for /d %%J in ("%%~P\*") do (
            if exist "%%~fJ\bin\java.exe" if exist "%%~fJ\bin\javac.exe" if exist "%%~fJ\lib\jvm.cfg" (
                set "JAVA_HOME=%%~fJ"
                exit /b 0
            )
        )
    )
)

rem 3. Ultimo recurso: java disponible en PATH
for /f "delims=" %%J in ('where java 2^>nul') do (
    for %%K in ("%%~dpJ..") do (
        if exist "%%~fK\bin\java.exe" if exist "%%~fK\bin\javac.exe" if exist "%%~fK\lib\jvm.cfg" (
            set "JAVA_HOME=%%~fK"
            exit /b 0
        )
    )
)

exit /b 1