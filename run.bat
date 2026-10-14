@echo off
setlocal enabledelayedexpansion
title TCP vs UDP File Transfer Lab Project

echo ==============================================================================
echo        COMPUTER NETWORKS LAB: TCP VS. UDP FILE TRANSFER BENCHMARK
echo ==============================================================================
echo.

:: Check Java Installation
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java is not installed or not found in system PATH.
    echo Please install OpenJDK 17 or later and add it to your PATH.
    pause
    exit /b 1
)

:: Ensure bin directory exists
if not exist "bin" mkdir bin
if not exist "test-files" mkdir test-files
if not exist "received\tcp" mkdir received\tcp
if not exist "received\udp" mkdir received\udp
if not exist "results" mkdir results

:: Compile Java Source Code
echo [1/3] Compiling Java source files from src\ ...
javac -d bin src\*.java
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Compilation failed! Please check the error messages above.
    pause
    exit /b 1
)
echo [SUCCESS] Compilation succeeded! Classes placed in bin\ directory.
echo.

:: Interactive Menu
:MENU
echo ------------------------------------------------------------------------------
echo Select an action to execute:
echo   1. Run Full Automated Benchmark (Generates files, CSVs & Graphs)
echo   2. Start TCP Server (Default port 8888)
echo   3. Run TCP Client (Send a file over TCP)
echo   4. Start UDP Server (Default port 8889)
echo   5. Run UDP Client (Send a file over UDP)
echo   6. Generate Deterministic Test Files (100KB, 1MB, 5MB, 10MB)
echo   7. Re-generate Plot Images (Python matplotlib)
echo   8. Open Results Directory
echo   9. Exit
echo ------------------------------------------------------------------------------
set /p choice="Enter your choice [1-9]: "

if "%choice%"=="1" (
    echo.
    echo Starting automated benchmark...
    java -cp bin BenchmarkRunner
    echo.
    pause
    goto MENU
)

if "%choice%"=="2" (
    echo.
    echo Starting TCP Server on port 8888...
    java -cp bin TCPServer 8888 received\tcp
    echo.
    pause
    goto MENU
)

if "%choice%"=="3" (
    echo.
    set /p filepath="Enter file path to transmit (e.g. test-files\file_1MB.bin): "
    java -cp bin TCPClient !filepath! localhost 8888
    echo.
    pause
    goto MENU
)

if "%choice%"=="4" (
    echo.
    echo Starting UDP Server on port 8889...
    java -cp bin UDPServer 8889 received\udp
    echo.
    pause
    goto MENU
)

if "%choice%"=="5" (
    echo.
    set /p filepath="Enter file path to transmit (e.g. test-files\file_1MB.bin): "
    set /p delay="Enter inter-packet delay in microseconds (0 for full speed): "
    if "!delay!"=="" set delay=0
    java -cp bin UDPClient !filepath! localhost 8889 !delay!
    echo.
    pause
    goto MENU
)

if "%choice%"=="6" (
    echo.
    echo Generating deterministic test files...
    java -cp bin Main generate
    echo.
    pause
    goto MENU
)

if "%choice%"=="7" (
    echo.
    echo Generating plots...
    python scripts\plot_results.py
    echo.
    pause
    goto MENU
)

if "%choice%"=="8" (
    explorer results
    goto MENU
)

if "%choice%"=="9" (
    echo Exiting. Goodbye!
    exit /b 0
)

echo Invalid choice. Please enter a number between 1 and 9.
pause
goto MENU
